package com.teletalker.app.services.scheduler;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.teletalker.app.features.home.fragments.calender.data.data_sources.local.ScheduledCallDatabase;
import com.teletalker.app.features.home.fragments.calender.data.models.ScheduledCall;

import java.util.List;

/**
 * WorkManager Worker that checks for scheduled calls and executes them
 */
public class ScheduledCallWorker extends Worker {
    private static final String TAG = "ScheduledCallWorker";

    public ScheduledCallWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "ScheduledCallWorker started - checking for due calls");

        try {
            // Get database instance
            ScheduledCallDatabase database = ScheduledCallDatabase.getInstance(getApplicationContext());

            // Get current time with a 1-minute buffer (calls due in next minute)
            long currentTime = System.currentTimeMillis();
            long bufferTime = currentTime + (60 * 1000); // 1 minute ahead

            // Query for pending calls that are due
            List<ScheduledCall> dueCalls = database.scheduledCallDao()
                    .getPendingCallsDue(bufferTime);

            if (dueCalls == null || dueCalls.isEmpty()) {
                Log.d(TAG, "No calls due at this time");
                return Result.success();
            }

            Log.d(TAG, "Found " + dueCalls.size() + " call(s) due for execution");

            // Process each due call
            for (ScheduledCall call : dueCalls) {
                // Check if call is within execution window (not too early)
                if (call.getScheduledDateTime() <= bufferTime) {
                    executeScheduledCall(call);
                }
            }

            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Error executing scheduled calls: " + e.getMessage(), e);
            return Result.retry();
        }
    }

    private void executeScheduledCall(ScheduledCall call) {
        Log.d(TAG, "Executing scheduled call to: " + call.getPhoneNumber());

        try {
            // Create intent for ScheduledCallExecutor service
            Intent intent = new Intent(getApplicationContext(), ScheduledCallExecutor.class);
            intent.putExtra("call_id", call.getId());
            intent.putExtra("phone_number", call.getPhoneNumber());
            intent.putExtra("contact_name", call.getContactName());
            intent.putExtra("duration_minutes", call.getDurationMinutes());
            intent.putExtra("conversation_notes", call.getConversationNotes());

            // Start the service
            getApplicationContext().startService(intent);

            Log.d(TAG, "Started ScheduledCallExecutor for call ID: " + call.getId());

        } catch (Exception e) {
            Log.e(TAG, "Failed to execute call: " + e.getMessage(), e);

            // Mark call as failed
            ScheduledCallDatabase database = ScheduledCallDatabase.getInstance(getApplicationContext());
            database.scheduledCallDao().updateCallStatus(call.getId(), "failed");
        }
    }
}