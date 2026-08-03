package com.teletalker.app.services.scheduler;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telecom.TelecomManager;
import android.util.Log;

import androidx.annotation.Nullable;
import android.content.pm.ServiceInfo;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.teletalker.app.R;
import com.teletalker.app.features.home.HomeActivity;
import com.teletalker.app.features.home.fragments.calender.data.data_sources.local.ScheduledCallDatabase;
import com.teletalker.app.utils.AuthStateManager;
import com.teletalker.app.utils.PreferencesManager;

/**
 * Service that executes scheduled outbound calls with AI integration
 */
public class ScheduledCallExecutor extends Service {
    private static final String TAG = "ScheduledCallExecutor";
    private static final String CHANNEL_ID = "scheduled_calls_channel";
    private static final int NOTIFICATION_ID = 9999;

    private long callId;
    private String phoneNumber;
    private String contactName;
    private int durationMinutes;
    private String conversationNotes;
    private String purpose;



    private Handler handler;
    private PreferencesManager preferences;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        preferences = PreferencesManager.getInstance(this);



        createNotificationChannel();
        Log.d(TAG, "ScheduledCallExecutor service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Extract call details from intent
        callId = intent.getLongExtra("call_id", -1);
        phoneNumber = intent.getStringExtra("phone_number");
        contactName = intent.getStringExtra("contact_name");
        durationMinutes = intent.getIntExtra("duration_minutes", 10);
        conversationNotes = intent.getStringExtra("conversation_notes");
        purpose = intent.getStringExtra("conversation_purpose");

        Log.d(TAG, "========================================");
        Log.d(TAG, "EXECUTING SCHEDULED CALL");
        Log.d(TAG, "Call ID: " + callId);
        Log.d(TAG, "Phone: " + phoneNumber);
        Log.d(TAG, "Contact: " + contactName);
        Log.d(TAG, "Duration: " + durationMinutes + " min");
        Log.d(TAG, "AI Notes: " + conversationNotes);
        Log.d(TAG, "AI conversation_purpose: " + purpose);
        Log.d(TAG, "========================================");

        // Start foreground. This service only PLACES the call (recording is handled by
        // CallDetector once the call goes active) and it is launched from a background
        // alarm, where a MICROPHONE-typed FGS is disallowed on API 34+. Use a non-mic
        // type and never let a typed-FGS restriction crash the scheduled call.
        startForegroundCompat();

        // Prepare AI before making call
        prepareAIForCall();

        // Execute the call
        handler.postDelayed(this::executeCall, 2000); // 2 second delay to ensure AI is ready

        return START_NOT_STICKY;
    }

