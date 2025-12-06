package com.teletalker.app.utils;


import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Decoupled bridge for passing call context between components
 * No coupling - any component can write/read call context
 */
public class CallContextManager {
    private static final String TAG = "CallContextManager";
    private static final String PREFS_NAME = "call_context_cache";

    // Keys for storing call context
    private static final String KEY_PHONE_NUMBER = "phone_number";
    private static final String KEY_CALL_PURPOSE = "call_purpose";
    private static final String KEY_CONVERSATION_NOTES = "conversation_notes";
    private static final String KEY_IS_OUTBOUND = "is_outbound";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_CALL_ID = "call_id";
    private static final String KEY_HAS_CONTEXT = "has_context";

    // Context expires after 2 minutes (in case call never happens)
    private static final long CONTEXT_TIMEOUT_MS = 2 * 60 * 1000;

    private final SharedPreferences prefs;

    public CallContextManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Store call context for upcoming outbound call
     * Call this BEFORE making the phone call
     */
    public void setOutboundCallContext(
            String phoneNumber,
            String purpose,
            String conversationNotes,
            long callId) {

        Log.d(TAG, "Storing outbound call context for: " + phoneNumber);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_PHONE_NUMBER, phoneNumber);
        editor.putString(KEY_CALL_PURPOSE, purpose);
        editor.putString(KEY_CONVERSATION_NOTES, conversationNotes);
        editor.putBoolean(KEY_IS_OUTBOUND, true);
        editor.putLong(KEY_TIMESTAMP, System.currentTimeMillis());
        editor.putLong(KEY_CALL_ID, callId);
        editor.putBoolean(KEY_HAS_CONTEXT, true);
        editor.apply();

        Log.d(TAG, "Call context stored - Purpose: " + purpose);
    }

    /**
     * Check if there's pending call context for a phone number
     * Returns null if no context or context expired
     */
    public CallContext getCallContext(String phoneNumber) {
        if (!prefs.getBoolean(KEY_HAS_CONTEXT, false)) {
            return null;
        }

        // Check if context expired
        long timestamp = prefs.getLong(KEY_TIMESTAMP, 0);
        long age = System.currentTimeMillis() - timestamp;

        if (age > CONTEXT_TIMEOUT_MS) {
            Log.d(TAG, "Call context expired (" + age + "ms old)");
            clearCallContext();
            return null;
        }

        // Check if phone number matches (or if it's for any outbound call)
        String storedPhone = prefs.getString(KEY_PHONE_NUMBER, null);
        if (storedPhone != null && phoneNumber != null) {
            // Normalize phone numbers for comparison
            String normalizedStored = normalizePhoneNumber(storedPhone);
            String normalizedCurrent = normalizePhoneNumber(phoneNumber);

            if (!normalizedStored.equals(normalizedCurrent)) {
                Log.d(TAG, "Phone number mismatch: stored=" + normalizedStored +
                        ", current=" + normalizedCurrent);
                return null;
            }
        }

        // Build and return context
        CallContext context = new CallContext();
        context.phoneNumber = storedPhone;
        context.purpose = prefs.getString(KEY_CALL_PURPOSE, null);
        context.conversationNotes = prefs.getString(KEY_CONVERSATION_NOTES, null);
        context.isOutbound = prefs.getBoolean(KEY_IS_OUTBOUND, false);
        context.callId = prefs.getLong(KEY_CALL_ID, -1);
        context.timestamp = timestamp;

        Log.d(TAG, "Retrieved call context - Outbound: " + context.isOutbound +
                ", Purpose: " + context.purpose);

        return context;
    }

    /**
     * Get call context without phone number matching (for outbound calls)
     */
    public CallContext getPendingOutboundContext() {
        if (!prefs.getBoolean(KEY_HAS_CONTEXT, false)) {
            return null;
        }

        if (!prefs.getBoolean(KEY_IS_OUTBOUND, false)) {
            return null;
        }

        // Check if context expired
        long timestamp = prefs.getLong(KEY_TIMESTAMP, 0);
        long age = System.currentTimeMillis() - timestamp;

        if (age > CONTEXT_TIMEOUT_MS) {
            Log.d(TAG, "Outbound context expired");
            clearCallContext();
            return null;
        }

        CallContext context = new CallContext();
        context.phoneNumber = prefs.getString(KEY_PHONE_NUMBER, null);
        context.purpose = prefs.getString(KEY_CALL_PURPOSE, null);
        context.conversationNotes = prefs.getString(KEY_CONVERSATION_NOTES, null);
        context.isOutbound = true;
        context.callId = prefs.getLong(KEY_CALL_ID, -1);
        context.timestamp = timestamp;

        Log.d(TAG, "Retrieved pending outbound context");
        return context;
    }

    /**
     * Clear call context after use
     */
    public void clearCallContext() {
        Log.d(TAG, "Clearing call context");
        prefs.edit().clear().apply();
    }

    /**
     * Check if there's any pending call context
     */
    public boolean hasContext() {
        boolean has = prefs.getBoolean(KEY_HAS_CONTEXT, false);
        if (has) {
            // Verify not expired
            long age = System.currentTimeMillis() - prefs.getLong(KEY_TIMESTAMP, 0);
            if (age > CONTEXT_TIMEOUT_MS) {
                clearCallContext();
                return false;
            }
        }
        return has;
    }

    /**
     * Normalize phone number for comparison (remove spaces, dashes, etc.)
     */
    private String normalizePhoneNumber(String phone) {
        if (phone == null) return "";
        return phone.replaceAll("[^0-9+]", "");
    }

    /**
     * Data class to hold call context
     */
    public static class CallContext {
        public String phoneNumber;
        public String purpose;
        public String conversationNotes;
        public boolean isOutbound;
        public long callId;
        public long timestamp;

        public boolean isValid() {
            return phoneNumber != null && conversationNotes != null;
        }

        public JSONObject toJSON() {
            try {
                JSONObject json = new JSONObject();
                json.put("phone_number", phoneNumber);
                json.put("purpose", purpose);
                json.put("conversation_notes", conversationNotes);
                json.put("is_outbound", isOutbound);
                json.put("call_id", callId);
                json.put("timestamp", timestamp);
                return json;
            } catch (JSONException e) {
                Log.e("CallContext", "Error converting to JSON", e);
                return null;
            }
        }

        @Override
        public String toString() {
            return "CallContext{" +
                    "phone='" + phoneNumber + '\'' +
                    ", purpose='" + purpose + '\'' +
                    ", outbound=" + isOutbound +
                    ", notesLength=" + (conversationNotes != null ? conversationNotes.length() : 0) +
                    '}';
        }
    }
}