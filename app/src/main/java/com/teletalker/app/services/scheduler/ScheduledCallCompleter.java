package com.teletalker.app.services.scheduler;

import android.content.Context;
import android.util.Log;

import com.teletalker.app.features.booking.data.BookingCallTranscript;
import com.teletalker.app.features.home.fragments.calender.data.data_sources.local.ScheduledCallDao;
import com.teletalker.app.features.home.fragments.calender.data.data_sources.local.ScheduledCallDatabase;
import com.teletalker.app.features.home.fragments.calender.data.models.ScheduledCall;
import com.teletalker.app.network.FirebaseFunctionsManager;

import java.util.List;

/**
 * Finalizes a scheduled call's row in Room when the underlying Telecom Call
 * actually disconnects. The previous code relied on a fixed
 * <code>durationMinutes</code> timer inside {@link ScheduledCallExecutor},
 * but the service usually dies before the timer fires — so rows got stuck
 * on <code>in_progress</code> and booking outcomes were never reported.
 *
 * Call this from {@code CallDetector} when it sees
 * {@code Call.STATE_DISCONNECTED}.
 */
public final class ScheduledCallCompleter {

    private static final String TAG = "ScheduledCallCompleter";
    /** Look back up to this far for a matching scheduled call. */
    private static final long LOOKBACK_MS = 2L * 60 * 60 * 1000; // 2h
    /** Look forward a little too — the alarm may have been set very recently. */
    private static final long LOOKAHEAD_MS = 10L * 60 * 1000;    // 10m

    private ScheduledCallCompleter() {}

    /**
     * If there is an in-progress (or recently pending) scheduled call that
     * matches this phone number, mark it completed in Room and post the
     * outcome to Firestore for booking-app calls. Safe to call from any
     * thread — DB work is dispatched to a worker.
     */
    public static void completeIfScheduled(Context context, String phoneNumber) {
        if (context == null) return;
        final String number = phoneNumber == null ? "" : phoneNumber;
        final Context appCtx = context.getApplicationContext();

        new Thread(() -> {
            try {
                ScheduledCallDao dao = ScheduledCallDatabase.getInstance(appCtx).scheduledCallDao();

                ScheduledCall target = findMatchingCall(dao, number);
                if (target == null) {
                    Log.v(TAG, "No scheduled call to finalize for " + number);
                    return;
                }

                String previousStatus = target.getStatus();
                if ("completed".equals(previousStatus) || "failed".equals(previousStatus)) {
                    Log.d(TAG, "Scheduled call " + target.getId() +
                            " already finalised (" + previousStatus + ") — skipping");
                    return;
                }

                long now = System.currentTimeMillis();
                target.setActualCallEndTime(now);
                long start = target.getActualCallStartTime();
                if (start > 0) {
                    target.setActualCallDurationSeconds((int) ((now - start) / 1000));
                }
                target.setStatus("completed");
                dao.update(target);

                Log.d(TAG, "✅ Scheduled call " + target.getId() +
                        " finalised: " + previousStatus + " → completed");

                // Booking-app calls: classify what happened from the
                // transcript and push the outcome back to Firestore so the
                // merchant dashboard reflects what really happened.
                if (target.isFromBookingApp()) {
                    final String bookingId = target.getExternalBookingId();
                    if (bookingId != null && !bookingId.isEmpty()) {
                        // No-answer = call connected briefly without the customer
                        // ever speaking. Use both criteria so we don't false-positive
                        // when the customer answers and immediately hangs up.
                        BookingCallTranscript transcript = BookingCallTranscript.get();
                        int durationSec = target.getActualCallDurationSeconds();
                        BookingCallTranscript.Outcome outcome;
                        if (!transcript.userSpoke() && durationSec <= 8) {
                            outcome = BookingCallTranscript.Outcome.NO_ANSWER;
                        } else {
                            outcome = transcript.classify();
                        }
                        final String outcomeStr = BookingCallTranscript.outcomeToString(outcome);

                        Log.d(TAG, "Booking " + bookingId + " → " + outcomeStr +
                                " (transcript " + transcript.shortSummary() +
                                ", duration " + durationSec + "s)");

                        // If the AI agreed a specific new slot, ship it so
                        // the booking gets atomically rescheduled to that time.
                        String newApptIso = (outcome == BookingCallTranscript.Outcome.RESCHEDULED)
                                ? transcript.getRescheduleIso()
                                : null;
                        String cancelReason = (outcome == BookingCallTranscript.Outcome.DECLINED)
                                ? transcript.getCancellationReason()
                                : null;

                        FirebaseFunctionsManager.getInstance().reportBookingCallOutcome(
                                bookingId,
                                outcomeStr,
                                newApptIso,
                                cancelReason,
                                new FirebaseFunctionsManager.OnBookingOutcomeCallback() {
                                    @Override
                                    public void onSuccess() {
                                        Log.d(TAG, "Booking outcome reported: " + outcomeStr);
                                    }

                                    @Override
                                    public void onError(String error) {
                                        Log.w(TAG, "Booking outcome report failed: " + error);
                                    }
                                });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "completeIfScheduled error: " + e.getMessage(), e);
            }
        }).start();
    }

    /**
     * Find the best matching scheduled-call row for the given phone number.
     * We prefer the most recent in_progress row, falling back to a pending
     * row whose alarm has fired or is about to fire.
     */
    private static ScheduledCall findMatchingCall(ScheduledCallDao dao, String number) {
        long now = System.currentTimeMillis();
        long windowStart = now - LOOKBACK_MS;
        long windowEnd = now + LOOKAHEAD_MS;

        // First try: anything active or pending for this phone in the window.
        List<ScheduledCall> active = dao.getActiveCallsByPhoneNumber(number, windowStart, windowEnd);
        ScheduledCall match = pickFirstInProgress(active);
        if (match != null) return match;

        // Second try: tolerate phone-format differences by scanning all
        // recent in_progress rows. Saves us when Telecom delivers a slightly
        // different number format than what we stored.
        ScheduledCall mostRecent = dao.getMostRecentInProgressCall();
        if (mostRecent != null && phoneNumbersMatch(mostRecent.getPhoneNumber(), number)) {
            return mostRecent;
        }
        return null;
    }

    private static ScheduledCall pickFirstInProgress(List<ScheduledCall> calls) {
        if (calls == null || calls.isEmpty()) return null;
        for (ScheduledCall c : calls) {
            if ("in_progress".equals(c.getStatus())) return c;
        }
        // None in_progress — return the most recent (probably pending);
        // status will move forward correctly when we update.
        return calls.get(0);
    }

    /** Tolerant phone-number compare (digits-only, trailing-7-digit fallback). */
    private static boolean phoneNumbersMatch(String a, String b) {
        if (a == null || b == null) return false;
        String na = a.replaceAll("[^0-9]", "");
        String nb = b.replaceAll("[^0-9]", "");
        if (na.isEmpty() || nb.isEmpty()) return false;
        if (na.equals(nb)) return true;
        int n = Math.min(na.length(), nb.length());
        if (n < 7) return false;
        return na.substring(na.length() - n).equals(nb.substring(nb.length() - n));
    }
}
