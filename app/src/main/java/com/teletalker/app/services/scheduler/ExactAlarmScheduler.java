package com.teletalker.app.services.scheduler;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.teletalker.app.features.home.fragments.calender.data.models.ScheduledCall;

/**
 * Precise alarm scheduler using AlarmManager for accurate scheduled call execution
 * This replaces WorkManager for time-critical operations
 */
public class ExactAlarmScheduler {
    private static final String TAG = "ExactAlarmScheduler";

    private final Context context;
    private final AlarmManager alarmManager;

    public ExactAlarmScheduler(Context context) {
        this.context = context.getApplicationContext();
        this.alarmManager = (AlarmManager) this.context.getSystemService(Context.ALARM_SERVICE);
    }

    /**
     * Schedule an exact alarm for a scheduled call
     * Uses the most precise alarm type available based on Android version
     */
    public void scheduleExactAlarm(ScheduledCall call) {
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager not available!");
            return;
        }

        long scheduledTime = call.getScheduledDateTime();
        long currentTime = System.currentTimeMillis();

        if (scheduledTime <= currentTime) {
            Log.w(TAG, "Call is scheduled in the past, will execute immediately");
            // Execute immediately via broadcast
            executeCallImmediately(call);
            return;
        }

        // Create intent for alarm receiver
        Intent intent = new Intent(context, ScheduledCallAlarmReceiver.class);
        intent.setAction("com.teletalker.EXECUTE_SCHEDULED_CALL");
        intent.putExtra("call_id", call.getId());
        intent.putExtra("phone_number", call.getPhoneNumber());
        intent.putExtra("contact_name", call.getContactName());
        intent.putExtra("duration_minutes", call.getDurationMinutes());
        intent.putExtra("conversation_notes", call.getConversationNotes());

        // Use unique request code based on call ID to allow multiple alarms
        int requestCode = (int) call.getId();

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Schedule alarm based on Android version and permissions
        scheduleWithBestMethod(scheduledTime, pendingIntent, call);

        long delayMinutes = (scheduledTime - currentTime) / (60 * 1000);
        Log.d(TAG, String.format("Exact alarm scheduled for call ID %d in %d minutes at %s",
                call.getId(), delayMinutes, new java.util.Date(scheduledTime)));
    }

    /**
     * Choose the best alarm scheduling method based on Android version
     */
    private void scheduleWithBestMethod(long scheduledTime, PendingIntent pendingIntent, ScheduledCall call) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ (API 31+)
                if (alarmManager.canScheduleExactAlarms()) {
                    // Use setAlarmClock for highest priority (shows in status bar)
                    AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(
                            scheduledTime,
                            pendingIntent
                    );
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent);
                    Log.d(TAG, "Scheduled with setAlarmClock (Android 12+, highest priority)");
                } else {
                    Log.w(TAG, "Exact alarm permission not granted, using setExactAndAllowWhileIdle");
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            scheduledTime,
                            pendingIntent
                    );
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6.0+ (API 23+) - Use setExactAndAllowWhileIdle to work in Doze mode
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        scheduledTime,
                        pendingIntent
                );
                Log.d(TAG, "Scheduled with setExactAndAllowWhileIdle (Android 6+)");
            } else {
                // Android 4.4+ (API 19+) - Use setExact
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        scheduledTime,
                        pendingIntent
                );
                Log.d(TAG, "Scheduled with setExact (Android 4.4+)");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException scheduling exact alarm: " + e.getMessage());
            // Fallback to less precise alarm
            alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    scheduledTime,
                    pendingIntent
            );
            Log.w(TAG, "Fell back to inexact alarm - may not fire at exact time");
        }
    }

    /**
     * Cancel a scheduled alarm
     */
    public void cancelAlarm(long callId) {
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager not available!");
            return;
        }

        Intent intent = new Intent(context, ScheduledCallAlarmReceiver.class);
        intent.setAction("com.teletalker.EXECUTE_SCHEDULED_CALL");

        int requestCode = (int) callId;

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_NO_CREATE
        );

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            Log.d(TAG, "Cancelled alarm for call ID: " + callId);
        } else {
            Log.d(TAG, "No alarm found for call ID: " + callId);
        }
    }

    /**
     * Cancel all scheduled alarms (not recommended - better to cancel individually)
     */
    public void cancelAllAlarms() {
        Log.w(TAG, "cancelAllAlarms() called - this may not cancel all alarms properly");
        // Note: There's no direct way to cancel all alarms
        // Apps should track call IDs and cancel individually
    }

    /**
     * Check if we can schedule exact alarms (Android 12+)
     */
    public boolean canScheduleExactAlarms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return alarmManager != null && alarmManager.canScheduleExactAlarms();
        }
        return true; // Always allowed on older versions
    }

    /**
     * Execute call immediately (for calls in the past or immediate execution)
     */
    private void executeCallImmediately(ScheduledCall call) {
        Intent intent = new Intent(context, ScheduledCallAlarmReceiver.class);
        intent.setAction("com.teletalker.EXECUTE_SCHEDULED_CALL");
        intent.putExtra("call_id", call.getId());
        intent.putExtra("phone_number", call.getPhoneNumber());
        intent.putExtra("contact_name", call.getContactName());
        intent.putExtra("duration_minutes", call.getDurationMinutes());
        intent.putExtra("conversation_notes", call.getConversationNotes());

        context.sendBroadcast(intent);
        Log.d(TAG, "Executing call immediately via broadcast");
    }

    /**
     * Reschedule a call (cancel old alarm and create new one)
     */
    public void rescheduleCall(ScheduledCall call) {
        Log.d(TAG, "Rescheduling call ID: " + call.getId());
        cancelAlarm(call.getId());
        scheduleExactAlarm(call);
    }
}