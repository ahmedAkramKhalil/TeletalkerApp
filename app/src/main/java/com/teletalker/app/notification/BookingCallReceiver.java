package com.teletalker.app.notification;

import android.content.Context;
import android.util.Log;

import com.teletalker.app.billing.BillingManager;
import com.teletalker.app.features.home.fragments.calender.data.data_sources.local.ScheduledCallDatabase;
import com.teletalker.app.features.home.fragments.calender.data.models.ScheduledCall;
import com.teletalker.app.services.scheduler.CallSchedulerManager;
import com.teletalker.app.utils.PreferencesManager;

import java.util.Map;

/**
 * Receives FCM data messages of type "schedule_call" originating from the
 * booking web app (via the createBooking Cloud Function), persists a
 * ScheduledCall row, and arms the existing alarm-based scheduler.
 *
 * The downstream pipeline (ExactAlarmScheduler -> ScheduledCallAlarmReceiver
 * -> ScheduledCallExecutor -> AICallRecorderRefactored) is unchanged.
 *
 * Expected FCM data fields:
 *   type             = "schedule_call"
 *   booking_id       Firestore /bookings/{id} doc id
 *   target_number    Customer phone in E.164
 *   contact_name     Customer name
 *   purpose          Short call purpose, used by AI
 *   notes            (optional) extra script/context
 *   scheduled_for    Unix epoch millis (string) when the call should fire
 *   duration_minutes (optional) string, defaults to "2"
 */
public final class BookingCallReceiver {

    private static final String TAG = "BookingCallReceiver";
    public static final String SOURCE_BOOKING_APP = "booking_app";

    private BookingCallReceiver() {
    }

    /**
     * Handle a schedule_call FCM payload. Runs DB I/O on a worker thread —
     * safe to invoke from FirebaseMessagingService.onMessageReceived().
     */
    public static void handle(Context context, Map<String, String> data) {
        if (data == null) {
            Log.w(TAG, "Empty FCM data payload — ignoring");
            return;
        }

        final String bookingId = data.get("booking_id");
        final String phoneNumber = data.get("target_number");
        final String contactName = data.get("contact_name");
        final String purpose = data.get("purpose");
        final String notes = optString(data, "notes", "");
        final long scheduledFor = parseLong(data.get("scheduled_for"),
                System.currentTimeMillis() + 30_000L); // default: ~30s out
        final int durationMinutes = parseInt(data.get("duration_minutes"), 2);

        // Per-merchant agent override (empty = device default).
        final String agentIdOverride = optString(data, "agent_id", "");

        // JSON-encoded list of free reschedule slots. AI uses these mid-call.
        final String availableSlotsJson = optString(data, "available_slots", "");
        // JSON-encoded merchant customisation. Decoded by AICallRecorder.
        final String aiCustomizationJson = optString(data, "ai_customization", "");

        // Structured booking details — used to build a booking-aware AI prompt.
        final String businessName    = optString(data, "business_name",    "");
        final String serviceName     = optString(data, "service_name",     "");
        final String serviceDuration = optString(data, "service_duration", "");
        final String appointmentLocal = optString(data, "appointment_local", "");
        final String appointmentDay   = optString(data, "appointment_day",   "");
        final String appointmentTime  = optString(data, "appointment_time",  "");
        final String appointmentIso   = optString(data, "appointment_iso",   "");
        final String merchantTimezone = optString(data, "merchant_timezone", "");

        if (bookingId == null || phoneNumber == null || phoneNumber.isEmpty()) {
            Log.e(TAG, "Missing required fields (booking_id, target_number) — dropping");
            return;
        }

        // Stash booking details for AICallRecorderRefactored to read when it
        // builds the conversation_config_override. These are cleared inside
        // checkAndApplyScheduledCallContext after the AI consumes them.
        PreferencesManager prefs = PreferencesManager.getInstance(context);
        prefs.putString("pending_scheduled_call_contact",     contactName);
        prefs.putString("pending_scheduled_call_business",    businessName);
        prefs.putString("pending_scheduled_call_service",     serviceName);
        prefs.putString("pending_scheduled_call_service_min", serviceDuration);
        prefs.putString("pending_scheduled_call_appt_local",  appointmentLocal);
        prefs.putString("pending_scheduled_call_appt_day",    appointmentDay);
        prefs.putString("pending_scheduled_call_appt_time",   appointmentTime);
        prefs.putString("pending_scheduled_call_appt_iso",    appointmentIso);
        prefs.putString("pending_scheduled_call_tz",          merchantTimezone);
        prefs.putString("pending_scheduled_call_agent_id",    agentIdOverride);
        prefs.putString("pending_scheduled_call_slots",       availableSlotsJson);
        // Firestore booking id — surfaced in the system prompt so the AI
        // includes it as a parameter when invoking a server-side webhook tool.
        prefs.putString("pending_scheduled_call_booking_id",  bookingId == null ? "" : bookingId);
        prefs.putString("pending_scheduled_call_ai_custom",   aiCustomizationJson);

        Log.d(TAG, "========================================");
        Log.d(TAG, "Booking-app scheduled call received");
        Log.d(TAG, "Booking ID: " + bookingId);
        Log.d(TAG, "Phone: " + phoneNumber);
        Log.d(TAG, "Contact: " + contactName);
        Log.d(TAG, "Purpose: " + purpose);
        Log.d(TAG, "Fires at: " + new java.util.Date(scheduledFor));
        Log.d(TAG, "========================================");

        final Context appCtx = context.getApplicationContext();

        // Balance gate: if the merchant doesn't have enough minutes to
        // place the AI call, fire an urgent alert with a top-up CTA. We
        // STILL schedule the call — the user has ~30s to top up before
        // the alarm fires, and the AI side has its own low-balance
        // behaviour as a final fallback.
        try {
            BillingManager billing = BillingManager.getInstance(appCtx);
            double remaining = billing.getCachedRemainingMinutes();
            // Need at least one minute (the typical confirmation call duration).
            if (remaining < 1.0) {
                BookingAlerts.notifyLowBalance(appCtx, contactName, remaining);
            }
        } catch (Exception e) {
            Log.w(TAG, "Balance check failed (continuing): " + e.getMessage());
        }

        new Thread(() -> {
            try {
                ScheduledCall call = new ScheduledCall();
                call.setPhoneNumber(phoneNumber);
                call.setContactName(contactName);
                call.setScheduledDateTime(scheduledFor);
                call.setDurationMinutes(durationMinutes);
                call.setPurpose(purpose);
                call.setConversationNotes(notes);
                call.setSource(SOURCE_BOOKING_APP);
                call.setExternalBookingId(bookingId);

                // Insert first so Room generates the auto ID — the alarm
                // scheduler keys its PendingIntent off that ID.
                ScheduledCallDatabase db = ScheduledCallDatabase.getInstance(appCtx);
                long id = db.scheduledCallDao().insert(call);
                call.setId(id);

                Log.d(TAG, "Persisted booking call with local id=" + id);

                new CallSchedulerManager(appCtx).scheduleCall(call);
            } catch (Exception e) {
                Log.e(TAG, "Failed to schedule booking call: " + e.getMessage(), e);
            }
        }).start();
    }

    private static String optString(Map<String, String> data, String key, String fallback) {
        String v = data.get(key);
        return v == null ? fallback : v;
    }

    private static long parseLong(String s, long fallback) {
        if (s == null || s.isEmpty()) return fallback;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseInt(String s, int fallback) {
        if (s == null || s.isEmpty()) return fallback;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
