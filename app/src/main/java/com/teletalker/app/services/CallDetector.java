package com.teletalker.app.services;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.ContactsContract;
import android.telecom.Call;
import android.telecom.InCallService;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.teletalker.app.R;
import com.teletalker.app.features.home.HomeActivity;
import com.teletalker.app.features.home.fragments.callhistory.data.data_sources.local.database.CallDatabase;
import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;
import com.teletalker.app.services.ai.AICallRecorderRefactored;
import com.teletalker.app.services.ai.CallRecorder;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;

/**
 * Enhanced CallDetector with Complete AI Integration and Audio Injection Support
 * Now uses AICallRecorder for real-time AI conversation with audio injection
 */
public class CallDetector extends InCallService {
    private static final String TAG = "TeleTalkerAIInCallService";
    private static final String CHANNEL_ID = "teletalker_ai_call_service";
    private static final String PHONE_PACKAGE = "com.android.phone";

    // Action constants for notification buttons
    private static final String ACTION_PAUSE = "com.teletalker.app.services.CallDetector.pause";
    private static final String ACTION_RESUME = "com.teletalker.app.services.CallDetector.resume";
    private static final String ACTION_RESTORE = "com.teletalker.app.services.CallDetector.restore";
    private static final String ACTION_DELETE = "com.teletalker.app.services.CallDetector.delete";
    private static final String ACTION_TOGGLE_AI = "com.teletalker.app.services.CallDetector.toggle_ai";
    private static final String ACTION_CHANGE_AI_MODE = "com.teletalker.app.services.CallDetector.change_ai_mode";
    private static final String ACTION_TOGGLE_INJECTION = "com.teletalker.app.services.CallDetector.toggle_injection";
    private static final String EXTRA_TOKEN = "token";
    private static final String EXTRA_NOTIFICATION_ID = "notification_id";
    private static final String EXTRA_AI_MODE = "ai_mode";

    // SharedPreferences keys for AI configuration
    private static final String PREFS_NAME = "teletalker_ai_config";
    private static final String PREF_ELEVENLABS_API_KEY = "elevenlabs_api_key";
    private static final String PREF_AGENT_ID = "agent_id";
    private static final String PREF_AI_MODE = "ai_mode";
    private static final String PREF_AI_ENABLED = "ai_enabled";
    private static final String PREF_INJECTION_ENABLED = "injection_enabled";

    private Handler handler;
    private NotificationManager notificationManager;
    private PowerManager.WakeLock wakeLock;

    // Security token to prevent third-party interference
    private byte[] token;

    // Notification management - Enhanced for AI with Injection
    private int foregroundNotificationId;
    private Map<Integer, AICallRecorderWrapper> notificationIdsToRecorders = new HashMap<>();
    private Map<Integer, NotificationState> allNotificationIds = new HashMap<>();

    // Call tracking
    private Map<Call, AICallRecorderWrapper> callsToRecorders = new HashMap<>();

    // AI Configuration
    private SharedPreferences aiPreferences;
    private String elevenLabsApiKey;
    private String agentId;
    private AICallRecorderRefactored.AIMode currentAIMode = AICallRecorderRefactored.AIMode.SMART_ASSISTANT;
    private boolean isAIEnabled = true;
    private boolean isInjectionEnabled = true;

    // ===== STATIC CONFIGURATION METHODS =====

    /**
     * Configure AI credentials and settings
     */
    public static void configureAI(Context context, String apiKey, String agentId,
                                   AICallRecorderRefactored .AIMode aiMode, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(PREF_ELEVENLABS_API_KEY, apiKey)
                .putString(PREF_AGENT_ID, agentId)
                .putString(PREF_AI_MODE, aiMode.name())
                .putBoolean(PREF_AI_ENABLED, enabled)
                .putBoolean(PREF_INJECTION_ENABLED, true) // Enable injection by default
                .apply();

        Log.d("CallDetector", "✅ AI Configuration saved - Enabled: " + enabled + ", Mode: " + aiMode + ", Injection: ON");
    }