    /**
     * Foreground promotion that survives Android 14's typed-FGS rules. A background
     * alarm cannot start a microphone-typed FGS, so we use SPECIAL_USE (API 34+) /
     * PHONE_CALL (29–33), and fall back to an untyped start rather than crash.
     */
    private void startForegroundCompat() {
        int type;
        if (Build.VERSION.SDK_INT >= 34) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
        } else if (Build.VERSION.SDK_INT >= 29) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL;
        } else {
            type = 0;
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, createNotification(), type);
        } catch (Exception e) {
            Log.e(TAG, "startForeground(type=" + type + ") failed; retrying untyped: " + e.getMessage());
            try {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, createNotification(), 0);
            } catch (Exception e2) {
                Log.e(TAG, "startForeground untyped also failed", e2);
            }
        }
    }

    private void prepareAIForCall() {
        Log.d(TAG, "Preparing AI with conversation notes...");

        // Get ElevenLabs credentials
        String apiKey = preferences.getApiKey();
        String agentId = preferences.getSelectedAgentId();

        if (apiKey == null || agentId == null) {
            Log.e(TAG, "ElevenLabs credentials not configured!");
            markCallAsFailed("AI credentials not configured");
            stopSelf();
            return;
        }

        // TODO: Here we'll integrate with ElevenLabs API to configure the agent
        // For now, we'll use the existing AI system which will pick up the notes
        // when the call connects through CallDetector service

        Log.d(TAG, "AI prepared with notes: " + conversationNotes);

        // Store conversation notes in preferences so CallDetector can access them

        preferences.putString("pending_scheduled_call_purpose", purpose);
        preferences.putBoolean("pending_scheduled_call_is_outbound", true);

        preferences.putString("pending_scheduled_call_notes", conversationNotes);
        preferences.putString("pending_scheduled_call_phone", phoneNumber);
        preferences.putLong("pending_scheduled_call_id", callId);
    }






    private void executeCall() {
        Log.d(TAG, "Executing outbound call to: " + phoneNumber);

        // Suspend/ban guard (defence-in-depth; the backend also blocks at
        // createBooking). Status is cached by DeviceReporter on login. Fail-open:
        // an unknown/active status never blocks a legitimate call.
        String acctStatus = PreferencesManager.getInstance(getApplicationContext())
                .getString(com.teletalker.app.utils.DeviceReporter.KEY_ACCOUNT_STATUS, "active");
        if ("suspended".equals(acctStatus) || "banned".equals(acctStatus)) {
            Log.e(TAG, "Account " + acctStatus + " — aborting scheduled call to " + phoneNumber);
            markCallAsFailed("Account " + acctStatus);
            stopSelf();
            return;
        }

        // Check permissions
        if (!hasCallPermissions()) {
            Log.e(TAG, "Missing CALL_PHONE permission");
            markCallAsFailed("Missing call permission");
            stopSelf();
            return;
        }

        try {
            // Method 1: Use Telecom API (preferred for Android 9+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                placeCallWithTelecom();
            } else {
                // Method 2: Fallback to Intent (older Android versions)
                placeCallWithIntent();
            }

            // Mark call as in progress
            updateCallStatus("in_progress");

            // Schedule call duration monitoring
            scheduleCallDurationMonitor();

        } catch (Exception e) {
            Log.e(TAG, "Failed to place call: " + e.getMessage(), e);
            markCallAsFailed("Failed to place call: " + e.getMessage());
            stopSelf();
        }
    }

    private void placeCallWithTelecom() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;

        TelecomManager telecomManager = (TelecomManager) getSystemService(TELECOM_SERVICE);
        if (telecomManager == null) {
            Log.e(TAG, "TelecomManager not available");
            placeCallWithIntent();
            return;
        }

        Uri uri = Uri.fromParts("tel", phoneNumber, null);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED) {

            telecomManager.placeCall(uri, null);
            Log.d(TAG, "Call placed using TelecomManager");

            // Update notification
            updateNotification("Calling " + contactName + "...");

        } else {
            Log.e(TAG, "CALL_PHONE permission denied");
            placeCallWithIntent();
        }
    }

    private void placeCallWithIntent() {
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:" + phoneNumber));
        callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED) {

            startActivity(callIntent);
            Log.d(TAG, "Call placed using Intent");

            // Update notification
            updateNotification("Calling " + contactName + "...");

        } else {
            Log.e(TAG, "CALL_PHONE permission denied");
            markCallAsFailed("Missing call permission");
            stopSelf();
        }
    }

    private void scheduleCallDurationMonitor() {
        // Monitor call and end after specified duration
        long durationMillis = durationMinutes * 60 * 1000L;

        Log.d(TAG, "Call duration monitor scheduled for " + durationMinutes + " minutes");

        handler.postDelayed(() -> {
            Log.d(TAG, "Call duration reached, marking as completed");
            markCallAsCompleted();
            stopSelf();
        }, durationMillis);
    }

    private void updateCallStatus(String status) {
        new Thread(() -> {
            try {
                ScheduledCallDatabase database = ScheduledCallDatabase.getInstance(getApplicationContext());
                database.scheduledCallDao().updateCallStatus(callId, status);
                Log.d(TAG, "Call status updated to: " + status);
            } catch (Exception e) {
                Log.e(TAG, "Failed to update call status: " + e.getMessage());
            }
        }).start();
    }

    private void markCallAsCompleted() {
        updateCallStatus("completed");

        // Clear pending notes
        preferences.remove("pending_scheduled_call_notes");
        preferences.remove("pending_scheduled_call_phone");
        preferences.remove("pending_scheduled_call_id");

        updateNotification("Call completed");

        // Stop service after 3 seconds
        handler.postDelayed(() -> stopSelf(), 3000);
    }

    private void markCallAsFailed(String reason) {
        Log.e(TAG, "Call failed: " + reason);
        updateCallStatus("failed");

        // Clear pending notes
        preferences.remove("pending_scheduled_call_notes");
        preferences.remove("pending_scheduled_call_phone");
        preferences.remove("pending_scheduled_call_id");

        updateNotification("Call failed: " + reason);

        // Stop service after 3 seconds
        handler.postDelayed(() -> stopSelf(), 3000);
    }

    private boolean hasCallPermissions() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Scheduled Calls",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for scheduled AI calls");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, HomeActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Scheduled Call")
                .setContentText("Preparing to call " + contactName)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .build();
    }

    private void updateNotification(String message) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Scheduled Call")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        Log.d(TAG, "ScheduledCallExecutor service destroyed");
    }
}