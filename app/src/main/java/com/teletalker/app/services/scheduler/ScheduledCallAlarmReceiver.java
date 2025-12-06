package com.teletalker.app.services.scheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.teletalker.app.features.home.fragments.calender.data.data_sources.local.ScheduledCallDatabase;

/**
 * BroadcastReceiver that handles exact alarm triggers for scheduled calls
 */
public class ScheduledCallAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "CallAlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            Log.w(TAG, "Received null intent or action");
            return;
        }

        String action = intent.getAction();
        Log.d(TAG, "========================================");
        Log.d(TAG, "ALARM RECEIVED - Action: " + action);

        if ("com.teletalker.EXECUTE_SCHEDULED_CALL".equals(action)) {
            handleScheduledCallAlarm(context, intent);
        }
    }

    private void handleScheduledCallAlarm(Context context, Intent intent) {
        // Extract call details
        long callId = intent.getLongExtra("call_id", -1);
        String phoneNumber = intent.getStringExtra("phone_number");
        String contactName = intent.getStringExtra("contact_name");
        int durationMinutes = intent.getIntExtra("duration_minutes", 10);
        String conversationNotes = intent.getStringExtra("conversation_notes");

        Log.d(TAG, "Scheduled Call Alarm Triggered!");
        Log.d(TAG, "Call ID: " + callId);
        Log.d(TAG, "Phone: " + phoneNumber);
        Log.d(TAG, "Contact: " + contactName);
        Log.d(TAG, "Time: " + new java.util.Date());
        Log.d(TAG, "========================================");

        if (callId == -1 || phoneNumber == null) {
            Log.e(TAG, "Invalid call data received");
            return;
        }

        // Verify call is still pending in database
        verifyAndExecuteCall(context, callId, phoneNumber, contactName,
                durationMinutes, conversationNotes);
    }

    private void verifyAndExecuteCall(Context context, long callId, String phoneNumber,
                                      String contactName, int durationMinutes,
                                      String conversationNotes) {
        // Check database to ensure call wasn't cancelled
        new Thread(() -> {
            try {
                ScheduledCallDatabase database = ScheduledCallDatabase.getInstance(context);

                // Verify call still exists and is pending
                // Note: You'll need to add this method to your DAO
                String status = database.scheduledCallDao().getCallStatus(callId);

                if (status == null) {
                    Log.w(TAG, "Call ID " + callId + " not found in database");
                    return;
                }

                if (!"pending".equals(status)) {
                    Log.w(TAG, "Call ID " + callId + " is not pending (status: " + status + ")");
                    return;
                }

                // Call is valid, execute it
                executeCall(context, callId, phoneNumber, contactName,
                        durationMinutes, conversationNotes);

            } catch (Exception e) {
                Log.e(TAG, "Error verifying call: " + e.getMessage(), e);
            }
        }).start();
    }

    private void executeCall(Context context, long callId, String phoneNumber,
                             String contactName, int durationMinutes, String conversationNotes) {
        try {
            // Create intent for ScheduledCallExecutor service
            Intent serviceIntent = new Intent(context, ScheduledCallExecutor.class);
            serviceIntent.putExtra("call_id", callId);
            serviceIntent.putExtra("phone_number", phoneNumber);
            serviceIntent.putExtra("contact_name", contactName);
            serviceIntent.putExtra("duration_minutes", durationMinutes);
            serviceIntent.putExtra("conversation_notes", conversationNotes);

            // Start foreground service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            Log.d(TAG, "Started ScheduledCallExecutor service for call ID: " + callId);

        } catch (Exception e) {
            Log.e(TAG, "Failed to start call executor service: " + e.getMessage(), e);
            markCallAsFailed(context, callId, "Failed to start service: " + e.getMessage());
        }
    }

    private void markCallAsFailed(Context context, long callId, String reason) {
        new Thread(() -> {
            try {
                ScheduledCallDatabase database = ScheduledCallDatabase.getInstance(context);
                database.scheduledCallDao().updateCallStatus(callId, "failed");
                Log.e(TAG, "Marked call " + callId + " as failed: " + reason);
            } catch (Exception e) {
                Log.e(TAG, "Failed to update call status: " + e.getMessage());
            }
        }).start();
    }
}