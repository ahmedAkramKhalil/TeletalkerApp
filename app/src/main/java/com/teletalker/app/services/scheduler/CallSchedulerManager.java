package com.teletalker.app.services.scheduler;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.teletalker.app.features.home.fragments.calender.data.models.ScheduledCall;

import java.util.concurrent.TimeUnit;

/**
 * Updated scheduler manager that uses ExactAlarmScheduler as primary method
 * and keeps WorkManager as a safety backup
 */
public class CallSchedulerManager {
    private static final String TAG = "CallSchedulerManager";

    // Unique tags for work requests
    private static final String PERIODIC_CHECK_TAG = "scheduled_calls_periodic_check";

    // Safety check every 15 minutes (WorkManager minimum)
    private static final long PERIODIC_CHECK_INTERVAL_MINUTES = 15;

    private final Context context;
    private final WorkManager workManager;
    private final ExactAlarmScheduler exactAlarmScheduler;

    public CallSchedulerManager(Context context) {
        this.context = context.getApplicationContext();
        this.workManager = WorkManager.getInstance(this.context);
        this.exactAlarmScheduler = new ExactAlarmScheduler(this.context);
    }

    /**
     * Initialize the periodic background worker as a SAFETY BACKUP
     * The primary scheduling uses ExactAlarmScheduler
     */
    public void initializePeriodicCheck() {
        Log.d(TAG, "Initializing periodic scheduled call checker (BACKUP ONLY)");

        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .setRequiresStorageNotLow(false)
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build();

        PeriodicWorkRequest periodicWorkRequest = new PeriodicWorkRequest.Builder(
                ScheduledCallWorker.class,
                PERIODIC_CHECK_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        )
                .setConstraints(constraints)
                .addTag(PERIODIC_CHECK_TAG)
                .build();

        // Use KEEP policy to avoid duplicate workers
        workManager.enqueueUniquePeriodicWork(
                PERIODIC_CHECK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest
        );

        Log.d(TAG, "Periodic backup check initialized - runs every " +
                PERIODIC_CHECK_INTERVAL_MINUTES + " minutes");
    }

    /**
     * Schedule a call using ExactAlarmScheduler (PRIMARY METHOD)
     * This provides precise timing using AlarmManager
     */
    public void scheduleCall(ScheduledCall call) {
        Log.d(TAG, "=========================================");
        Log.d(TAG, "SCHEDULING CALL ID: " + call.getId());
        Log.d(TAG, "Phone: " + call.getPhoneNumber());
        Log.d(TAG, "Contact: " + call.getContactName());
        Log.d(TAG, "Scheduled Time: " + new java.util.Date(call.getScheduledDateTime()));
        Log.d(TAG, "Current Time: " + new java.util.Date());

        long delay = call.getScheduledDateTime() - System.currentTimeMillis();
        long delayMinutes = delay / (60 * 1000);
        Log.d(TAG, "Delay: " + delayMinutes + " minutes");
        Log.d(TAG, "=========================================");

        try {
            // PRIMARY: Schedule using ExactAlarmScheduler for precise timing
            exactAlarmScheduler.scheduleExactAlarm(call);
            Log.d(TAG, "✓ Exact alarm scheduled successfully");
        } catch (Exception e) {
            Log.e(TAG, "✗ Failed to schedule exact alarm: " + e.getMessage(), e);
        }

        // Note: WorkManager periodic check acts as automatic backup
        // No need for explicit WorkManager one-time requests
    }

    /**
     * Cancel a scheduled call by removing its exact alarm
     */
    public void cancelScheduledCall(long callId) {
        Log.d(TAG, "Cancelling scheduled call ID: " + callId);

        try {
            exactAlarmScheduler.cancelAlarm(callId);
            Log.d(TAG, "✓ Exact alarm cancelled");
        } catch (Exception e) {
            Log.e(TAG, "✗ Failed to cancel exact alarm: " + e.getMessage(), e);
        }
    }

    /**
     * Stop the periodic background checker
     */
    public void stopPeriodicCheck() {
        workManager.cancelUniqueWork(PERIODIC_CHECK_TAG);
        Log.d(TAG, "Stopped periodic scheduled call checker");
    }

    /**
     * Reschedule a call (useful when user edits scheduled time)
     */
    public void rescheduleCall(ScheduledCall call) {
        Log.d(TAG, "Rescheduling call ID: " + call.getId());

        try {
            exactAlarmScheduler.rescheduleCall(call);
            Log.d(TAG, "✓ Call rescheduled successfully");
        } catch (Exception e) {
            Log.e(TAG, "✗ Failed to reschedule call: " + e.getMessage(), e);
        }
    }

    /**
     * Check if exact alarms can be scheduled (Android 12+)
     */
    public boolean canScheduleExactAlarms() {
        return exactAlarmScheduler.canScheduleExactAlarms();
    }

    /**
     * Get scheduling status for debugging
     */
    public String getSchedulingStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Scheduling System Status:\n");
        status.append("- Exact Alarms Available: ").append(canScheduleExactAlarms()).append("\n");
        status.append("- Periodic Backup: Every ").append(PERIODIC_CHECK_INTERVAL_MINUTES).append(" min\n");
        return status.toString();
    }
}