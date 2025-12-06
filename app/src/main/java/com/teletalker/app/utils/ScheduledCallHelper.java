package com.teletalker.app.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.teletalker.app.features.home.fragments.calender.data.data_sources.local.ScheduledCallDatabase;
import com.teletalker.app.features.home.fragments.calender.data.models.ScheduledCall;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight helper for querying scheduled call information from the database.
 * Provides minimal coupling between AICallRecorder/CallDetector and the database layer.
 *
 * This helper allows the call recording system to detect if an incoming/outgoing call
 * is a scheduled call and retrieve its associated AI conversation instructions.
 */
public class ScheduledCallHelper {
    private static final String TAG = "ScheduledCallHelper";

    // Time window for matching calls (calls scheduled within last 10 minutes)
    private static final long ACTIVE_CALL_WINDOW_MS = 10 * 60 * 1000; // 10 minutes

    // Grace period for scheduled calls (can execute up to 5 minutes late)
    private static final long SCHEDULED_CALL_GRACE_PERIOD_MS = 5 * 60 * 1000; // 5 minutes

    /**
     * Simple data class containing scheduled call information.
     * No database dependencies - just plain data.
     */
    public static class CallInfo {
        public final long callId;
        public final String phoneNumber;
        public final String contactName;
        public final String purpose;
        public final String notes;
        public final boolean isOutbound;
        public final long scheduledTime;
        public final int durationMinutes;
        public final String status;

        public CallInfo(long callId, String phoneNumber, String contactName,
                        String purpose, String notes, boolean isOutbound,
                        long scheduledTime, int durationMinutes, String status) {
            this.callId = callId;
            this.phoneNumber = phoneNumber;
            this.contactName = contactName;
            this.purpose = purpose;
            this.notes = notes;
            this.isOutbound = isOutbound;
            this.scheduledTime = scheduledTime;
            this.durationMinutes = durationMinutes;
            this.status = status;
        }

        /**
         * Check if this call info has valid data
         */
        public boolean isValid() {
            return phoneNumber != null && !phoneNumber.isEmpty() ;
        }

        /**
         * Check if this call has AI instructions
         */
        public boolean hasAIInstructions() {
            return notes != null && !notes.trim().isEmpty();
        }

        /**
         * Check if this call is overdue (past scheduled time + grace period)
         */
        public boolean isOverdue() {
            long overdueThreshold = scheduledTime + SCHEDULED_CALL_GRACE_PERIOD_MS;
            return System.currentTimeMillis() > overdueThreshold;
        }

        /**
         * Get time until scheduled (negative if overdue)
         */
        public long getTimeUntilScheduled() {
            return scheduledTime - System.currentTimeMillis();
        }

        /**
         * Convert to JSON for logging or transmission
         */
        public JSONObject toJSON() {
            try {
                JSONObject json = new JSONObject();
                json.put("call_id", callId);
                json.put("phone_number", phoneNumber);
                json.put("contact_name", contactName);
                json.put("purpose", purpose);
                json.put("notes_length", notes != null ? notes.length() : 0);
                json.put("is_outbound", isOutbound);
                json.put("scheduled_time", scheduledTime);
                json.put("duration_minutes", durationMinutes);
                json.put("status", status);
                return json;
            } catch (JSONException e) {
                Log.e(TAG, "Error converting CallInfo to JSON", e);
                return new JSONObject();
            }
        }

        @Override
        public String toString() {
            return "CallInfo{" +
                    "id=" + callId +
                    ", phone='" + maskPhoneNumber(phoneNumber) + '\'' +
                    ", contact='" + contactName + '\'' +
                    ", purpose='" + purpose + '\'' +
                    ", hasNotes=" + hasAIInstructions() +
                    ", outbound=" + isOutbound +
                    ", status='" + status + '\'' +
                    '}';
        }

        private String maskPhoneNumber(String phone) {
            if (phone == null || phone.length() < 4) return "****";
            return "****" + phone.substring(phone.length() - 4);
        }
    }

