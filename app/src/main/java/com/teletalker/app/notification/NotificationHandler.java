package com.teletalker.app.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.teletalker.app.R;
import com.teletalker.app.billing.BillingManager;
import com.teletalker.app.features.home.HomeActivity;
import com.teletalker.app.features.home.fragments.calender.data.data_sources.local.ScheduledCallDatabase;
import com.teletalker.app.features.home.fragments.calender.data.models.ScheduledCall;
import com.teletalker.app.features.home.fragments.settings.InvoicesActivity;
import com.teletalker.app.services.scheduler.CallSchedulerManager;
import com.teletalker.app.utils.PreferencesManager;
import com.teletalker.app.utils.SubscriptionManager;

import java.util.Map;
import java.util.concurrent.Executors;

public class NotificationHandler extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "teletalker_payments";
    private static final String TAG = "NotificationHandler";

    @Override
    public void onMessageReceived(RemoteMessage message) {
        Map<String, String> data = message.getData();
        String type = data.get("type");
        String action = data.get("action");

        // Dashboard booking → schedule the outbound AI confirmation call.
        // createBooking (balancer/booking.js) pushes a data-only FCM with
        // type=schedule_call. Route it through the same path the in-app
        // Calendar UI uses so the ExactAlarm fires and the call runs.
        if ("schedule_call".equals(type)) {
            handleScheduleCall(data);
            return;
        }

        // Sync balance on payment notifications
        if ("payment_success".equals(type) || "renewal_success".equals(type)) {
            BillingManager.getInstance(this).syncBalance();
        }
        if ("payment_success".equals(type) ||
                "renewal_success".equals(type) ||
                "subscription_success".equals(type)) {

            SubscriptionManager.getInstance(this).sync();
        }

        // Show notification
        String title = message.getNotification() != null ?
                message.getNotification().getTitle() : "TeleTalker";
        String body = message.getNotification() != null ?
                message.getNotification().getBody() : "";

        showNotification(title, body, action, data.get("invoiceUrl"));
    }

    /**
     * Build a ScheduledCall from a dashboard-booking FCM payload, persist it,
     * and hand it to CallSchedulerManager (ExactAlarm). Mirrors the flow in
     * ScheduleCallBottomSheet so the auto-call actually fires.
     *
     * Note: the per-booking agent_id / ai_customization sent by the backend
     * are not yet stored (the ScheduledCall schema has no columns for them);
     * the call runs with the device's default agent. Enhancing that needs a
     * Room migration + executor wiring — tracked separately.
     */
    private void handleScheduleCall(Map<String, String> data) {
        final Context appContext = getApplicationContext();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String phone = data.get("target_number");
                if (phone == null || phone.trim().isEmpty()) {
                    Log.e(TAG, "schedule_call: missing target_number, ignoring");
                    return;
                }

                long scheduledFor = parseLong(data.get("scheduled_for"),
                        System.currentTimeMillis() + 30_000L);
                int durationMinutes = (int) parseLong(data.get("duration_minutes"), 2);
                String contactName = data.get("contact_name");
                String purpose = data.get("purpose");
                String notes = data.get("notes");

                ScheduledCall call = new ScheduledCall();
                call.setPhoneNumber(phone.trim());
                call.setContactName(contactName == null || contactName.trim().isEmpty()
                        ? "Booking" : contactName.trim());
                call.setPurpose(purpose == null ? "" : purpose);
                call.setConversationNotes(notes == null ? "" : notes);
                call.setScheduledDateTime(scheduledFor);
                call.setDurationMinutes(durationMinutes > 0 ? durationMinutes : 2);
                call.setStatus("pending");

                ScheduledCallDatabase db = ScheduledCallDatabase.getInstance(appContext);
                long callId = db.scheduledCallDao().insert(call);
                call.setId(callId);

                // Carry the real Firestore booking_id so the AI can pass it to
                // the confirm_booking/cancel_booking webhook tools. Keyed by
                // callId so it never leaks into unrelated (manual) calls. Without
                // this the agent hallucinates a booking_id → webhook 404 → the
                // dashboard never receives the call outcome.
                String bookingId = data.get("booking_id");
                if (bookingId != null && !bookingId.trim().isEmpty()) {
                    PreferencesManager.getInstance(appContext)
                            .putString("pending_booking_id_" + callId, bookingId.trim());
                    Log.d(TAG, "schedule_call: stored booking_id=" + bookingId + " for callId=" + callId);
                }

                new CallSchedulerManager(appContext).scheduleCall(call);
                Log.d(TAG, "schedule_call: scheduled booking call id=" + callId
                        + " phone=" + phone + " at=" + new java.util.Date(scheduledFor));
            } catch (Exception e) {
                Log.e(TAG, "schedule_call: failed to schedule booking call", e);
            }
        });
    }

    private static long parseLong(String s, long fallback) {
        try {
            return s == null ? fallback : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void showNotification(String title, String body, String action, String invoiceUrl) {
        createChannel();

        Intent intent;

        // Route based on action
        if ("open_payment".equals(action) || "view_invoices".equals(action)) {
            intent = new Intent(this, InvoicesActivity.class);
        } else {
            intent = new Intent(this, HomeActivity.class);
        }

        if (invoiceUrl != null) {
            intent.putExtra("invoice_url", invoiceUrl);
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_bing)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Payments",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onNewToken(String token) {
        // Push the rotated FCM token to the backend so createBooking can reach
        // this device. Without this, a token rotation (app reinstall / device
        // reset) leaves a stale token on file → FCM "NotRegistered" → bookings
        // fail with 503. Best-effort; only meaningful when a user is signed in.
        try {
            if (token == null || token.isEmpty()) return;
            if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null) return;
            com.teletalker.app.network.FirebaseFunctionsManager.getInstance().updateFCMToken(
                    token,
                    new com.teletalker.app.network.FirebaseFunctionsManager.OnTokenUpdateCallback() {
                        @Override public void onSuccess() { Log.d(TAG, "FCM token pushed (onNewToken)"); }
                        @Override public void onError(String error) { Log.w(TAG, "FCM token push failed: " + error); }
                    });
        } catch (Exception e) {
            Log.w(TAG, "onNewToken push failed", e);
        }
    }
}