    /**
     * Configure audio injection setting
     */
    public static void configureAudioInjection(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(PREF_INJECTION_ENABLED, enabled)
                .apply();

        Log.d("CallDetector", "🎧 Audio injection " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Get current AI configuration status
     */
    public static boolean isAIConfigured(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String apiKey = prefs.getString(PREF_ELEVENLABS_API_KEY, null);
        String agentId = prefs.getString(PREF_AGENT_ID, null);
        return apiKey != null && agentId != null && !apiKey.isEmpty() && !agentId.isEmpty();
    }

    // ===== ENHANCED WRAPPER AND STATE CLASSES =====

    private static class NotificationState {
        final int titleResId;
        final String message;
        final boolean canShowDelete;
        final boolean isPaused;
        final boolean isHolding;
        final boolean isAIConnected;
        final boolean isAIResponding;
        final AICallRecorderRefactored .AIMode aiMode;
        final boolean isAudioInjectionActive;
        final String audioInjectionMethod;

        NotificationState(int titleResId, String message, boolean canShowDelete, boolean isPaused,
                          boolean isHolding, boolean isAIConnected, boolean isAIResponding,
                          AICallRecorderRefactored .AIMode aiMode, boolean isAudioInjectionActive,
                          String audioInjectionMethod) {
            this.titleResId = titleResId;
            this.message = message;
            this.canShowDelete = canShowDelete;
            this.isPaused = isPaused;
            this.isHolding = isHolding;
            this.isAIConnected = isAIConnected;
            this.isAIResponding = isAIResponding;
            this.aiMode = aiMode;
            this.isAudioInjectionActive = isAudioInjectionActive;
            this.audioInjectionMethod = audioInjectionMethod;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            NotificationState that = (NotificationState) obj;
            return titleResId == that.titleResId &&
                    canShowDelete == that.canShowDelete &&
                    isPaused == that.isPaused &&
                    isHolding == that.isHolding &&
                    isAIConnected == that.isAIConnected &&
                    isAIResponding == that.isAIResponding &&
                    isAudioInjectionActive == that.isAudioInjectionActive &&
                    aiMode == that.aiMode &&
                    (message != null ? message.equals(that.message) : that.message == null) &&
                    (audioInjectionMethod != null ? audioInjectionMethod.equals(that.audioInjectionMethod) : that.audioInjectionMethod == null);
        }
    }

    private static class AICallRecorderWrapper {
        enum State {
            NOT_STARTED, RECORDING, FINALIZING, COMPLETED
        }

        enum KeepState {
            KEEP, DISCARD, DISCARD_TOO_SHORT
        }

        final AICallRecorderRefactored recorder;
        final CallInfo callInfo;
        State state = State.NOT_STARTED;
        KeepState keepState = KeepState.KEEP;
        boolean isPaused = false;
        boolean isHolding = false;
        boolean isAIConnected = false;
        boolean isAIResponding = false;
        String lastAIResponse = "";
        Call call;

        // Audio Injection Status
        boolean isAudioInjectionActive = false;
        String audioInjectionMethod = "None";
        int injectedChunks = 0;
        long injectedBytes = 0;

        AICallRecorderWrapper(AICallRecorderRefactored recorder, CallInfo callInfo, Call call) {
            this.recorder = recorder;
            this.callInfo = callInfo;
            this.call = call;
        }

        String getOutputPath() {
            return callInfo.recordingFile != null ? callInfo.recordingFile : "Unknown";
        }

        String getAIStatusSummary() {
            StringBuilder status = new StringBuilder();

            if (callInfo.aiEnabled) {
                if (isAIConnected) {
                    status.append("🤖 AI Active");
                    if (isAudioInjectionActive) {
                        status.append(" + 🎧 Injecting (").append(audioInjectionMethod).append(")");
                    }
                    if (isAIResponding) {
                        status.append(" 🗣️ Speaking");
                    }
                } else {
                    status.append("🤖 AI Connecting...");
                }
            } else {
                status.append("🤖 AI Disabled");
            }

            return status.toString();
        }
    }

    private static class CallInfo {
        String phoneNumber;
        String contactName;
        String direction; // "in", "out", "conference"
        long callStartTime;
        long callAnswerTime;
        boolean isRecorded;
        public CallRecorder.RecordingMode recordingMode;  // ✅ CHANGE FROM AICallRecorder.RecordingMode

        String recordingFile;
        AICallRecorderRefactored.AIMode aiMode;
        boolean aiEnabled;
        boolean injectionEnabled;

        CallInfo() {
            isRecorded = false;
            callStartTime = System.currentTimeMillis();
            aiEnabled = true;
            injectionEnabled = true;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🚀 TeleTalker AI InCall Service with Injection created");

        handler = new Handler(Looper.getMainLooper());
        notificationManager = getSystemService(NotificationManager.class);

        // Initialize AI configuration
        initializeAIConfiguration();

        // Generate security token
        token = new byte[128];
        new Random().nextBytes(token);

        // Generate foreground notification ID
        foregroundNotificationId = generateNotificationId();

        createNotificationChannel();
        acquireWakeLock();

        // Log comprehensive configuration status
        logAIConfigurationStatus();
    }

    private void initializeAIConfiguration() {
        aiPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        elevenLabsApiKey = aiPreferences.getString(PREF_ELEVENLABS_API_KEY, null);
        agentId = aiPreferences.getString(PREF_AGENT_ID, null);
        isAIEnabled = aiPreferences.getBoolean(PREF_AI_ENABLED, true);
        isInjectionEnabled = aiPreferences.getBoolean(PREF_INJECTION_ENABLED, true);

        String aiModeString = aiPreferences.getString(PREF_AI_MODE, AICallRecorderRefactored .AIMode.SMART_ASSISTANT.name());
        try {
            currentAIMode = AICallRecorderRefactored .AIMode.valueOf(aiModeString);
        } catch (IllegalArgumentException e) {
            currentAIMode = AICallRecorderRefactored .AIMode.SMART_ASSISTANT;
        }
    }

    private void logAIConfigurationStatus() {
        Log.d(TAG, "═══════════════════════════════════════════════");
        Log.d(TAG, "🤖 AI CONFIGURATION STATUS:");
        Log.d(TAG, "✅ AI Enabled: " + isAIEnabled);
        Log.d(TAG, "🔑 API Key: " + (elevenLabsApiKey != null ? "***SET***" : "NOT SET"));
        Log.d(TAG, "🆔 Agent ID: " + (agentId != null ? agentId : "NOT SET"));
        Log.d(TAG, "🎯 AI Mode: " + currentAIMode);
        Log.d(TAG, "🎧 Audio Injection: " + (isInjectionEnabled ? "ENABLED" : "DISABLED"));
        Log.d(TAG, "🎉 Ready for AI calls: " + (isAIEnabled && elevenLabsApiKey != null && agentId != null));
        Log.d(TAG, "═══════════════════════════════════════════════");
    }

    public void updateAIConfiguration(String apiKey, String agentId, AICallRecorderRefactored .AIMode aiMode, boolean enabled) {
        this.elevenLabsApiKey = apiKey;
        this.agentId = agentId;
        this.currentAIMode = aiMode;
        this.isAIEnabled = enabled;

        // Save to preferences
        aiPreferences.edit()
                .putString(PREF_ELEVENLABS_API_KEY, apiKey)
                .putString(PREF_AGENT_ID, agentId)
                .putString(PREF_AI_MODE, aiMode.name())
                .putBoolean(PREF_AI_ENABLED, enabled)
                .apply();

        Log.d(TAG, "🔄 AI Configuration updated dynamically");
        logAIConfigurationStatus();
    }

    // ===== ENHANCED NOTIFICATION ACTION HANDLING =====

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Handle notification action intents ONLY
        try {
            byte[] receivedToken = intent != null ? intent.getByteArrayExtra(EXTRA_TOKEN) : null;
            if (receivedToken != null && java.util.Arrays.equals(receivedToken, token)) {
                int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1);
                if (notificationId != -1) {
                    handleNotificationAction(intent.getAction(), intent, notificationId);
                }
            } else if (receivedToken != null) {
                Log.w(TAG, "Invalid token received in intent");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to handle intent: " + intent, e);
        }

        stopSelf(startId);
        return START_NOT_STICKY;
    }

    private void handleNotificationAction(String action, Intent intent, int notificationId) {
        AICallRecorderWrapper wrapper = notificationIdsToRecorders.get(notificationId);
        if (wrapper == null) return;

        switch (action) {
            case ACTION_PAUSE:
                wrapper.isPaused = true;
                pauseRecording(wrapper);
                break;
            case ACTION_RESUME:
                wrapper.isPaused = false;
                resumeRecording(wrapper);
                break;
            case ACTION_RESTORE:
                wrapper.keepState = AICallRecorderWrapper.KeepState.KEEP;
                break;
            case ACTION_DELETE:
                wrapper.keepState = AICallRecorderWrapper.KeepState.DISCARD;
                break;
            case ACTION_TOGGLE_AI:
                toggleAI(wrapper);
                break;
            case ACTION_CHANGE_AI_MODE:
                changeAIMode(wrapper, intent);
                break;
            case ACTION_TOGGLE_INJECTION:
                toggleAudioInjection(wrapper);
                break;
        }
        updateForegroundState();
    }

    private void toggleAI(AICallRecorderWrapper wrapper) {
        wrapper.callInfo.aiEnabled = !wrapper.callInfo.aiEnabled;

        if (wrapper.callInfo.aiEnabled && elevenLabsApiKey != null && agentId != null) {
            wrapper.recorder.setElevenLabsConfig(elevenLabsApiKey, agentId);
            wrapper.recorder.setAIMode(currentAIMode);
        }

        Log.d(TAG, "🤖 AI toggled for call: " + wrapper.callInfo.aiEnabled);
    }

    private void changeAIMode(AICallRecorderWrapper wrapper, Intent intent) {
        String aiModeString = intent.getStringExtra(EXTRA_AI_MODE);
        if (aiModeString != null) {
            try {
                AICallRecorderRefactored .AIMode newMode = AICallRecorderRefactored .AIMode.valueOf(aiModeString);
                wrapper.recorder.setAIMode(newMode);
                wrapper.callInfo.aiMode = newMode;
                Log.d(TAG, "🎯 AI mode changed to: " + newMode);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid AI mode: " + aiModeString);
            }
        }
    }

    private void toggleAudioInjection(AICallRecorderWrapper wrapper) {
        wrapper.callInfo.injectionEnabled = !wrapper.callInfo.injectionEnabled;
        Log.d(TAG, "🎧 Audio injection toggled for call: " + wrapper.callInfo.injectionEnabled);
    }

    private void pauseRecording(AICallRecorderWrapper wrapper) {
        Log.d(TAG, "🔸 Pausing AI recording");
        updateForegroundState();
    }

    private void resumeRecording(AICallRecorderWrapper wrapper) {
        Log.d(TAG, "▶️ Resuming AI recording");
        updateForegroundState();
    }

    // ===== CALL STATE MANAGEMENT =====

    private final Call.Callback callCallback = new Call.Callback() {
        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        @Override
        public void onStateChanged(Call call, int state) {
            super.onStateChanged(call, state);
            Log.d(TAG, "📞 Call state changed: " + call + ", " + state);
            handleStateChange(call, state);
        }

        @Override
        public void onDetailsChanged(Call call, Call.Details details) {
            super.onDetailsChanged(call, details);
            Log.d(TAG, "📞 Call details changed: " + call + ", " + details);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                handleDetailsChange(call, details);
            }
            handleStateChange(call, null);
        }

        @Override
        public void onCallDestroyed(Call call) {
            super.onCallDestroyed(call);
            Log.d(TAG, "📞 Call destroyed: " + call);
            requestStopRecording(call);
        }
    };

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        Log.d(TAG, "📞 NEW CALL ADDED: " + getCallInfo(call));

        call.registerCallback(callCallback);
        handleStateChange(call, null);
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        Log.d(TAG, "📞 CALL REMOVED: " + getCallInfo(call));

        requestStopRecording(call);
    }