    // ============================================================================
    // MAIN QUERY METHODS
    // ============================================================================

//    public static CallInfo findActiveCallByPhoneNumber(Context context, String phoneNumber) {
//        if (context == null || phoneNumber == null || phoneNumber.trim().isEmpty()) {
//            Log.w(TAG, "Invalid parameters for findActiveCallByPhoneNumber");
//            return null;
//        }
//
//        try {
//            String normalizedPhone = normalizePhoneNumber(phoneNumber);
//            Log.d(TAG, "Searching for scheduled call: " + maskPhone(normalizedPhone));
//
//            ScheduledCallDatabase database = ScheduledCallDatabase.getInstance(context);
//
//            // Calculate time window
//            long now = System.currentTimeMillis();
//            long windowStart = now - ACTIVE_CALL_WINDOW_MS;
//            long windowEnd = now + SCHEDULED_CALL_GRACE_PERIOD_MS;
//
//            // Query database for matching calls
//            List<ScheduledCall> matchingCalls = database.scheduledCallDao()
//                    .getActiveCallsByPhoneNumber(normalizedPhone, windowStart, windowEnd);
//
//            if (matchingCalls == null || matchingCalls.isEmpty()) {
//                Log.d(TAG, "No active scheduled call found for: " + maskPhone(normalizedPhone));
//                return null;
//            }
//
//            // Get the most recent matching call
//            ScheduledCall scheduledCall = matchingCalls.get(0);
//
//            Log.d(TAG, "✅ Found scheduled call: ID=" + scheduledCall.getId() +
//                    ", Status=" + scheduledCall.getStatus() +
//                    ", Purpose=" + scheduledCall.getPurpose());
//
//            return convertToCallInfo(scheduledCall);
//
//        } catch (Exception e) {
//            Log.e(TAG, "Error finding scheduled call by phone number: " + e.getMessage(), e);
//            return null;
//        }
//    }



    // 1. ADD CALLBACK INTERFACE
    public interface CallInfoCallback {
        void onCallInfoFound(CallInfo callInfo);
        void onCallInfoNotFound();
        void onError(Exception e);
    }

