package com.teletalker.app.services.scheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.teletalker.app.features.home.fragments.calender.data.data_sources.local.ScheduledCallDatabase;
import com.teletalker.app.features.home.fragments.calender.data.models.ScheduledCall;

import java.util.List;

/**
 * Receiver that restarts scheduled calls after device reboot
 * Updated to use ExactAlarmScheduler for precise timing
 */
public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = "BootCompletedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() == null) {
            return;
        }

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {

            Log.d(TAG, "========================================");
            Log.d(TAG, "DEVICE BOOTED - Rescheduling calls");
            Log.d(TAG, "Time: " + new java.util.Date());
            Log.d(TAG, "========================================");

            // Reschedule all pending calls
            rescheduleAllPendingCalls(context);
        }
    }

    private void rescheduleAllPendingCalls(Context context) {
        new Thread(() -> {
            try {
                // Get database instance
                ScheduledCallDatabase database = ScheduledCallDatabase.getInstance(context);

                // Get all pending calls
                long currentTime = System.currentTimeMillis();
                List<ScheduledCall> pendingCalls = database.scheduledCallDao()
                        .getPendingCallsDue(Long.MAX_VALUE); // Get all pending calls

                if (pendingCalls == null || pendingCalls.isEmpty()) {
                    Log.d(TAG, "No pending calls to reschedule");
                    return;
                }

                Log.d(TAG, "Found " + pendingCalls.size() + " total calls in database");

                // Initialize scheduler manager with ExactAlarmScheduler
                CallSchedulerManager schedulerManager = new CallSchedulerManager(context);

                // Initialize periodic backup check
                schedulerManager.initializePeriodicCheck();
                Log.d(TAG, "Periodic backup check initialized");

                int rescheduled = 0;
                int skipped = 0;

                for (ScheduledCall call : pendingCalls) {
                    // Only reschedule calls that are in the future
                    if (call.getScheduledDateTime() > currentTime &&
                            "pending".equals(call.getStatus())) {

                        try {
                            schedulerManager.scheduleCall(call);
                            rescheduled++;

                            Log.d(TAG, String.format("✓ Rescheduled call ID %d for %s (%s)",
                                    call.getId(),
                                    new java.util.Date(call.getScheduledDateTime()),
                                    call.getContactName()));

                        } catch (Exception e) {
                            Log.e(TAG, "Failed to reschedule call " + call.getId() + ": " + e.getMessage());
                        }
                    } else {
                        skipped++;
                        if (call.getScheduledDateTime() <= currentTime) {
                            Log.d(TAG, "✗ Skipped call ID " + call.getId() + " (in the past)");
                        } else {
                            Log.d(TAG, "✗ Skipped call ID " + call.getId() + " (status: " + call.getStatus() + ")");
                        }
                    }
                }

                Log.d(TAG, "========================================");
                Log.d(TAG, "RESCHEDULING COMPLETE");
                Log.d(TAG, "✓ Rescheduled: " + rescheduled + " call(s)");
                Log.d(TAG, "✗ Skipped: " + skipped + " call(s)");
                Log.d(TAG, "========================================");

            } catch (Exception e) {
                Log.e(TAG, "ERROR rescheduling calls after boot", e);
                Log.e(TAG, "Error message: " + e.getMessage());
                Log.e(TAG, "========================================");
            }
        }).start();
    }
}