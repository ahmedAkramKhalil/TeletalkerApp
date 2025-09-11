package com.teletalker.app.services;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

// StatusBroadcastManager.java
public class StatusBroadcastManager {
    private static final String TAG = "StatusBroadcastManager";

    // Broadcast actions
    public static final String ACTION_CALL_STATUS_CHANGED = "com.teletalker.CALL_STATUS_CHANGED";
    public static final String ACTION_AI_STATUS_CHANGED = "com.teletalker.AI_STATUS_CHANGED";

    // Intent extras
    public static final String EXTRA_CALL_STATUS = "call_status";
    public static final String EXTRA_PHONE_NUMBER = "phone_number";
    public static final String EXTRA_CONTACT_NAME = "contact_name";
    public static final String EXTRA_AI_STATUS = "ai_status";
    public static final String EXTRA_IS_RECORDING = "is_recording";
    public static final String EXTRA_IS_INJECTING = "is_injecting";
    public static final String EXTRA_AI_RESPONSE = "ai_response";

    public static void broadcastCallStatus(Context context, String callStatus,
                                           String phoneNumber, String contactName) {
        Intent intent = new Intent(ACTION_CALL_STATUS_CHANGED);
        intent.putExtra(EXTRA_CALL_STATUS, callStatus);
        intent.putExtra(EXTRA_PHONE_NUMBER, phoneNumber);
        intent.putExtra(EXTRA_CONTACT_NAME, contactName);

        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        Log.d(TAG, "Broadcast call status: " + callStatus + " for " + contactName);
    }

    public static void broadcastAIStatus(Context context, String aiStatus,
                                         boolean isRecording, boolean isInjecting, String response) {
        Intent intent = new Intent(ACTION_AI_STATUS_CHANGED);
        intent.putExtra(EXTRA_AI_STATUS, aiStatus);
        intent.putExtra(EXTRA_IS_RECORDING, isRecording);
        intent.putExtra(EXTRA_IS_INJECTING, isInjecting);
        intent.putExtra(EXTRA_AI_RESPONSE, response);

        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        Log.d(TAG, "Broadcast AI status: " + aiStatus + ", Recording: " + isRecording + ", Injecting: " + isInjecting);
    }
}