    // 2. ADD THESE AT TOP OF CLASS
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 3. REPLACE YOUR METHOD WITH THIS
    public static void findActiveCallByPhoneNumber(Context context, String phoneNumber, CallInfoCallback callback) {
        if (context == null || phoneNumber == null || phoneNumber.trim().isEmpty()) {
            mainHandler.post(callback::onCallInfoNotFound);
            return;
        }

        executorService.execute(() -> {
            try {
                String normalizedPhone = normalizePhoneNumber(phoneNumber);
                ScheduledCallDatabase database = ScheduledCallDatabase.getInstance(context);

                long now = System.currentTimeMillis();
                long windowStart = now - ACTIVE_CALL_WINDOW_MS;
                long windowEnd = now + SCHEDULED_CALL_GRACE_PERIOD_MS;

                List<ScheduledCall> matchingCalls = database.scheduledCallDao()
                        .getActiveCallsByPhoneNumber(normalizedPhone, windowStart, windowEnd);

                if (matchingCalls == null || matchingCalls.isEmpty()) {
                    mainHandler.post(callback::onCallInfoNotFound);
                    return;
                }

                CallInfo callInfo = convertToCallInfo(matchingCalls.get(0));
                mainHandler.post(() -> callback.onCallInfoFound(callInfo));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    /**
     * Find a scheduled call by its database ID.
     *
     * @param context Application context
     * @param callId Database ID of the scheduled call
     * @return CallInfo if found, null otherwise
     */
    public static CallInfo findCallById(Context context, long callId) {
        if (context == null || callId <= 0) {
            Log.w(TAG, "Invalid parameters for findCallById");
            return null;
        }

        try {
            Log.d(TAG, "Looking up scheduled call by ID: " + callId);

            ScheduledCallDatabase database = ScheduledCallDatabase.getInstance(context);
            ScheduledCall scheduledCall = database.scheduledCallDao().getCallById(callId);

            if (scheduledCall != null) {
                Log.d(TAG, "✅ Found scheduled call by ID: " + callId);
                return convertToCallInfo(scheduledCall);
            } else {
                Log.d(TAG, "No scheduled call found with ID: " + callId);
                return null;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error finding scheduled call by ID: " + e.getMessage(), e);
            return null;
        }
    }



    public static CallInfo getMostRecentActiveCall(Context context) {
        if (context == null) {
            Log.w(TAG, "Invalid context for getMostRecentActiveCall");
            return null;
        }

        try {
            Log.d(TAG, "Looking for most recent active scheduled call");

            ScheduledCallDatabase database = ScheduledCallDatabase.getInstance(context);

            // First try to get in_progress calls
            ScheduledCall scheduledCall = database.scheduledCallDao()
                    .getMostRecentInProgressCall();

            // If no in_progress calls, get most recent pending call
            if (scheduledCall == null) {
                long now = System.currentTimeMillis();
                long windowStart = now - ACTIVE_CALL_WINDOW_MS;

                scheduledCall = database.scheduledCallDao()
                        .getMostRecentPendingCall(windowStart);
            }

            if (scheduledCall != null) {
                Log.d(TAG, "✅ Found recent active call: ID=" + scheduledCall.getId() +
                        ", Status=" + scheduledCall.getStatus());
                return convertToCallInfo(scheduledCall);
            } else {
                Log.d(TAG, "No recent active scheduled calls found");
                return null;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error getting recent active call: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get all pending calls that are due for execution within the specified time window.
     *
     * @param context Application context
     * @param withinMillis Time window in milliseconds (e.g., 60000 for next minute)
     * @return List of CallInfo for due calls (may be empty)
     */
    public static List<CallInfo> getDueCallsWithinWindow(Context context, long withinMillis) {
        if (context == null) {
            Log.w(TAG, "Invalid context for getDueCallsWithinWindow");
            return null;
        }

        try {
            long now = System.currentTimeMillis();
            long windowEnd = now + withinMillis;

            ScheduledCallDatabase database = ScheduledCallDatabase.getInstance(context);
            List<ScheduledCall> dueCalls = database.scheduledCallDao()
                    .getPendingCallsDue(windowEnd);

            if (dueCalls == null || dueCalls.isEmpty()) {
                return null;
            }

            Log.d(TAG, "Found " + dueCalls.size() + " due call(s) within " + withinMillis + "ms");

            // Convert to CallInfo list
            return dueCalls.stream()
                    .map(ScheduledCallHelper::convertToCallInfo)
                    .collect(java.util.stream.Collectors.toList());

        } catch (Exception e) {
            Log.e(TAG, "Error getting due calls: " + e.getMessage(), e);
            return null;
        }
    }

    // ============================================================================
    // STATUS UPDATE METHODS
    // ============================================================================

    /**
     * Mark a scheduled call as in progress (call is currently active).
     * Also records the actual start time.
     *
     * @param context Application context
     * @param callId Database ID of the scheduled call
     */
    public static void markCallInProgress(Context context, long callId) {
        if (context == null || callId <= 0) {
            Log.w(TAG, "Invalid parameters for markCallInProgress");
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                ScheduledCallDatabase database = ScheduledCallDatabase.getInstance(context);

                // Get the call to update it
                ScheduledCall call = database.scheduledCallDao().getCallById(callId);
                if (call != null) {
                    call.markStarted();
                    database.scheduledCallDao().update(call);

                    Log.d(TAG, "✅ Marked call " + callId + " as IN_PROGRESS");
                } else {
                    Log.w(TAG, "Call " + callId + " not found for status update");
                }

            } catch (Exception e) {
                Log.e(TAG, "Error marking call in progress: " + e.getMessage(), e);
            }
        });
        executor.shutdown();
    }

    /**
     * Mark a scheduled call as completed.
     * Also records the actual end time and calculates duration.
     *
     * @param context Application context
     * @param callId Database ID of the scheduled call
     */
    public static void markCallCompleted(Context context, long callId) {
        if (context == null || callId <= 0) {
            Log.w(TAG, "Invalid parameters for markCallCompleted");
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                ScheduledCallDatabase database = ScheduledCallDatabase.getInstance(context);

                // Get the call to update it
                ScheduledCall call = database.scheduledCallDao().getCallById(callId);
                if (call != null) {
                    call.markCompleted();
                    database.scheduledCallDao().update(call);

                    Log.d(TAG, "✅ Marked call " + callId + " as COMPLETED (Duration: " +
                            call.getActualCallDurationSeconds() + "s)");
                } else {
                    Log.w(TAG, "Call " + callId + " not found for completion update");
                }

            } catch (Exception e) {
                Log.e(TAG, "Error marking call completed: " + e.getMessage(), e);
            }
        });
        executor.shutdown();
    }

    /**
     * Mark a scheduled call as failed.
     * Records the failure time.
     *
     * @param context Application context
     * @param callId Database ID of the scheduled call
     */
    public static void markCallFailed(Context context, long callId) {
        if (context == null || callId <= 0) {
            Log.w(TAG, "Invalid parameters for markCallFailed");
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                ScheduledCallDatabase database = ScheduledCallDatabase.getInstance(context);

                // Get the call to update it
                ScheduledCall call = database.scheduledCallDao().getCallById(callId);
                if (call != null) {
                    call.markFailed();
                    database.scheduledCallDao().update(call);

                    Log.d(TAG, "✅ Marked call " + callId + " as FAILED");
                } else {
                    Log.w(TAG, "Call " + callId + " not found for failure update");
                }

            } catch (Exception e) {
                Log.e(TAG, "Error marking call failed: " + e.getMessage(), e);
            }
        });
        executor.shutdown();
    }

    /**
     * Update the call SID (from Twilio/ElevenLabs) for tracking.
     *
     * @param context Application context
     * @param callId Database ID of the scheduled call
     * @param callSid Call SID from telephony provider
     */
//    public static void updateCallSid(Context context, long callId, String callSid) {
//        if (context == null || callId <= 0 || callSid == null) {
//            Log.w(TAG, "Invalid parameters for updateCallSid");
//            return;
//        }
//
//        ExecutorService executor = Executors.newSingleThreadExecutor();
//        executor.execute(() -> {
//            try {
//                ScheduledCallDatabase database = ScheduledCallDatabase.getInstance(context);
//                database.scheduledCallDao().updateCallSid(callId, callSid);
//
//                Log.d(TAG, "✅ Updated call " + callId + " with SID: " + callSid);
//
//            } catch (Exception e) {
//                Log.e(TAG, "Error updating call SID: " + e.getMessage(), e);
//            }
//        });
//        executor.shutdown();
//    }

    // ============================================================================
    // UTILITY METHODS
    // ============================================================================

    /**
     * Convert a ScheduledCall entity to a CallInfo data object.
     */
    private static CallInfo convertToCallInfo(ScheduledCall scheduledCall) {
        if (scheduledCall == null) return null;

        return new CallInfo(
                scheduledCall.getId(),
                scheduledCall.getPhoneNumber(),
                scheduledCall.getContactName(),
                scheduledCall.getPurpose(),
                scheduledCall.getConversationNotes(),
                true, // Scheduled calls are always outbound
                scheduledCall.getScheduledDateTime(),
                scheduledCall.getDurationMinutes(),
                scheduledCall.getStatus()
        );
    }

    public static void cancelAllScheduledCalls(Context context, String reason) {
        List<CallInfo> calls = getAllScheduledCalls(context);


        for (CallInfo call : calls) {
            markCallCancelled(context, call.callId, reason);
        }
        Toast.makeText(context, "All scheduled calls cancelled: " + reason, Toast.LENGTH_LONG).show();
    }


    public static void getAllScheduledCallsAsync(Context context, OnCallsLoadedCallback callback) {
        new Thread(() -> {
            List<CallInfo> calls = getAllScheduledCalls(context);
            new Handler(Looper.getMainLooper()).post(() -> callback.onLoaded(calls));
        }).start();
    }

    public static void markCallCancelledAsync(Context context, long callId, String reason) {
        new Thread(() -> markCallCancelled(context, callId, reason)).start();
    }

    public interface OnCallsLoadedCallback {
        void onLoaded(List<CallInfo> calls);
    }


    public static List<CallInfo> getAllScheduledCalls(Context context) {
        List<CallInfo> scheduledCalls = new ArrayList<>();

        try {
            ScheduledCallDatabase db = ScheduledCallDatabase.getInstance(context);
            List<ScheduledCall> dbCalls = db.scheduledCallDao().getPendingCallsDue(Long.MAX_VALUE);
            for (ScheduledCall call : dbCalls) {
                if ("pending".equals(call.getStatus()) || "in_progress".equals(call.getStatus())) {
                    CallInfo info =   new CallInfo(call.getId(), call.getPhoneNumber(), call.getContactName(), call.getPurpose(), call.getConversationNotes(), true, call.getScheduledDateTime(), call.getDurationMinutes(), call.getStatus());
                    scheduledCalls.add(info);
                }
            }
            Log.d(TAG, "Retrieved " + scheduledCalls.size() + " scheduled calls");
        } catch (Exception e) {
            Log.e(TAG, "Error getting scheduled calls from database", e);
        }

        return scheduledCalls;
    }

    public static void markCallCancelled(Context context, long callId, String reason) {
        try {
            ScheduledCallDatabase db = ScheduledCallDatabase.getInstance(context);
            ScheduledCall call = db.scheduledCallDao().getCallById(callId);
            if (call != null) {
                call.setStatus("cancelled");
//                call.setConversationNotes(call.getConversationNotes() + "\n[Cancelled: " + reason + "]");
                db.scheduledCallDao().update(call);
                Log.d(TAG, "Call " + callId + " cancelled: " + reason);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error marking call cancelled", e);
        }
    }

    /**
     * Normalize phone number for comparison.
     * Removes all non-digit characters except the leading '+'.
     *
     * Examples:
     * - "+1 (555) 123-4567" -> "+15551234567"
     * - "555-123-4567" -> "5551234567"
     * - "+44 20 1234 5678" -> "+442012345678"
     */
    public static String normalizePhoneNumber(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return "";
        }

        String normalized = phone.trim();

        // Keep leading '+' if present
        boolean hasPlus = normalized.startsWith("+");

        // Remove all non-digit characters
        normalized = normalized.replaceAll("[^0-9]", "");

        // Restore leading '+' if it was there
        if (hasPlus && !normalized.isEmpty()) {
            normalized = "+" + normalized;
        }

        return normalized;
    }

    /**
     * Mask phone number for logging (shows only last 4 digits).
     */
    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return "****" + phone.substring(phone.length() - 4);
    }

    /**
     * Check if a phone number matches a scheduled call's number.
     * Handles different formats and country codes.
     */
    public static boolean phoneNumbersMatch(String phone1, String phone2) {
        if (phone1 == null || phone2 == null) {
            return false;
        }

        String normalized1 = normalizePhoneNumber(phone1);
        String normalized2 = normalizePhoneNumber(phone2);

        // Exact match
        if (normalized1.equals(normalized2)) {
            return true;
        }

        // Try matching without country code (last 10 digits for US)
        if (normalized1.length() >= 10 && normalized2.length() >= 10) {
            String suffix1 = normalized1.substring(normalized1.length() - 10);
            String suffix2 = normalized2.substring(normalized2.length() - 10);
            return suffix1.equals(suffix2);
        }

        return false;
    }

    /**
     * Check if a call is within the execution window.
     */
    public static boolean isWithinExecutionWindow(long scheduledTime) {
        long now = System.currentTimeMillis();
        long timeDifference = now - scheduledTime;

        // Can execute if scheduled time is within grace period (past or future)
        return timeDifference >= -SCHEDULED_CALL_GRACE_PERIOD_MS &&
                timeDifference <= SCHEDULED_CALL_GRACE_PERIOD_MS;
    }

    /**
     * Get a human-readable status message for debugging.
     */
    public static String getStatusSummary(Context context) {
        try {
            ScheduledCallDatabase database = ScheduledCallDatabase.getInstance(context);

            int pendingCount = database.scheduledCallDao().getCountByStatus("pending");
            int inProgressCount = database.scheduledCallDao().getCountByStatus("in_progress");
            int completedCount = database.scheduledCallDao().getCountByStatus("completed");
            int failedCount = database.scheduledCallDao().getCountByStatus("failed");

            return String.format("Scheduled Calls - Pending: %d, In Progress: %d, Completed: %d, Failed: %d",
                    pendingCount, inProgressCount, completedCount, failedCount);

        } catch (Exception e) {
            Log.e(TAG, "Error getting status summary: " + e.getMessage(), e);
            return "Error getting status";
        }
    }
}