    private void handleStateChange(Call call, Integer state) {
        int callState = state != null ? state :
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
                        call.getDetails().getState() : call.getState());

        Log.d(TAG, "📞 Handle state change: " + getCallStateString(callState));

        if (call.getParent() != null) {
            Log.v(TAG, "⚠️ Ignoring state change of conference call child");
            return;
        }

        if (callState == Call.STATE_ACTIVE) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "❌ Cannot start recording due to missing permission");
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startAIRecording(call);
            }
        } else if (callState == Call.STATE_DISCONNECTING || callState == Call.STATE_DISCONNECTED) {
            requestStopRecording(call);
        }

        // Update holding state
        AICallRecorderWrapper wrapper = callsToRecorders.get(call);
        if (wrapper != null) {
            wrapper.isHolding = (callState == Call.STATE_HOLDING);
            updateForegroundState();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void handleDetailsChange(Call call, Call.Details details) {
        Call parentCall = call.getParent();
        AICallRecorderWrapper wrapper = parentCall != null ?
                callsToRecorders.get(parentCall) : callsToRecorders.get(call);

        if (wrapper != null) {
            extractCallDetails(call, wrapper.callInfo);
            updateForegroundState();
        }
    }

    // ===== ENHANCED AI RECORDING WITH INJECTION =====

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private void startAIRecording(Call call) {
        if (!hasRecordAudioPermission()) {
            Log.w(TAG, "❌ Required permissions have not been granted");
            return;
        }

        if (callsToRecorders.containsKey(call)) {
            Log.d(TAG, "⚠️ Already recording this call");
            return;
        }

        String callPackage = call.getDetails().getAccountHandle().getComponentName().getPackageName();
        if (!callPackage.equals(PHONE_PACKAGE)) {
            Log.w(TAG, "⚠️ Ignoring call associated with package: " + callPackage);
            return;
        }

        try {
            Log.d(TAG, "═══════════════════════════════════════════════");
            Log.d(TAG, "🎙️ STARTING AI-ENHANCED RECORDING WITH INJECTION");
            Log.d(TAG, "═══════════════════════════════════════════════");

            CallInfo callInfo = new CallInfo();
            extractCallDetails(call, callInfo);
            callInfo.aiEnabled = isAIEnabled;
            callInfo.aiMode = currentAIMode;
            callInfo.injectionEnabled = isInjectionEnabled;

            // Create AI-enhanced recorder
            AICallRecorderRefactored recorder = new AICallRecorderRefactored(this);
            AICallRecorderWrapper wrapper = new AICallRecorderWrapper(recorder, callInfo, call);

            callsToRecorders.put(call, wrapper);

            // Generate notification ID
            int notificationId = notificationIdsToRecorders.isEmpty() ?
                    foregroundNotificationId : generateNotificationId();
            notificationIdsToRecorders.put(notificationId, wrapper);

            // Configure AI features if enabled and credentials available
            if (callInfo.aiEnabled && elevenLabsApiKey != null && agentId != null) {
                Log.d(TAG, "🤖 CONFIGURING AI FEATURES WITH INJECTION");
                recorder.setElevenLabsConfig(elevenLabsApiKey, agentId);
                recorder.setAIMode(currentAIMode);

                // Configure audio injection

                Log.d(TAG, "✅ AI configured: Mode=" + currentAIMode + ", Agent=" + agentId +
                        ", Injection=" + (callInfo.injectionEnabled ? "ON" : "OFF"));
            } else {
                Log.d(TAG, "⚠️ AI NOT CONFIGURED - Recording without AI features");
                Log.d(TAG, "   - AI Enabled: " + callInfo.aiEnabled);
                Log.d(TAG, "   - API Key: " + (elevenLabsApiKey != null ? "SET" : "NOT SET"));
                Log.d(TAG, "   - Agent ID: " + (agentId != null ? "SET" : "NOT SET"));
            }

            // Setup comprehensive recording callback
            recorder.setCallback(new ComprehensiveAIRecordingCallback(wrapper));

            // Update foreground state
            updateForegroundState();

            // Start AI-enhanced recording
            String filename = generateRecordingFilename(callInfo);
            wrapper.state = AICallRecorderWrapper.State.RECORDING;

            Log.d(TAG, "🚀 STARTING RECORDING: " + filename);
            boolean started = recorder.startRecording(filename);

            if (started) {
                Log.d(TAG, "✅ AI-ENHANCED RECORDING WITH INJECTION STARTED SUCCESSFULLY! 🎉");
                callInfo.isRecorded = true;
                callInfo.recordingFile = filename;

                // Log comprehensive AI status after initialization
                handler.postDelayed(() -> {
                    if (recorder.isRecording()) {
                        Log.d(TAG, "🔍 COMPREHENSIVE AI STATUS CHECK:");
                        recorder.logAIStatus();
                    }
                }, 5000);

            } else {
                Log.e(TAG, "❌ FAILED TO START AI-ENHANCED RECORDING");
                wrapper.state = AICallRecorderWrapper.State.COMPLETED;
            }

        } catch (Exception e) {
            Log.e(TAG, "💥 EXCEPTION STARTING AI RECORDING: " + e.getMessage(), e);
        }
    }

    private void requestStopRecording(Call call) {
        call.unregisterCallback(callCallback);

        AICallRecorderWrapper wrapper = callsToRecorders.get(call);
        if (wrapper != null) {
            stopAIRecording(wrapper);
            callsToRecorders.remove(call);
        }
    }

    private void stopAIRecording(AICallRecorderWrapper wrapper) {
        try {
            Log.d(TAG, "🛑 STOPPING AI RECORDING WITH INJECTION...");
            wrapper.state = AICallRecorderWrapper.State.FINALIZING;
            updateForegroundState();

            wrapper.recorder.stopRecording();
            Log.d(TAG, "✅ AI RECORDING WITH INJECTION STOPPED SUCCESSFULLY");

            wrapper.state = AICallRecorderWrapper.State.COMPLETED;

            // Save to database with injection info
            saveCallToDatabase(wrapper.callInfo);

            // Schedule removal from notifications
            handler.postDelayed(() -> {
                onRecorderExited(wrapper);
            }, 3000);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error stopping AI recording: " + e.getMessage());
            wrapper.state = AICallRecorderWrapper.State.COMPLETED;
            onRecorderExited(wrapper);
        }
    }

    private void onRecorderExited(AICallRecorderWrapper wrapper) {
        notificationIdsToRecorders.values().removeIf(w -> w == wrapper);
        updateForegroundState();
    }

    // ===== COMPREHENSIVE AI RECORDING CALLBACK =====

    private class ComprehensiveAIRecordingCallback implements AICallRecorderRefactored.AIRecordingCallback {
        private final AICallRecorderWrapper wrapper;

        ComprehensiveAIRecordingCallback(AICallRecorderWrapper wrapper) {
            this.wrapper = wrapper;
        }

        @Override
        public void onRecordingStarted(CallRecorder.RecordingMode mode) {
            wrapper.callInfo.recordingMode = mode;
            Log.d(TAG, "✅ RECORDING STARTED: " + mode.getDescription());
            updateForegroundState();
        }

        @Override
        public void onRecordingFailed(String reason) {
            Log.e(TAG, "❌ RECORDING FAILED: " + reason);
            wrapper.callInfo.isRecorded = false;
            wrapper.state = AICallRecorderWrapper.State.COMPLETED;
            updateForegroundState();
        }

        @Override
        public void onRecordingStopped(String filename, long duration) {
            if (wrapper.callInfo != null && filename != null) {
                wrapper.callInfo.recordingFile = new File(filename).getName();
                wrapper.callInfo.isRecorded = true;
            }
            Log.d(TAG, "🛑 RECORDING STOPPED: " + filename + " (" + (duration / 1000) + "s)");
            updateForegroundState();
        }

        @Override
        public void onAIConnected() {
            wrapper.isAIConnected = true;
            Log.d(TAG, "🤖 AI CONNECTED FOR CALL! 🎉");
            updateForegroundState();
        }

        @Override
        public void onAIDisconnected() {
            wrapper.isAIConnected = false;
            wrapper.isAIResponding = false;
            wrapper.isAudioInjectionActive = false;
            Log.d(TAG, "🔌 AI DISCONNECTED FROM CALL");
            updateForegroundState();
        }

        @Override
        public void onAIError(String error) {
            wrapper.isAIConnected = false;
            wrapper.isAIResponding = false;
            wrapper.isAudioInjectionActive = false;
            Log.e(TAG, "⚠️ AI ERROR: " + error);
            updateForegroundState();
        }

        @Override
        public void onAIResponse(String transcript, boolean isPlaying) {
            wrapper.isAIResponding = isPlaying;
            wrapper.lastAIResponse = transcript;
            Log.d(TAG, "🗣️ AI RESPONSE: '" + transcript + "'" + (isPlaying ? " (PLAYING)" : ""));
            updateForegroundState();
        }

        @Override
        public void onAIStreamingStarted(String audioSource) {

        }

        @Override
        public void onAIStreamingStopped() {

        }

        // Audio Injection Callbacks
        @Override
        public void onAudioInjectionStarted(String method) {
            wrapper.isAudioInjectionActive = true;
            wrapper.audioInjectionMethod = method;
            Log.d(TAG, "🎧 AUDIO INJECTION STARTED: " + method + " 🎯");
            updateForegroundState();
        }

        @Override
        public void onAudioInjectionStopped() {
            wrapper.isAudioInjectionActive = false;
            wrapper.audioInjectionMethod = "None";
            Log.d(TAG, "🎧 AUDIO INJECTION STOPPED");
            updateForegroundState();
        }

        @Override
        public void onAudioInjected(int chunkSize, long totalBytes) {
            wrapper.injectedChunks++;
            wrapper.injectedBytes = totalBytes;
            Log.v(TAG, "🎯 AUDIO INJECTED: " + chunkSize + " bytes (Total: " + totalBytes + " bytes, Chunks: " + wrapper.injectedChunks + ")");
            // Update notification periodically
            if (wrapper.injectedChunks % 50 == 0) {
                updateForegroundState();
            }
        }

        @Override
        public void onAudioInjectionError(String error) {
            Log.e(TAG, "💥 AUDIO INJECTION ERROR: " + error);
            wrapper.isAudioInjectionActive = false;
            updateForegroundState();
        }
    }

    // ===== ENHANCED NOTIFICATION SYSTEM =====

    @SuppressLint("ForegroundServiceType")
    private void updateForegroundState() {
        if (notificationIdsToRecorders.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            return;
        }

        // Cancel and remove notifications for recorders that have exited
        for (int notificationId : allNotificationIds.keySet().toArray(new Integer[0])) {
            if (!notificationIdsToRecorders.containsKey(notificationId)) {
                if (notificationId != foregroundNotificationId) {
                    notificationManager.cancel(notificationId);
                }
                allNotificationIds.remove(notificationId);
            }
        }

        // Reassign the foreground notification if needed
        if (!notificationIdsToRecorders.containsKey(foregroundNotificationId)) {
            Map.Entry<Integer, AICallRecorderWrapper> entry = notificationIdsToRecorders.entrySet().iterator().next();
            int oldNotificationId = entry.getKey();
            AICallRecorderWrapper wrapper = entry.getValue();

            notificationIdsToRecorders.remove(oldNotificationId);
            notificationManager.cancel(oldNotificationId);
            allNotificationIds.remove(oldNotificationId);
            notificationIdsToRecorders.put(foregroundNotificationId, wrapper);
        }

        // Create/update notifications with AI and injection status
        for (Map.Entry<Integer, AICallRecorderWrapper> entry : notificationIdsToRecorders.entrySet()) {
            int notificationId = entry.getKey();
            AICallRecorderWrapper wrapper = entry.getValue();

            NotificationState newState = createNotificationState(wrapper);
            NotificationState currentState = allNotificationIds.get(notificationId);

            if (newState.equals(currentState)) {
                continue;
            }

            Notification notification = createNotificationForWrapper(wrapper, notificationId);

            if (notificationId == foregroundNotificationId) {
                startForeground(notificationId, notification);
            } else {
                notificationManager.notify(notificationId, notification);
            }

            allNotificationIds.put(notificationId, newState);
        }
    }

    private NotificationState createNotificationState(AICallRecorderWrapper wrapper) {
        int titleResId;
        boolean canShowDelete;

        switch (wrapper.state) {
            case NOT_STARTED:
                titleResId = R.string.app_name;
                canShowDelete = true;
                break;
            case RECORDING:
                if (wrapper.isHolding) {
                    titleResId = R.string.app_name;
                } else if (wrapper.isPaused) {
                    titleResId = R.string.app_name;
                } else {
                    titleResId = R.string.app_name;
                }
                canShowDelete = true;
                break;
            case FINALIZING:
            case COMPLETED:
            default:
                titleResId = R.string.app_name;
                canShowDelete = false;
                break;
        }

        String message = wrapper.getOutputPath();
        if (wrapper.keepState == AICallRecorderWrapper.KeepState.DISCARD) {
            message += "\n\nMarked for deletion";
        }

        return new NotificationState(titleResId, message, canShowDelete, wrapper.isPaused,
                wrapper.isHolding, wrapper.isAIConnected, wrapper.isAIResponding,
                wrapper.callInfo.aiMode, wrapper.isAudioInjectionActive,
                wrapper.audioInjectionMethod);
    }

    private Notification createNotificationForWrapper(AICallRecorderWrapper wrapper, int notificationId) {
        Intent homeIntent = new Intent(this, HomeActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, homeIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                        PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("TeleTalker AI Call Recording + Injection")
                .setContentText(createNotificationText(wrapper))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE);

        // Add comprehensive AI status to notification
        if (wrapper.callInfo.aiEnabled) {
            builder.setSubText(getComprehensiveAIStatusText(wrapper));
        }

        // Add action buttons with AI and injection controls
        addNotificationActions(builder, wrapper, notificationId);

        return builder.build();
    }

    private String createNotificationText(AICallRecorderWrapper wrapper) {
        StringBuilder text = new StringBuilder();

        if (wrapper.callInfo.contactName != null && !wrapper.callInfo.contactName.equals("Unknown")) {
            text.append(wrapper.callInfo.contactName);
        } else if (wrapper.callInfo.phoneNumber != null) {
            text.append(wrapper.callInfo.phoneNumber);
        } else {
            text.append("Unknown caller");
        }

        switch (wrapper.state) {
            case NOT_STARTED:
                text.append(wrapper.callInfo.aiEnabled ? " - AI Initializing..." : " - Initializing...");
                break;
            case RECORDING:
                if (wrapper.isHolding) {
                    text.append(" - On hold");
                } else if (wrapper.isPaused) {
                    text.append(" - Paused");
                } else {
                    text.append(wrapper.callInfo.aiEnabled ? " - AI Recording..." : " - Recording...");
                }
                break;
            case FINALIZING:
                text.append(" - Finalizing...");
                break;
            case COMPLETED:
                text.append(" - Completed");
                break;
        }

        return text.toString();
    }

    private String getComprehensiveAIStatusText(AICallRecorderWrapper wrapper) {
        if (!wrapper.callInfo.aiEnabled) {
            return "AI Disabled";
        }

        StringBuilder status = new StringBuilder();

        if (wrapper.isAIResponding) {
            status.append("🤖 AI Speaking");
            if (wrapper.isAudioInjectionActive) {
                status.append(" & Injecting (").append(wrapper.audioInjectionMethod).append(")");
            }
        } else if (wrapper.isAIConnected) {
            status.append("🤖 AI Active");
            if (wrapper.isAudioInjectionActive) {
                status.append(" + 🎧 Injecting (").append(wrapper.audioInjectionMethod).append(")");
            }
        } else {
            status.append("🤖 AI Connecting...");
        }

        // Add injection stats if active
        if (wrapper.isAudioInjectionActive && wrapper.injectedChunks > 0) {
            status.append(" - ").append(wrapper.injectedChunks).append(" chunks");
        }

        return status.toString();
    }

    private void addNotificationActions(NotificationCompat.Builder builder, AICallRecorderWrapper wrapper, int notificationId) {
        // Standard recording actions
        if (wrapper.state == AICallRecorderWrapper.State.RECORDING && !wrapper.isHolding) {
            if (wrapper.isPaused) {
                Intent resumeIntent = createActionIntent(notificationId, ACTION_RESUME);
                PendingIntent resumePendingIntent = PendingIntent.getService(
                        this, notificationId * 10 + 1, resumeIntent,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                                PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
                builder.addAction(android.R.drawable.ic_media_play, "Resume", resumePendingIntent);
            } else {
                Intent pauseIntent = createActionIntent(notificationId, ACTION_PAUSE);
                PendingIntent pausePendingIntent = PendingIntent.getService(
                        this, notificationId * 10 + 2, pauseIntent,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                                PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
                builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent);
            }
        }

        // AI toggle action
        if (wrapper.state == AICallRecorderWrapper.State.RECORDING) {
            String aiActionText = wrapper.callInfo.aiEnabled ? "Disable AI" : "Enable AI";
            Intent toggleAIIntent = createActionIntent(notificationId, ACTION_TOGGLE_AI);
            PendingIntent toggleAIPendingIntent = PendingIntent.getService(
                    this, notificationId * 10 + 5, toggleAIIntent,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                            PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(android.R.drawable.ic_menu_manage, aiActionText, toggleAIPendingIntent);

            // Audio injection toggle action
            String injectionActionText = wrapper.callInfo.injectionEnabled ? "Stop Injection" : "Start Injection";
            Intent toggleInjectionIntent = createActionIntent(notificationId, ACTION_TOGGLE_INJECTION);
            PendingIntent toggleInjectionPendingIntent = PendingIntent.getService(
                    this, notificationId * 10 + 6, toggleInjectionIntent,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                            PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(android.R.drawable.ic_menu_send, injectionActionText, toggleInjectionPendingIntent);
        }

        // Delete/restore actions
        if (wrapper.state == AICallRecorderWrapper.State.RECORDING || wrapper.state == AICallRecorderWrapper.State.NOT_STARTED) {
            if (wrapper.keepState == AICallRecorderWrapper.KeepState.KEEP) {
                Intent deleteIntent = createActionIntent(notificationId, ACTION_DELETE);
                PendingIntent deletePendingIntent = PendingIntent.getService(
                        this, notificationId * 10 + 3, deleteIntent,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                                PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
                builder.addAction(android.R.drawable.ic_menu_delete, "Delete", deletePendingIntent);
            } else {
                Intent restoreIntent = createActionIntent(notificationId, ACTION_RESTORE);
                PendingIntent restorePendingIntent = PendingIntent.getService(
                        this, notificationId * 10 + 4, restoreIntent,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                                PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
                builder.addAction(android.R.drawable.ic_menu_revert, "Restore", restorePendingIntent);
            }
        }
    }

    private Intent createActionIntent(int notificationId, String action) {
        Intent intent = new Intent(this, CallDetector.class);
        intent.setAction(action);
        intent.setData(Uri.fromParts("notification", String.valueOf(notificationId), null));
        intent.putExtra(EXTRA_TOKEN, token);
        intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
        return intent;
    }

    // ===== UTILITY METHODS =====

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void extractCallDetails(Call call, CallInfo callInfo) {
        if (call.getDetails().getHandle() != null) {
            callInfo.phoneNumber = call.getDetails().getHandle().getSchemeSpecificPart();
        }

        if (call.getDetails().getGatewayInfo() != null) {
            callInfo.phoneNumber = call.getDetails().getGatewayInfo()
                    .getOriginalAddress().getSchemeSpecificPart();
        }

        callInfo.contactName = getContactName(callInfo.phoneNumber);

        int callDirection = call.getDetails().getCallDirection();
        if (callDirection == Call.Details.DIRECTION_INCOMING) {
            callInfo.direction = "in";
        } else if (callDirection == Call.Details.DIRECTION_OUTGOING) {
            callInfo.direction = "out";
        }

        if (call.getDetails().hasProperty(Call.Details.PROPERTY_CONFERENCE)) {
            callInfo.direction = "conference";
        }
    }

    private String generateRecordingFilename(CallInfo callInfo) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss.SSS", Locale.getDefault());
        String timestamp = dateFormat.format(new Date(callInfo.callStartTime));

        StringBuilder filename = new StringBuilder(timestamp);

        // Add AI prefix if enabled
        if (callInfo.aiEnabled) {
            filename.append("_AI");
            if (callInfo.injectionEnabled) {
                filename.append("_INJECT");
            }
        }

        if (callInfo.direction != null) {
            filename.append("_").append(callInfo.direction);
        }

        if (callInfo.phoneNumber != null && !callInfo.phoneNumber.isEmpty()) {
            String cleanNumber = callInfo.phoneNumber.replaceAll("[^\\d+]", "");
            filename.append("_").append(cleanNumber);
        }

        if (callInfo.contactName != null && !callInfo.contactName.equals("Unknown")) {
            String cleanName = callInfo.contactName.replaceAll("[^\\w\\s]", "").replaceAll("\\s+", "_");
            if (!cleanName.isEmpty()) {
                filename.append("_").append(cleanName);
            }
        }

        filename.append(".wav");
        return filename.toString();
    }

    @SuppressLint("Range")
    private String getContactName(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return "Unknown";
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            return "Unknown";
        }

        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(phoneNumber));

            try (Cursor cursor = getContentResolver().query(uri,
                    new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {

                if (cursor != null && cursor.moveToFirst()) {
                    String contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
                    return contactName != null ? contactName : "Unknown";
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting contact name: " + e.getMessage());
        }

        return "Unknown";
    }

    private void saveCallToDatabase(CallInfo callInfo) {
        if (callInfo == null) return;

        String callDuration = "";
        if (callInfo.callAnswerTime > 0) {
            long duration = System.currentTimeMillis() - callInfo.callAnswerTime;
            callDuration = formatDuration(duration);
        }

        String callType = "Call";
        if (callInfo.aiEnabled) {
            callType = "AI Call";
            if (callInfo.injectionEnabled) {
                callType += " + Injection";
            }
        }

        CallEntity callEntity = new CallEntity(
                callInfo.phoneNumber != null ? callInfo.phoneNumber : "",
                callInfo.contactName != null ? callInfo.contactName : "Unknown",
                callType,
                callInfo.direction != null ? callInfo.direction : "Unknown",
                callDuration,
                convertTimestampToReadableTime(callInfo.callStartTime),
                callInfo.recordingFile,
                callInfo.isRecorded
        );

        Log.d(TAG, "💾 Saving AI call with injection to database: " + callEntity.toString());

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                CallDatabase.getInstance(getApplicationContext()).callDao().insertCall(callEntity);
                Log.d(TAG, "✅ AI call with injection saved to database successfully");
            } catch (Exception e) {
                Log.e(TAG, "❌ Failed to save AI call to database: " + e.getMessage());
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "TeleTalker AI Call Recording + Audio Injection",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("AI-enhanced call recording service with real-time audio injection into calls");
            channel.setShowBadge(false);
            channel.setSound(null, null);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "TeleTalker:AICallRecordingWithInjection");
        wakeLock.acquire(10*60*1000L);
    }

    private boolean hasRecordAudioPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private String getCallInfo(Call call) {
        if (call == null || call.getDetails() == null) return "Unknown";

        String number = "Unknown";
        if (call.getDetails().getHandle() != null) {
            number = call.getDetails().getHandle().getSchemeSpecificPart();
        }

        return number + " (" + getCallStateString(call.getState()) + ")";
    }

    private String getCallStateString(int state) {
        switch (state) {
            case Call.STATE_NEW: return "NEW";
            case Call.STATE_RINGING: return "RINGING";
            case Call.STATE_DIALING: return "DIALING";
            case Call.STATE_ACTIVE: return "ACTIVE";
            case Call.STATE_HOLDING: return "HOLDING";
            case Call.STATE_DISCONNECTED: return "DISCONNECTED";
            default: return "UNKNOWN(" + state + ")";
        }
    }

    @SuppressLint("DefaultLocale")
    private String formatDuration(long millis) {
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / (1000 * 60)) % 60;
        long hours = (millis / (1000 * 60 * 60)) % 24;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    private String convertTimestampToReadableTime(long timestamp) {
        Date date = new Date(timestamp);
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM hh:mm a", Locale.getDefault());
        return sdf.format(date);
    }

    private int generateNotificationId() {
        return (int) (System.currentTimeMillis() & 0xFFFFFF);
    }

    // Public methods for external AI configuration
    public boolean isAIEnabled() { return isAIEnabled; }
    public AICallRecorderRefactored .AIMode getCurrentAIMode() { return currentAIMode; }
    public boolean hasAICredentials() { return elevenLabsApiKey != null && agentId != null; }
    public boolean isInjectionEnabled() { return isInjectionEnabled; }

    public void testAIConfiguration() {
        Log.d(TAG, "🧪 TESTING AI CONFIGURATION WITH INJECTION");
        if (elevenLabsApiKey != null && agentId != null) {
            AICallRecorderRefactored testRecorder = new AICallRecorderRefactored(this);
            testRecorder.setElevenLabsConfig(elevenLabsApiKey, agentId);
            testRecorder.setAIMode(currentAIMode);
            Log.d(TAG, "✅ AI configuration with injection test completed");
        } else {
            Log.w(TAG, "⚠️ AI configuration incomplete - missing credentials");
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "🛑 TeleTalker AI InCall Service with Injection destroyed");

        // Stop all active AI recordings with injection
        for (AICallRecorderWrapper wrapper : callsToRecorders.values()) {
            if (wrapper.recorder.isRecording()) {
                wrapper.recorder.stopRecording();
            }
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "🔗 TeleTalker AI InCall Service with Injection bound");
        return super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "🔗 TeleTalker AI InCall Service with Injection unbound");
        return super.onUnbind(intent);
    }
}