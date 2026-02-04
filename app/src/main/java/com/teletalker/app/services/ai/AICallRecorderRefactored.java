package com.teletalker.app.services.ai;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.teletalker.app.billing.BillingManager;
import com.teletalker.app.services.CallAudioInjector;
import com.teletalker.app.services.StatusBroadcastManager;
import com.teletalker.app.utils.CallContextManager;
import com.teletalker.app.utils.NetworkUtils;
import com.teletalker.app.utils.PreferencesManager;
import com.teletalker.app.utils.ScheduledCallHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * FIXED: AI Call Recorder with proper initialization sequencing and first-call fixes
 * <p>
 * Key Improvements:
 * - Sequential component initialization to prevent race conditions
 * - Proper state reset between calls to fix first-call failures
 * - Single WebSocket connection management to prevent duplicates
 * - Enhanced error handling and diagnostics
 * - Better thread management and resource cleanup
 */
public class AICallRecorderRefactored {
    private static final String TAG = "AICallRecorder";

    // ElevenLabs Configuration
    private static final String ELEVENLABS_WS_URL = "wss://api.elevenlabs.io/v1/convai/conversation";

    // Connection Management - RELAXED timeouts for call scenarios
    private static final int MAX_CONNECTION_ATTEMPTS = 10;
    private static final long INITIAL_RECONNECT_DELAY = 200L; // 2 seconds
    private static final long MAX_RECONNECT_DELAY = 10000L; // 30 seconds
    private static final long CONNECTION_TIMEOUT = 60000L; // 30 seconds
    private static final long PING_INTERVAL = 30000L; // 20 seconds


    private String conversationPurpose = null;
    private String conversationNotes = null;
    private boolean isOutboundCall = false;


    // Enhanced injection options
    private SequentialAudioInjector sequentialInjector;
    private AudioStreamInjector audioStreamInjector;
    private boolean useRealTimeInjection = true;
    private boolean aiWasUsedInThisCall = false;

    // AI Response modes
    public enum AIMode {
        LISTEN_ONLY("AI listens but doesn't respond"),
        RESPOND_TO_USER("AI responds only to user"),
        RESPOND_TO_CALLER("AI responds only to caller"),
        RESPOND_TO_BOTH("AI responds to both parties"),
        SMART_ASSISTANT("AI acts as smart assistant");

        private final String description;

        AIMode(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // Enhanced callback interface
    public static interface AIRecordingCallback extends CallRecorder.RecordingCallback {
        // AI Connection callbacks
        void onAIConnected();

        void onAIDisconnected();

        void onAIError(String error);

        void onAIResponse(String transcript, boolean isPlaying);

        void onAIStreamingStarted(String audioSource);

        void onAIStreamingStopped();

        // Audio Injection callbacks
        void onAudioInjectionStarted(String method);

        void onAudioInjectionStopped();

        void onAudioInjected(int chunkSize, long totalBytes);

        void onAudioInjectionError(String error);

        // Enhanced callbacks
        void onConnectionHealthChanged(boolean healthy);

        void onAudioQualityChanged(String quality, String reason);

        void onSilenceDetected(long durationMs);

        void onSpeechDetected(long durationMs);
    }


    private void broadcastAIStatus(String status, boolean isRecording, boolean isInjecting, String response) {
        StatusBroadcastManager.broadcastAIStatus(context, status, isRecording, isInjecting, response);
    }


    // Core components
    private final Context context;
    private final Handler mainHandler;

    // FIXED: Better executor management
    private final ExecutorService sharedExecutor;
    private final ExecutorService connectionExecutor;

    // Recording components
    private CallRecorder coreRecorder;
    private EnhancedAudioProcessor audioProcessor;
    private OptimizedAudioStreamer audioStreamer;
    private AIResponseBuffer responseBuffer;
    private AudioResponseAccumulator audioAccumulator;
    private CallAudioInjector audioInjector;


    private BillingManager billing;
    private String currentPhoneNumber; // Track phone number for billing

    private String currentCallPhoneNumber;
    private long callStartTimestamp;
    private String recordingFilePath;

    // WebSocket connection management
    private WebSocket elevenLabsSocket;
    private OkHttpClient httpClient;
    private String elevenLabsApiKey;
    private String agentId;
    private AIMode currentAIMode = AIMode.SMART_ASSISTANT;

    // Callback
    private AIRecordingCallback callback;

    // FIXED: Initialization state management
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicBoolean isInitializing = new AtomicBoolean(false);

    // Connection state management
    private final AtomicBoolean isAIEnabled = new AtomicBoolean(false);
    private final AtomicBoolean isAIConnected = new AtomicBoolean(false);
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(false);
    private final AtomicBoolean hasActiveConnection = new AtomicBoolean(false);

    // Connection health monitoring - RELAXED for calls
    private final AtomicInteger connectionAttempts = new AtomicInteger(0);
    private final AtomicLong lastSuccessfulSend = new AtomicLong(0);
    private final AtomicLong lastPongReceived = new AtomicLong(0);
    private Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private Handler healthCheckHandler = new Handler(Looper.getMainLooper());

    // Statistics tracking
    private final AtomicLong totalMessagesReceived = new AtomicLong(0);
    private final AtomicLong totalAudioChunksReceived = new AtomicLong(0);
    private final AtomicLong totalErrorsEncountered = new AtomicLong(0);
    private CallContextManager callContextManager;

    public AICallRecorderRefactored(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.callContextManager = new CallContextManager(context);

        this.billing = BillingManager.getInstance(context);

        // Initialize executors first
        this.sharedExecutor = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r, "AIRecorder-Shared");
            t.setDaemon(true);
            return t;
        });

        this.connectionExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AIRecorder-Connection");
            t.setDaemon(true);
            return t;
        });
        // FIXED: Initialize components sequentially
        initializeComponentsSequentially();
    }
//    ScheduledCallHelper.CallInfo callInfo = null;

    private void checkAndApplyScheduledCallContext(String phoneNumber) {
        Log.d(TAG, "Checking for scheduled call context...");

        // ADD: Check preferences first (immediate, no async)
        PreferencesManager manager = PreferencesManager.getInstance(context);
        String pendingPhone = manager.getPendingScheduledPhone();
        boolean isOutbound = manager.isPendingScheduledOutbound();

        if (pendingPhone != null && pendingPhone.equals(phoneNumber)) {
            Log.d(TAG, "🎯 SCHEDULED CALL DETECTED (from preferences): " + phoneNumber);

            String notes = manager.getPendingScheduledNotes();
            long callId = manager.getPendingScheduledCallId();

            if (notes != null && !notes.isEmpty()) {
                Log.d(TAG, "📝 Loading AI conversation notes: " + notes.substring(0, Math.min(50, notes.length())));

                // Set context IMMEDIATELY - no async delay
                setCallContext(
                        "Scheduled outbound call",  // purpose
                        notes,                       // notes
                        true                        // isOutbound - ALWAYS true for scheduled calls
                );

                currentScheduledCallId = callId;
                Log.d(TAG, "✅ Call context set IMMEDIATELY for scheduled call ID: " + callId);
                return; // Exit early, we found it
            }
        }

        // FALLBACK: If not in preferences, try database (but this is async, so less reliable)
        if (phoneNumber != null) {
            ScheduledCallHelper.findActiveCallByPhoneNumber(context, phoneNumber, new ScheduledCallHelper.CallInfoCallback() {
                @Override
                public void onCallInfoFound(ScheduledCallHelper.CallInfo callInfo) {
                    if (callInfo != null && callInfo.isValid()) {
                        Log.d(TAG, "Found scheduled call from database: " + callInfo);
                        setCallContext(callInfo.purpose, callInfo.notes, true);
                        ScheduledCallHelper.markCallInProgress(context, callInfo.callId);
                        currentScheduledCallId = callInfo.callId;
                    }
                }

                @Override
                public void onCallInfoNotFound() {
                    Log.d(TAG, "No scheduled call context found - treating as regular call");
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Error finding scheduled call: " + e.getMessage());
                }
            });
        }
    }

    private static long currentScheduledCallId = -1;


    public void setCallContext(String purpose, String notes, boolean isOutbound) {
        this.conversationPurpose = purpose;
        this.conversationNotes = notes;
        this.isOutboundCall = isOutbound;

        Log.d(TAG, "Call context set - Purpose: " + purpose +
                ", Outbound: " + isOutbound +
                ", Notes length: " + (notes != null ? notes.length() : 0));
    }

    // Add this method to build conversation initiation data
// ============================================
// REPLACE YOUR buildConversationInitiationData() METHOD WITH THIS
// ============================================

    private JSONObject buildConversationInitiationData(boolean isOutbound) {
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("agent_id", agentId);

            // Get scheduled call context
            PreferencesManager prefs = PreferencesManager.getInstance(context);
            String purpose = prefs.getString("pending_scheduled_call_purpose", "");
            String notes = prefs.getString("pending_scheduled_call_notes", "");
            String contactName = prefs.getString("pending_scheduled_call_contact", "");

            // Build prompts based on call direction
            // Build prompts based on call direction
            String systemPrompt = buildSystemPrompt(isOutbound, purpose, notes, contactName);
            String firstMessage = buildFirstMessage(isOutbound, purpose, contactName);

            // Create overrides object
            JSONObject overrides = new JSONObject();

            // Agent override with system prompt
            JSONObject agent = new JSONObject();
            JSONObject promptConfig = new JSONObject();
            promptConfig.put("prompt", systemPrompt);
            agent.put("prompt", promptConfig);
            overrides.put("agent", agent);

            // Conversation config override with first message
            JSONObject conversationConfig = new JSONObject();
            conversationConfig.put("first_message", firstMessage);
            overrides.put("conversation_config", conversationConfig);

            requestBody.put("overrides", overrides);

            Log.d(TAG, "========================================");
            Log.d(TAG, "CONVERSATION INITIATION DATA");
            Log.d(TAG, "Call Type: " + (isOutbound ? "OUTBOUND" : "INBOUND"));
            Log.d(TAG, "System Prompt: " + systemPrompt);
            Log.d(TAG, "First Message: " + firstMessage);
            Log.d(TAG, "========================================");

            return requestBody;

        } catch (JSONException e) {
            Log.e(TAG, "Error building conversation data", e);
            return null;
        }
    }

    private String buildSystemPrompt(boolean isOutbound, String purpose, String notes, String contactName) {
        if (isOutbound) {
            // OUTBOUND - personalized with variables
            StringBuilder prompt = new StringBuilder();

            prompt.append("You are an AI phone assistant for TeleTalker. ");
            prompt.append("This is an outbound call that you initiated. ");

            if (purpose != null && !purpose.isEmpty()) {
                prompt.append("Call Purpose: ").append(purpose).append(". ");
            }

            if (notes != null && !notes.isEmpty()) {
                prompt.append("Your Instructions: ").append(notes).append(". ");
            }

            if (contactName != null && !contactName.isEmpty()) {
                prompt.append("You are speaking with: ").append(contactName).append(". ");
            }

            prompt.append("Speak naturally and complete the conversation objective. ");
            prompt.append("Be concise and friendly.");

            return prompt.toString();

        } else {
            // INBOUND - static text only
            return "You are an AI phone assistant for TeleTalker. " +
                    "This is an inbound call. The user called you. " +
                    "Listen carefully and assist them with their needs. " +
                    "Be helpful, professional, and concise.";
        }
    }

    private String buildFirstMessage(boolean isOutbound, String purpose, String contactName) {
        if (isOutbound) {
            // OUTBOUND - personalized greeting
            StringBuilder message = new StringBuilder("Hello");

            if (contactName != null && !contactName.isEmpty()) {
                message.append(" ").append(contactName);
            }

            if (purpose != null && !purpose.isEmpty()) {
                message.append(", I'm calling regarding ").append(purpose);
            }

            message.append(".");

            return message.toString();

        } else {
            // INBOUND - static greeting
            return "Hello! How can I help you today?";
        }
    }

    private void initializeComponentsSequentially() {
        if (isInitializing.get()) {
            Log.w(TAG, "Already initializing components");
            return;
        }

        isInitializing.set(true);
        Log.d(TAG, "Starting sequential component initialization...");

        try {
            // Step 1: Core recorder (must be first)
            this.coreRecorder = new CallRecorder(context);
            Log.d(TAG, "Core recorder initialized");

            // Step 2: Audio processing components
            this.audioProcessor = new EnhancedAudioProcessor();
            this.audioStreamer = new OptimizedAudioStreamer(context);
            Log.d(TAG, "Audio components initialized");

            // Step 3: Response handling
            this.responseBuffer = new AIResponseBuffer();
            this.audioAccumulator = new AudioResponseAccumulator(sharedExecutor);
            Log.d(TAG, "Response components initialized");

            // Step 4: Audio injection (initialize but don't start)
            this.audioInjector = new CallAudioInjector(context);
            this.sequentialInjector = new SequentialAudioInjector(audioInjector);
            this.audioStreamInjector = new AudioStreamInjector();
            Log.d(TAG, "Injection components initialized");

            // Step 5: Setup callbacks (after all components exist)
            setupEnhancedCallbacks();
            Log.d(TAG, "Callbacks configured");

            // Step 6: Initialize AI components (HTTP client, etc.)
            initializeEnhancedAIComponents();
            Log.d(TAG, "AI components initialized");

            // Step 7: Start health monitoring
            startConnectionHealthMonitoring();
            Log.d(TAG, "Health monitoring started");

            isInitialized.set(true);
            Log.d(TAG, "All components initialized successfully");

        } catch (Exception e) {
            Log.e(TAG, "Component initialization failed: " + e.getMessage(), e);
            isInitialized.set(false);
        } finally {
            isInitializing.set(false);
        }
    }

    private boolean waitForInitialization() {
        int maxWaitMs = 10000; // 10 seconds max wait
        int waitedMs = 0;

        while (!isInitialized.get() && waitedMs < maxWaitMs) {
            if (isInitializing.get()) {
                Log.d(TAG, "Waiting for initialization to complete...");
            } else {
                Log.w(TAG, "Initialization not started, attempting to initialize");
                initializeComponentsSequentially();
            }

            try {
                Thread.sleep(200);
                waitedMs += 200;
            } catch (InterruptedException e) {
                Log.w(TAG, "Initialization wait interrupted");
                return false;
            }
        }

        boolean ready = isInitialized.get();
        Log.d(TAG, ready ? "Initialization complete" : "Initialization timeout");
        return ready;
    }

    public void setCallback(AIRecordingCallback callback) {
        this.callback = callback;
        if (this.coreRecorder != null) {
            this.coreRecorder.setCallback(callback);
        }
    }

    public void setElevenLabsConfig(String apiKey, String agentId) {
        this.elevenLabsApiKey = apiKey;
        this.agentId = agentId;
        this.isAIEnabled.set(apiKey != null && agentId != null);

        Log.d(TAG, "ElevenLabs config updated - AI Enabled: " + isAIEnabled.get() +
                ", API Key: " + (apiKey != null ? "***SET***" : "null") +
                ", Agent ID: " + agentId);
    }

    public void setAIMode(AIMode mode) {
        this.currentAIMode = mode;
        Log.d(TAG, "AI Mode set to: " + mode.getDescription());
    }


    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public boolean startRecording(String filename) {
        return startRecording(filename, null);
    }


    /**
     * FIXED: Start recording with proper initialization and state reset
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public boolean startRecording(String filename, String phoneNumber) {
        Log.d(TAG, "Starting Enhanced AI Call Recording: " + filename);
        recordingStartTime = System.currentTimeMillis();





        Log.d(TAG, "========================================");
        Log.d(TAG, "START RECORDING REQUEST");
        Log.d(TAG, "Phone: " + phoneNumber);
        Log.d(TAG, "========================================");

        // ===== STEP 1: CHECK INTERNET =====
        if (!NetworkUtils.isInternetAvailable(context)) {
            Log.e(TAG, "❌ NO INTERNET - Cannot start AI call");
            Toast.makeText(context,
                    "No internet connection - AI features unavailable",
                    Toast.LENGTH_SHORT).show();
            return false; // STOP HERE
        }
        Log.d(TAG, "✓ Internet available");

        // ===== STEP 2: CHECK BALANCE =====
        double balance = billing.getCachedRemainingMinutes();
        Log.d(TAG, "Current balance: " + balance + " minutes");

        if (!billing.hasSufficientBalance()) {
            Log.w(TAG, "⚠️ LOW BALANCE WARNING");
            Log.w(TAG, "   Balance: " + balance + " minutes");

            Toast.makeText(context,
                    String.format("Low balance: %.1f min remaining", balance),
                    Toast.LENGTH_SHORT).show();

            // Continue anyway but user is warned
        } else {
            Log.d(TAG, "✓ Sufficient balance: " + balance + " minutes");
        }

        // ===== STEP 3: STORE CALL INFO FOR BILLING =====
        this.currentCallPhoneNumber = phoneNumber;
        this.callStartTimestamp = System.currentTimeMillis();

        Log.d(TAG, "Call start time recorded: " + callStartTimestamp);
        Log.d(TAG, "Phone stored for billing: " + currentCallPhoneNumber);
        checkAndApplyScheduledCallContext(phoneNumber);

        boolean shouldEnableAI = shouldEnableAIForThisCall(phoneNumber);


        // FIXED: Diagnostic logging for first call issues
        diagnoseFirstCallIssue();

        // FIXED: Wait for initialization to complete
        if (!waitForInitialization()) {
            Log.e(TAG, "Cannot start recording - initialization failed");
            return false;
        }

        // FIXED: Reset any previous state
        resetCallState();

        // === CORE RECORDING (ALWAYS WORKS) ===
        boolean coreRecordingStarted = coreRecorder.startRecording(filename);

        if (coreRecordingStarted) {
            Log.d(TAG, "Core recording started successfully");
            broadcastAIStatus("CONNECTING", true, false, "");

            // === ENHANCED AI FEATURES ===
            if (isAIEnabled.get() && shouldEnableAI) {
                // FIXED: Start AI features with delay to avoid race conditions
                mainHandler.postDelayed(() -> {
                    if (coreRecorder.isRecording()) {
                        startEnhancedAIFeatures();
                    } else {
                        Log.w(TAG, "Core recording stopped before AI features could start");
                    }
                }, 500); // 2 second delay for stability
            } else {
                Log.d(TAG, "AI features disabled - recording without AI enhancement");
            }

            return true;
        } else {
            Log.e(TAG, "Failed to start core recording");
            return false;
        }
    }


    private boolean shouldEnableAIForThisCall(String phoneNumber) {
        PreferencesManager prefs = PreferencesManager.getInstance(context);

        // Check if bot is enabled at all
        if (!prefs.isBotActive()) {
            Log.d(TAG, "Bot is disabled in settings");
            return false;
        }

        // Get user's plan
        String userPlan = prefs.getString("user_plan", "lite");  // Default to lite
        boolean isLitePlan = "lite".equalsIgnoreCase(userPlan);

        // Determine if this is an outbound call
        // isOutboundCall should already be set by checkAndApplyScheduledCallContext()
        // or you can check via CallContextManager
        boolean isOutgoing = isOutboundCall || isScheduledOutboundCall(phoneNumber);

        if (isLitePlan) {
            // Lite plan: AI only for OUTGOING calls
            if (!isOutgoing) {
                Log.d(TAG, "✅ Lite plan +Incoming   call = AI ENABLED");
                return true;
            } else {
                Log.d(TAG, "❌ Lite plan + Outgoing call = AI DISABLED");
                return false;
            }
        } else {
            // Standard plan: AI for ALL calls
            Log.d(TAG, "✅ Standard plan = AI ENABLED for all calls");
            return true;
        }
    }

    // Helper to check if this is a scheduled outbound call
    private boolean isScheduledOutboundCall(String phoneNumber) {
        PreferencesManager prefs = PreferencesManager.getInstance(context);
        String pendingPhone = prefs.getPendingScheduledPhone();
        boolean isOutbound = prefs.isPendingScheduledOutbound();

        return pendingPhone != null && pendingPhone.equals(phoneNumber) && isOutbound;
    }


    private void resetCallState() {
        Log.d(TAG, "Resetting call state for fresh start...");

        // Reset connection state
        isAIConnected.set(false);
        isConnecting.set(false);
        shouldReconnect.set(false);
        hasActiveConnection.set(false);
        connectionAttempts.set(0);

        // Reset statistics
        totalMessagesReceived.set(0);
        totalAudioChunksReceived.set(0);
        totalErrorsEncountered.set(0);
        lastSuccessfulSend.set(0);
        lastPongReceived.set(0);

        // Close any existing WebSocket
        if (elevenLabsSocket != null) {
            try {
                elevenLabsSocket.close(1000, "Resetting for new call");
                Thread.sleep(100); // Brief pause
            } catch (Exception e) {
                Log.w(TAG, "Error closing existing WebSocket: " + e.getMessage());
            }
            elevenLabsSocket = null;
        }

        // Reset audio components
        if (audioStreamer != null) {
            audioStreamer.stopStreaming();
        }

        if (responseBuffer != null) {
            responseBuffer.clearBuffer();
            responseBuffer.resetStatistics();
        }

        if (audioAccumulator != null) {
            audioAccumulator.reset();
        }

        // Clear all handlers
        if (reconnectHandler != null) {
            reconnectHandler.removeCallbacksAndMessages(null);
        }
        if (healthCheckHandler != null) {
            healthCheckHandler.removeCallbacksAndMessages(null);
        }

        Log.d(TAG, "Call state reset complete");
    }

    public void diagnoseFirstCallIssue() {
        Log.d(TAG, "=== FIRST CALL DIAGNOSTIC ===");
        Log.d(TAG, "Initialized: " + isInitialized.get());
        Log.d(TAG, "Initializing: " + isInitializing.get());
        Log.d(TAG, "AI Enabled: " + isAIEnabled.get());
        Log.d(TAG, "Credentials set: " + (elevenLabsApiKey != null && agentId != null));
        Log.d(TAG, "Core Recording: " + (coreRecorder != null ? coreRecorder.isRecording() : "null"));
        Log.d(TAG, "WebSocket: " + (elevenLabsSocket != null ? "exists" : "null"));
        Log.d(TAG, "Audio Streamer: " + (audioStreamer != null ? "exists" : "null"));
        Log.d(TAG, "Response Buffer: " + (responseBuffer != null ? "exists" : "null"));

        // Check permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int permission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO);
            Log.d(TAG, "RECORD_AUDIO permission: " + (permission == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
        }

        // Check component readiness
        if (audioStreamer != null) {
            OptimizedAudioStreamer.StreamingStats stats = audioStreamer.getStats();
            Log.d(TAG, "Audio Streaming: " + stats.isStreaming);
            Log.d(TAG, "Audio Connection: " + stats.connectionHealthy);
        }
    }

    /**
     * FIXED: Stop recording with proper cleanup
     */
//    public void stopRecording() {
//        Log.d(TAG, "Stopping Enhanced AI Call Recording...");
//        broadcastAIStatus("HIDDEN", false, false, "");
//
//        // Calculate call duration
//        long callDurationMs = 0;
//        if (coreRecorder != null && coreRecorder.isRecording()) {
//            callDurationMs = System.currentTimeMillis() - recordingStartTime;
//        }
//
//        // Mark scheduled call as completed if applicable
//        if (currentScheduledCallId > 0) {
//            ScheduledCallHelper.markCallCompleted(context, currentScheduledCallId);
//            currentScheduledCallId = -1;
//        }
//
//        // Stop core recording
//        if (coreRecorder != null) {
//            coreRecorder.stopRecording();
//            Log.d(TAG, "coreRecorder.stopRecording");
//        }
//
//        // Stop AI features
//        if (isAIEnabled.get()) {
//            stopEnhancedAIFeatures();
//        }
//
//        // DEDUCT MINUTES AFTER CALL ENDS
//        if (callDurationMs > 0) {
//            long callDurationSeconds = callDurationMs / 1000;
//
//            Log.d(TAG, "📊 Call ended - Duration: " + callDurationSeconds + "s (" +
//                    (callDurationSeconds / 60.0) + " minutes)");
//
//            // Get recording file path for Firebase
//            String recordingUrl = coreRecorder != null ? coreRecorder.getCurrentRecordingFile() : null;
//
//            // Deduct minutes
//            deductMinutesForCall(callDurationSeconds, currentPhoneNumber, recordingUrl);
//        } else {
//            Log.w(TAG, "⚠️ Call duration is 0, skipping minute deduction");
//        }
//
//        // Calculate duration
//        long callEndTimestamp = System.currentTimeMillis();
//        long durationMillis = callEndTimestamp - callStartTimestamp;
//        long durationSeconds = durationMillis / 1000;
//
//        Log.d(TAG, "📊 BILLING CALCULATION:");
//        Log.d(TAG, "   Call start: " + callStartTimestamp);
//        Log.d(TAG, "   Call end:   " + callEndTimestamp);
//        Log.d(TAG, "   Duration:   " + durationSeconds + " seconds (" +
//                (durationSeconds / 60.0) + " minutes)");
//
//        // Only bill if call was > 5 seconds
//        if (durationSeconds <= 5) {
//            Log.d(TAG, "⏩ Call too short (" + durationSeconds + "s) - no billing");
//            resetCallTracking();
//            return;
//        }
//
//        // Trigger billing
//        Log.d(TAG, "💰 TRIGGERING BILLING DEDUCTION...");
//        deductMinutesForCall(durationSeconds, currentCallPhoneNumber, recordingFilePath);
//
//        // Reset for next call
//        resetCallTracking();
//
//
//        Log.d(TAG, "Enhanced AI Call Recording stopped");
//    }
    public void stopRecording() {
        Log.d(TAG, "Stopping Enhanced AI Call Recording...");
        broadcastAIStatus("HIDDEN", false, false, "");

        // Calculate call duration
        long callDurationMs = 0;
        if (coreRecorder != null && coreRecorder.isRecording()) {
            callDurationMs = System.currentTimeMillis() - recordingStartTime;
        }

        // Mark scheduled call as completed if applicable
        if (currentScheduledCallId > 0) {
            ScheduledCallHelper.markCallCompleted(context, currentScheduledCallId);
            currentScheduledCallId = -1;
        }

        // Stop core recording
        if (coreRecorder != null) {
            coreRecorder.stopRecording();
        }

        // Stop AI features
        if (isAIEnabled.get()) {
            stopEnhancedAIFeatures();
        }

        // ✅ FIX: Only deduct minutes if AI was ACTUALLY USED
        long durationSeconds = callDurationMs / 1000;

        if (durationSeconds > 5 && aiWasUsedInThisCall) {
            Log.d(TAG, "💰 AI was used - deducting minutes");
            deductMinutesForCall(durationSeconds, currentCallPhoneNumber, recordingFilePath);
        } else {
            if (!aiWasUsedInThisCall) {
                Log.d(TAG, "⏩ AI was NOT used - skipping billing");
            } else {
                Log.d(TAG, "⏩ Call too short - skipping billing");
            }
        }

        // Reset for next call
        aiWasUsedInThisCall = false;
        resetCallTracking();

        Log.d(TAG, "Enhanced AI Call Recording stopped");
    }

    private void deductMinutesForCall(long durationSeconds, String phoneNumber, String recordingUrl) {

        Log.d(TAG, "========== SERVICE AUTH DEBUG ==========");

        // Check FirebaseApp
        FirebaseApp app = FirebaseApp.getInstance();
        Log.d(TAG, "FirebaseApp name: " + app.getName());
        Log.d(TAG, "FirebaseApp options project: " + app.getOptions().getProjectId());

        // Check Auth instance
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();

        Log.d(TAG, "Auth instance: " + auth);
        Log.d(TAG, "Current user: " + (user != null ? user.getUid() : "NULL!!!"));

        if (user == null) {
            Log.e(TAG, "❌ NO USER IN SERVICE CONTEXT!");
            // This is your problem
            return;
        }

        Log.d(TAG, "User UID: " + user.getUid());
        Log.d(TAG, "==========================================");

        Log.d(TAG, "========================================");
        Log.d(TAG, "DEDUCT MINUTES FOR CALL");
        Log.d(TAG, "Duration: " + durationSeconds + " seconds");
        Log.d(TAG, "Phone: " + phoneNumber);
        Log.d(TAG, "Recording: " + (recordingUrl != null ? recordingUrl : "none"));
        Log.d(TAG, "========================================");

        // Show immediate feedback
        Toast.makeText(context,
                "Processing billing for " + (durationSeconds / 60.0) + " min call...",
                Toast.LENGTH_SHORT).show();

        // Call BillingManager
        billing.deductMinutesForCall(durationSeconds, phoneNumber, recordingUrl,
                new BillingManager.DeductCallback() {
                    @Override
                    public void onDeductSuccess(double newBalance, double cost) {
                        Log.d(TAG, "========================================");
                        Log.d(TAG, "✅ BILLING SUCCESS");
                        Log.d(TAG, "   Cost: " + cost + " minutes");
                        Log.d(TAG, "   New balance: " + newBalance + " minutes");
                        Log.d(TAG, "========================================");

                        // Show success message
                        Toast.makeText(context,
                                String.format("Call cost: %.1f min | Balance: %.1f min", cost, newBalance),
                                Toast.LENGTH_LONG).show();

                        // Check if should auto-disable AI
                        if (newBalance < 0.1) {
                            Log.w(TAG, "⚠️ BALANCE DEPLETED - Auto-disabling AI");

                            PreferencesManager prefs = PreferencesManager.getInstance(context);
                            if (prefs.isBotActive()) {
                                prefs.setIsBotActive(false);

                                Toast.makeText(context,
                                        "AI disabled - balance depleted. Please top up.",
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    }

                    @Override
                    public void onDeductError(String error) {
                        Log.e(TAG, "========================================");
                        Log.e(TAG, "❌ BILLING ERROR");
                        Log.e(TAG, "   Error: " + error);
                        Log.e(TAG, "========================================");

                        Toast.makeText(context,
                                "Billing error: " + error,
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void resetCallTracking() {
        Log.d(TAG, "Resetting call tracking for next call");
        currentCallPhoneNumber = null;
        callStartTimestamp = 0;
        recordingFilePath = null;
    }



// ============================================================================
// Add field to track recording start time:

    private long recordingStartTime = 0;



    // ============================================================================
    // ENHANCED AI FEATURES
    // ============================================================================

    private void initializeEnhancedAIComponents() {
        try {
            // HTTP client with better configuration
            httpClient = new OkHttpClient.Builder()
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                    .pingInterval(PING_INTERVAL, TimeUnit.MILLISECONDS)
                    .retryOnConnectionFailure(true)
                    .build();

            // Don't start audio stream injector automatically
            if (audioStreamInjector != null) {
                // Initialize but don't start
                Log.d(TAG, "Audio stream injector initialized (not started)");
            }

            Log.d(TAG, "Enhanced AI components initialized");

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize enhanced AI components: " + e.getMessage());
            totalErrorsEncountered.incrementAndGet();
        }
    }

    private void setupEnhancedCallbacks() {
        // Audio processor callbacks
        if (audioProcessor != null) {
            audioProcessor.setCallback(new EnhancedAudioProcessor.AudioProcessorCallback() {
                @Override
                public void onSpeechStarted() {
                    Log.d(TAG, "Speech detection: STARTED");
                    notifyCallback(cb -> cb.onSpeechDetected(0));
                }

                @Override
                public void onSpeechEnded(long durationMs) {
                    Log.d(TAG, "Speech detection: ENDED (" + durationMs + "ms)");
                    notifyCallback(cb -> cb.onSpeechDetected(durationMs));
                }

                @Override
                public void onSilenceDetected(long durationMs) {
                    Log.v(TAG, "Silence detected: " + durationMs + "ms");
                    notifyCallback(cb -> cb.onSilenceDetected(durationMs));
                }

                @Override
                public void onAudioQualityChanged(EnhancedAudioProcessor.AudioQuality quality) {
                    Log.d(TAG, "Audio quality: " + quality.level + " - " + quality.reason);
                    notifyCallback(cb -> cb.onAudioQualityChanged(quality.level.toString(), quality.reason));
                }

                @Override
                public void onChunkProcessed(EnhancedAudioProcessor.AudioChunk chunk, boolean hasVoice) {
                    // Chunk processed - monitoring only
                }
            });
        }

        // Response buffer callbacks
        if (responseBuffer != null) {
            responseBuffer.setCallback(new AIResponseBuffer.ResponseCallback() {
                @Override
                public void onResponseStarted() {
                    if (audioStreamer != null) {
                        audioStreamer.pauseStreaming();
                    }

                    Log.d(TAG, "AI response playback started");
                }

                @Override
                public void onResponseStopped() {
                    mainHandler.postDelayed(() -> {
                        if (audioStreamer != null && isAIConnected.get()) {
                            audioStreamer.resumeStreaming();
                            Log.d(TAG, "✅ Mic capture resumed");
                        }
                    }, 300);  // 300ms buffer

                    Log.d(TAG, "AI response playback stopped");
                }

                @Override
                public void onResponseError(String error) {
                    Log.e(TAG, "AI response error: " + error);
                    if (audioStreamer != null) {
                        audioStreamer.resumeStreaming();
                    }


                    totalErrorsEncountered.incrementAndGet();
                    notifyCallback(cb -> cb.onAIError("Response error: " + error));
                }

                @Override
                public void onAudioReceived(int audioSize) {
                    Log.v(TAG, "AI audio received: " + audioSize + " bytes");
                    totalAudioChunksReceived.incrementAndGet();
                }
            });
        }

        // Audio accumulator callbacks
        if (audioAccumulator != null) {
            audioAccumulator.setCallback(new AudioResponseAccumulator.AccumulatorCallback() {
                @Override
                public void onResponseStarted() {
                    Log.d(TAG, "AI response accumulation started");
                }

                @Override
                public void onChunkAccumulated(int chunkSize, int totalSize) {
                    Log.v(TAG, "Audio chunk accumulated: " + chunkSize + " bytes, total: " + totalSize);
                }

                @Override
                public void onResponseCompleted(byte[] completeAudio, long durationMs) {
                    Log.d(TAG, "Complete AI response ready: " + completeAudio.length + " bytes, " + durationMs + "ms");
                    injectCompleteAudioResponse(completeAudio);
                }

                @Override
                public void onResponseTimeout(byte[] partialAudio, long durationMs) {
                    Log.w(TAG, "AI response timeout: " + partialAudio.length + " bytes, " + durationMs + "ms");
                    injectCompleteAudioResponse(partialAudio);
                }

                @Override
                public void onAccumulatorError(String error) {
                    Log.e(TAG, "Audio accumulator error: " + error);
                    totalErrorsEncountered.incrementAndGet();
                    notifyCallback(cb -> cb.onAIError("Audio accumulation error: " + error));
                }
            });
        }

        // Sequential injector callbacks
        if (sequentialInjector != null) {
            sequentialInjector.setCallback(new SequentialAudioInjector.SequentialInjectionCallback() {
                @Override
                public void onQueueStatusChanged(int queueSize, boolean isProcessing) {
                    Log.v(TAG, "Injection queue: " + queueSize + " items, processing: " + isProcessing);
                }

                @Override
                public void onInjectionStarted(int chunkNumber, int chunkSize) {
                    Log.v(TAG, "Injection started: chunk #" + chunkNumber + " (" + chunkSize + " bytes)");
                    notifyCallback(cb -> cb.onAudioInjectionStarted("Sequential #" + chunkNumber));
                }

                @Override
                public void onInjectionCompleted(int chunkNumber, boolean success, long durationMs) {
                    Log.v(TAG, "Injection completed: chunk #" + chunkNumber +
                            " (" + (success ? "SUCCESS" : "FAILED") + ", " + durationMs + "ms)");
                    if (success) {
                        notifyCallback(cb -> cb.onAudioInjected(0, 0));
                    } else {
                        notifyCallback(cb -> cb.onAudioInjectionError("Sequential injection failed"));
                    }
                }

                @Override
                public void onInjectionError(int chunkNumber, String error, int retryCount) {
                    Log.w(TAG, "Injection error: chunk #" + chunkNumber + " - " + error + " (retry " + retryCount + ")");
                    notifyCallback(cb -> cb.onAudioInjectionError("Chunk #" + chunkNumber + ": " + error));
                }

                @Override
                public void onQueueOverflow(int droppedChunks) {
                    Log.w(TAG, "Injection queue overflow: " + droppedChunks + " chunks dropped");
                }

                @Override
                public void onStatisticsUpdate(SequentialAudioInjector.InjectionStatistics stats) {
                    Log.v(TAG, " processed, " +
                            stats.successRate + "% success rate");
                }
            });
        }
    }

    private void startEnhancedAIFeatures() {
        Log.d(TAG, "Starting Enhanced AI Features (with proper sequencing)...");

        try {
            shouldReconnect.set(true);

            audioInjector.startStreamingMode(new CallAudioInjector.InjectionCallback() {
                @Override
                public void onStreamingStarted() {
                    Log.d(TAG, "Streaming ready - now connecting to ElevenLabs");
                    // ONLY start AI connection after streaming is ready
//                    connectToElevenLabsWithRetry();
                }

                @Override
                public void onStreamingStopped() {
//                    CallAudioInjector.InjectionCallback.super.onStreamingStopped();
                }

                @Override
                public void onInjectionError(String error) {
                    Log.e(TAG, "Streaming failed, falling back to file mode: " + error);
//                    connectToElevenLabsWithRetry(); // Continue with file-based injection
                }

                @Override
                public void onInjectionProgress(String output) {
                    CallAudioInjector.InjectionCallback.super.onInjectionProgress(output);
                }

                @Override
                public void onInjectionStarted() {
                    Log.d(TAG, "Audio injection streaming mode started");
                }

                @Override
                public void onInjectionCompleted(boolean success) {

                }
            });


            // Initialize response buffer with retry logic
            boolean bufferReady = false;
            for (int attempt = 1; attempt <= 3; attempt++) {
                if (responseBuffer.initialize()) {
                    bufferReady = true;
                    Log.d(TAG, "Response buffer initialized on attempt " + attempt);
                    break;
                } else {
                    Log.w(TAG, "Response buffer initialization attempt " + attempt + " failed");
                    if (attempt < 3) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
            }

            if (bufferReady) {
                // Connect with proper state management
                connectToElevenLabsWithRetry();
                Log.d(TAG, "Enhanced AI features initialization started");
            } else {
                Log.e(TAG, "AI response buffer failed after 3 attempts");
                notifyCallback(cb -> cb.onAIError("AI audio playback initialization failed"));
            }
        } catch (Exception e) {
            Log.e(TAG, "Enhanced AI features failed to start: " + e.getMessage());
            totalErrorsEncountered.incrementAndGet();
            notifyCallback(cb -> cb.onAIError("AI initialization failed: " + e.getMessage()));
        }
    }

    private void connectToElevenLabsWithRetry() {
        if (elevenLabsApiKey == null || agentId == null) {
            Log.e(TAG, "ElevenLabs credentials not set");
            notifyCallback(cb -> cb.onAIError("ElevenLabs credentials missing"));
            return;
        }

        // Ensure clean state before connecting
        if (hasActiveConnection.get() || isConnecting.get() || isAIConnected.get()) {
            Log.w(TAG, "Already connecting or connected, skipping duplicate connection");
            return;
        }

        Log.d(TAG, "Starting ElevenLabs connection with retry logic...");
        hasActiveConnection.set(true);

        connectionExecutor.execute(() -> {
            // Wait a bit to ensure core recording is stable
//            try {
//                Thread.sleep(1000);
//            } catch (InterruptedException e) {
//                hasActiveConnection.set(false);
//                return;
//            }

            // Verify core recording is still active
            if (!coreRecorder.isRecording()) {
                Log.w(TAG, "Core recording not active, skipping AI connection");
                hasActiveConnection.set(false);
                return;
            }

            attemptConnection();
        });
    }

    private static final AtomicInteger activeWebSocketCount = new AtomicInteger(0);
    private static final AtomicLong totalWebSocketsCreated = new AtomicLong(0);


    private void attemptConnection() {
        if (!shouldReconnect.get() || isConnecting.get() || isAIConnected.get()) {
            return;
        }

        isConnecting.set(true);
        int attempt = connectionAttempts.incrementAndGet();

        Log.d(TAG, "Connection attempt " + attempt + "/" + MAX_CONNECTION_ATTEMPTS);

        // Exponential backoff with jitter
        if (attempt > 1) {
            long delay = Math.min(INITIAL_RECONNECT_DELAY * (1L << (attempt - 1)), MAX_RECONNECT_DELAY);
            delay += (long) (Math.random() * 500);

            Log.d(TAG, "Waiting " + delay + "ms before connection attempt...");

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Log.d(TAG, "Connection delay interrupted");
                isConnecting.set(false);
                hasActiveConnection.set(false);
                return;
            }
        }

        try {
            // Double-check before creating WebSocket
            if (elevenLabsSocket != null) {
                Log.w(TAG, "WebSocket already exists, closing old connection first");
                elevenLabsSocket.close(1000, "Replacing connection");
                elevenLabsSocket = null;
            }

            String wsUrl = ELEVENLABS_WS_URL + "?agent_id=" + agentId;

            Request request = new Request.Builder()
                    .url(wsUrl)
                    .addHeader("xi-api-key", elevenLabsApiKey)
                    .addHeader("Connection", "Upgrade")
                    .addHeader("Upgrade", "websocket")
                    .addHeader("Sec-WebSocket-Version", "13")
                    .addHeader("User-Agent", "TeleTalker-Fixed/2.0")
                    .build();

            elevenLabsSocket = httpClient.newWebSocket(request, new EnhancedWebSocketListener());

            // Connection timeout handling
            mainHandler.postDelayed(() -> {
                if (isConnecting.get() && !isAIConnected.get()) {
                    Log.w(TAG, "Connection timeout after " + CONNECTION_TIMEOUT + "ms");
                    handleConnectionFailure("Connection timeout");
                }
            }, CONNECTION_TIMEOUT);

        } catch (Exception e) {
            Log.e(TAG, "Connection attempt " + attempt + " failed: " + e.getMessage());
            totalErrorsEncountered.incrementAndGet();
            handleConnectionFailure(e.getMessage());
        }
    }

    private void handleConnectionFailure(String reason) {
        isConnecting.set(false);
        isAIConnected.set(false);
        hasActiveConnection.set(false);

        if (elevenLabsSocket != null) {
            try {
                elevenLabsSocket.close(1000, "Connection failed");
            } catch (Exception e) {
                // Ignore close errors
            }
            elevenLabsSocket = null;
        }

        if (shouldReconnect.get() && connectionAttempts.get() < MAX_CONNECTION_ATTEMPTS &&
                coreRecorder.isRecording()) {

            Log.d(TAG, "Scheduling reconnection attempt " + (connectionAttempts.get() + 1) +
                    "/" + MAX_CONNECTION_ATTEMPTS);

            mainHandler.postDelayed(() -> {
                if (shouldReconnect.get() && coreRecorder.isRecording()) {
                    attemptConnection();
                }
            }, 1000);

        } else {
            Log.e(TAG, "All connection attempts exhausted or reconnection disabled");
            connectionAttempts.set(0);
            notifyCallback(cb -> cb.onAIError("AI connection failed after " + MAX_CONNECTION_ATTEMPTS +
                    " attempts: " + reason));
        }
    }

    private void startConnectionHealthMonitoring() {
        Runnable healthCheck = new Runnable() {
            @Override
            public void run() {
                if (isAIConnected.get() && shouldReconnect.get()) {
                    long timeSinceLastPong = System.currentTimeMillis() - lastPongReceived.get();
                    long timeSinceLastSend = System.currentTimeMillis() - lastSuccessfulSend.get();

                    // FIXED: Much more lenient timeout for call scenarios
                    if (timeSinceLastPong > PING_INTERVAL * 4) { // 80 seconds instead of 40
                        Log.w(TAG, "Ping timeout after " + timeSinceLastPong + "ms");
                        handleConnectionFailure("Ping timeout - no pong received");
                        return;
                    }

                    if (timeSinceLastSend > 90000) { // 90 seconds
                        Log.w(TAG, "No successful sends for " + timeSinceLastSend + "ms");
                        notifyCallback(cb -> cb.onConnectionHealthChanged(false));
                    }

                    // Send health check ping less frequently
                    if (timeSinceLastPong > PING_INTERVAL * 2) {
                        sendHealthCheckPing();
                    }
                }

                // Schedule next health check - less frequent
                healthCheckHandler.postDelayed(this, PING_INTERVAL);
            }
        };

        healthCheckHandler.postDelayed(healthCheck, PING_INTERVAL);
    }

    private void sendHealthCheckPing() {
        if (elevenLabsSocket != null && isAIConnected.get()) {
            try {
                JSONObject ping = new JSONObject();
                ping.put("type", "ping");
                ping.put("timestamp", System.currentTimeMillis());

                boolean sent = elevenLabsSocket.send(ping.toString());
                if (!sent) {
                    Log.w(TAG, "Failed to send health check ping");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending health check ping: " + e.getMessage());
            }
        }
    }

    private void stopEnhancedAIFeatures() {
        Log.d(TAG, "Stopping Enhanced AI Features...");

        shouldReconnect.set(false);
        hasActiveConnection.set(false);


        if (audioInjector.isStreamingModeActive()) {
            audioInjector.stopStreamingMode(null);
        }


        // Stop health monitoring
        reconnectHandler.removeCallbacksAndMessages(null);
        healthCheckHandler.removeCallbacksAndMessages(null);

        // Reset connection state
        isAIConnected.set(false);
        isConnecting.set(false);
        connectionAttempts.set(0);

        // Stop streaming components
        if (audioStreamer != null) {
            audioStreamer.stopStreaming();
        }

        // Force complete any pending audio response
        if (audioAccumulator != null) {
            audioAccumulator.forceCompleteResponse();
        }

        // Stop response buffer
        if (responseBuffer != null) {
            responseBuffer.shutdown();
        }

        // Reset accumulator
        if (audioAccumulator != null) {
            audioAccumulator.reset();
        }

        // Stop injection components
        if (sequentialInjector != null) {
            sequentialInjector.pause();
        }

        if (audioStreamInjector != null) {
            audioStreamInjector.stopStreaming();
        }

        // Close WebSocket properly
        if (elevenLabsSocket != null) {
            try {
                // Send end conversation message
                ElevenLabsWebSocketConfig.sendEndConversation(elevenLabsSocket);
                Thread.sleep(500); // Give time for message to send
            } catch (Exception e) {
                Log.w(TAG, "Warning sending end message: " + e.getMessage());
            }

            elevenLabsSocket.close(1000, "Call ended normally");
            elevenLabsSocket = null;
            Log.d(TAG, "ElevenLabs connection properly closed");
        }

        Log.d(TAG, "Enhanced AI features stopped");
    }

    // ============================================================================
    // ENHANCED WEBSOCKET LISTENER
    // ============================================================================

    private class EnhancedWebSocketListener extends WebSocketListener {

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.d(TAG, "WebSocket Connected Successfully - Code: " + response.code());
            aiWasUsedInThisCall = true;

            isAIConnected.set(true);
            isConnecting.set(false);

            broadcastAIStatus("CONNECTED", coreRecorder.isRecording(), false, "");

            connectionAttempts.set(0);
            lastPongReceived.set(System.currentTimeMillis());
            lastSuccessfulSend.set(System.currentTimeMillis());

            // Build and send initial configuration WITH conversation initiation data
            try {
                JSONObject initData = buildConversationInitiationData(isOutboundCall);
                ElevenLabsWebSocketConfig.sendInitialConfiguration(webSocket, agentId, initData);

                if (initData != null && isOutboundCall) {
                    Log.d(TAG, "Outbound call configuration sent with purpose and notes");
                } else {
                    Log.d(TAG, "Standard initial configuration sent");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to send initial configuration: " + e.getMessage());
            }

            // Start audio streaming with delay
            mainHandler.postDelayed(() -> {
                if (coreRecorder.isRecording() && isAIConnected.get()) {
                    startEnhancedAudioStreaming();
                } else {
                    Log.w(TAG, "Skipping audio streaming - call not active or connection lost");
                }
            }, 300);

            notifyCallback(cb -> cb.onAIConnected());
            notifyCallback(cb -> cb.onConnectionHealthChanged(true));
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            totalMessagesReceived.incrementAndGet();

            try {
                JSONObject message = new JSONObject(text);

                // Use comprehensive message handler
                ElevenLabsWebSocketConfig.handleElevenLabsMessage(message,
                        new ElevenLabsWebSocketConfig.MessageHandler() {
                            @Override
                            public void onAudioReceived(byte[] audioData) {
                                totalAudioChunksReceived.incrementAndGet();
                                Log.d(TAG, "AI Audio Response: " + audioData.length + " bytes");

                                // Add to response buffer for local playback
                                responseBuffer.addAudioResponse(audioData);

                                // Choose injection method
                                if (useRealTimeInjection) {
                                    if (audioStreamInjector != null) {
                                        audioStreamInjector.streamPCM(audioData);
                                    }
//                                    if (sequentialInjector != null) {
//                                        sequentialInjector.queueAudioChunk(audioData);
//                                    }
                                } else {
                                    if (sequentialInjector != null) {
                                        sequentialInjector.queueAudioChunk(audioData);
                                    }
//                                    audioAccumulator.addAudioChunk(audioData);
                                }

                                broadcastAIStatus("ACTIVE", coreRecorder.isRecording(), true, "");

                            }

                            @Override
                            public void onAgentResponse(String transcript) {
                                Log.d(TAG, "AI Response: '" + transcript + "'");
                                notifyCallback(cb -> cb.onAIResponse(transcript, responseBuffer.isCurrentlyPlaying()));
                                broadcastAIStatus("ACTIVE", coreRecorder.isRecording(),
                                        isAudioInjectionActive(), transcript);

                            }

                            @Override
                            public void onUserTranscript(String transcript, boolean isFinal) {
                                Log.d(TAG, "User Said: '" + transcript + "' (final: " + isFinal + ")");
                            }

                            @Override
                            public void onPingReceived(JSONObject pongResponse) {
                                boolean sent = webSocket.send(pongResponse.toString());
                                if (sent) {
                                    Log.v(TAG, "Pong sent successfully");
                                    lastSuccessfulSend.set(System.currentTimeMillis());
                                } else {
                                    Log.w(TAG, "Failed to send pong response");
                                }
                            }

                            @Override
                            public void onError(String message, String code) {
                                Log.e(TAG, "ElevenLabs Error: " + message +
                                        (code.isEmpty() ? "" : " (Code: " + code + ")"));
                                totalErrorsEncountered.incrementAndGet();
                                broadcastAIStatus("ERROR", coreRecorder.isRecording(), false, "Error: " + message);

                                notifyCallback(cb -> cb.onAIError("ElevenLabs Error: " + message));
                            }

                            @Override
                            public void onConversationMetadata(String conversationId) {
                                Log.d(TAG, "Conversation ID: " + conversationId);
                            }

                            @Override
                            public void onSessionCreated(String sessionId) {
                                Log.d(TAG, "Session ID: " + sessionId);
                            }
                        });

            } catch (JSONException e) {
                broadcastAIStatus("ERROR", coreRecorder.isRecording(), false, "Parse error");

                Log.e(TAG, "Error parsing ElevenLabs message: " + e.getMessage());
                Log.v(TAG, "Raw message: " + text);
                totalErrorsEncountered.incrementAndGet();
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            byte[] audioData = bytes.toByteArray();
            totalAudioChunksReceived.incrementAndGet();

            Log.d(TAG, "Binary Audio Response: " + audioData.length + " bytes");

            // Handle binary audio the same way as JSON audio
            responseBuffer.addAudioResponse(audioData);

            if (useRealTimeInjection) {
                if (audioStreamInjector != null) {
                    audioStreamInjector.streamPCM(audioData);
                }
                if (sequentialInjector != null) {
                    sequentialInjector.queueAudioChunk(audioData);
                }
            } else {
                audioAccumulator.addAudioChunk(audioData);
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {

            broadcastAIStatus("ERROR", coreRecorder.isRecording(), false, "Connection failed");

            String errorMsg = "WebSocket failure: " + t.getMessage();
            if (response != null) {
                errorMsg += " (HTTP " + response.code() + ")";
            }

            Log.e(TAG, errorMsg);
            totalErrorsEncountered.incrementAndGet();

            // Better error classification and handling
            if (isAuthenticationError(response)) {
                Log.e(TAG, "Authentication error - check API key and agent ID");
                shouldReconnect.set(false);
                notifyCallback(cb -> cb.onAIError("Authentication failed - check credentials"));
            } else if (isNetworkError(t)) {
                Log.w(TAG, "Network error detected, will attempt reconnection");
                handleConnectionFailure("Network error: " + t.getMessage());
            } else {
                Log.e(TAG, "Unexpected error: " + errorMsg);
                handleConnectionFailure(errorMsg);
            }
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "WebSocket Closed: " + code + " - " + reason);
            broadcastAIStatus("HIDDEN", false, false, "");

            isAIConnected.set(false);
            isConnecting.set(false);
            hasActiveConnection.set(false);

            if (code == 1000) {
                ElevenLabsWebSocketConfig.sendEndConversation(webSocket);
            }

            notifyCallback(cb -> cb.onAIDisconnected());
            notifyCallback(cb -> cb.onConnectionHealthChanged(false));

            // Smart reconnection logic
            if (shouldReconnect.get() && coreRecorder.isRecording() && code != 1000) {
                Log.w(TAG, "Unexpected closure during recording, attempting reconnection...");
                handleConnectionFailure("Connection closed unexpectedly: " + reason);
            }
        }
    }

    private void startEnhancedAudioStreaming() {
        Log.d(TAG, "Starting Enhanced Audio Streaming with proper setup...");

        if (audioStreamer == null) {
            Log.e(TAG, "AudioStreamer not initialized");
            notifyCallback(cb -> cb.onAIError("Audio streaming not available"));
            return;
        }

        if (elevenLabsSocket == null) {
            Log.e(TAG, "No WebSocket available for audio streaming");
            notifyCallback(cb -> cb.onAIError("WebSocket not available for audio"));
            return;
        }

        try {
            // Set WebSocket and start with proper error handling
            audioStreamer.setExistingWebSocket(elevenLabsSocket);

            audioStreamer.startAudioCaptureOnly(new OptimizedAudioStreamer.StreamerCallback() {
                @Override
                public void onStreamingStarted() {
                    Log.d(TAG, "Audio streaming started successfully");
                    notifyCallback(cb -> cb.onAIStreamingStarted("Enhanced Single Connection"));
                }

                @Override
                public void onStreamingStopped() {
                    Log.d(TAG, "Audio streaming stopped");
                    notifyCallback(cb -> cb.onAIStreamingStopped());
                }

                @Override
                public void onChunkSent(int chunkSize, boolean hasVoice) {
                    lastSuccessfulSend.set(System.currentTimeMillis());
                    if (hasVoice) {
                        Log.v(TAG, "Voice chunk sent: " + chunkSize + " bytes");
                    }
                }

                @Override
                public void onStreamingError(String error) {
                    Log.e(TAG, "Audio streaming error: " + error);
                    totalErrorsEncountered.incrementAndGet();
                    notifyCallback(cb -> cb.onAIError("Audio streaming error: " + error));
                }

                @Override
                public void onQueueOverflow(int droppedChunks) {
                    Log.w(TAG, "Audio queue overflow: " + droppedChunks + " chunks dropped");
                }

                @Override
                public void onConnectionHealthChanged(boolean healthy) {
                    Log.d(TAG, "Audio connection health: " + (healthy ? "GOOD" : "POOR"));
                    notifyCallback(cb -> cb.onConnectionHealthChanged(healthy));
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Failed to start audio streaming: " + e.getMessage());
            notifyCallback(cb -> cb.onAIError("Audio streaming failed: " + e.getMessage()));
        }
    }

    private boolean isNetworkError(Throwable t) {
        if (t == null) return false;

        String message = t.getMessage();
        if (message == null) return false;

        return message.contains("unexpected end of stream") ||
                message.contains("timeout") ||
                message.contains("connection") ||
                message.contains("network") ||
                message.contains("host") ||
                message.contains("socket") ||
                t instanceof java.net.SocketTimeoutException ||
                t instanceof java.net.ConnectException ||
                t instanceof java.io.IOException;
    }

    private boolean isAuthenticationError(Response response) {
        return response != null && (response.code() == 401 || response.code() == 403);
    }

    // ============================================================================
    // ENHANCED AUDIO INJECTION
    // ============================================================================

    private void injectCompleteAudioResponse(byte[] completeAudio) {
        if (completeAudio == null || completeAudio.length == 0) {
            Log.w(TAG, "Empty complete audio response");
            return;
        }

        Log.d(TAG, "Injecting Complete AI Response: " + completeAudio.length + " bytes");

        // Safety check
        if (!coreRecorder.isRecording()) {
            Log.d(TAG, "Skipping complete audio injection - recording not active");
            return;
        }

        CallAudioInjector.InjectionCallback injectionCallback = new CallAudioInjector.InjectionCallback() {
            @Override
            public void onInjectionStarted() {
                if (audioStreamer != null) {
                    audioStreamer.pauseStreaming();
                    Log.d(TAG, "Audio streaming PAUSED for injection");
                }
                Log.d(TAG, "Complete audio injection started");
                notifyCallback(cb -> cb.onAudioInjectionStarted("Complete Response"));
            }

            @Override
            public void onInjectionCompleted(boolean success) {
                if (audioStreamer != null) {
                    audioStreamer.resumeStreaming();
                    Log.d(TAG, "Audio streaming RESUMED after injection");
                }


                if (success) {
                    Log.d(TAG, "Complete audio injection completed successfully");
                    notifyCallback(cb -> cb.onAudioInjected(completeAudio.length, completeAudio.length));
                } else {
                    Log.w(TAG, "Complete audio injection completed with issues");
                    notifyCallback(cb -> cb.onAudioInjectionError("Complete injection had issues"));
                }
                notifyCallback(cb -> cb.onAudioInjectionStopped());
            }

            @Override
            public void onInjectionError(String error) {
                if (audioStreamer != null) {
                    audioStreamer.resumeStreaming();
                    Log.d(TAG, "Audio streaming RESUMED after injection");
                }

                Log.e(TAG, "Complete audio injection error: " + error);
                totalErrorsEncountered.incrementAndGet();
                notifyCallback(cb -> cb.onAudioInjectionError(error));
                notifyCallback(cb -> cb.onAudioInjectionStopped());
            }

        };

        try {
            audioInjector.injectAudio16kMono(completeAudio, injectionCallback);
        } catch (Exception e) {
            if (audioStreamer != null) {
                audioStreamer.resumeStreaming();
                Log.d(TAG, "Audio streaming RESUMED after injection");
            }

            Log.e(TAG, "Failed to inject complete audio: " + e.getMessage(), e);
            totalErrorsEncountered.incrementAndGet();
            notifyCallback(cb -> cb.onAudioInjectionError("Complete injection failed: " + e.getMessage()));
        }
    }

    // ============================================================================
    // ENHANCED PUBLIC API
    // ============================================================================

    // Injection control methods
    public void setRealTimeInjection(boolean enabled) {
        useRealTimeInjection = enabled;
        Log.d(TAG, "Real-time injection: " + (enabled ? "ENABLED" : "DISABLED"));
    }

    public void pauseInjection() {
        if (sequentialInjector != null) {
            sequentialInjector.pause();
            Log.d(TAG, "Audio injection paused");
        }
    }

    public void resumeInjection() {
        if (sequentialInjector != null) {
            sequentialInjector.resume();
            Log.d(TAG, "Audio injection resumed");
        }
    }

    // Status getters
    public boolean isRecording() {
        return coreRecorder != null && coreRecorder.isRecording();
    }

    public boolean isAIEnabled() {
        return isAIEnabled.get();
    }

    public boolean isAIConnected() {
        return isAIConnected.get();
    }

    public boolean isAIResponding() {
        return responseBuffer != null && responseBuffer.isCurrentlyPlaying();
    }

    public boolean isAudioInjectionActive() {
        boolean directInjection = audioInjector != null && audioInjector.isCurrentlyInjecting();
        boolean sequentialInjection = (sequentialInjector != null && sequentialInjector.isProcessing());
        return directInjection || sequentialInjection;
    }

    public AIMode getCurrentAIMode() {
        return currentAIMode;
    }

    public void logEnhancedAIStatus() {
        Log.d(TAG, "=== ENHANCED AI STATUS ===");
        Log.d(TAG, "Initialized: " + isInitialized.get());
        Log.d(TAG, "AI Enabled: " + isAIEnabled.get());
        Log.d(TAG, "AI Connected: " + isAIConnected.get());
        Log.d(TAG, "Connection Attempts: " + connectionAttempts.get() + "/" + MAX_CONNECTION_ATTEMPTS);
        Log.d(TAG, "Should Reconnect: " + shouldReconnect.get());
        Log.d(TAG, "AI Mode: " + currentAIMode.getDescription());
        Log.d(TAG, "Real-time Injection: " + useRealTimeInjection);

        // Connection statistics
        Log.d(TAG, "Messages Received: " + totalMessagesReceived.get());
        Log.d(TAG, "Audio Chunks Received: " + totalAudioChunksReceived.get());
        Log.d(TAG, "Errors Encountered: " + totalErrorsEncountered.get());

        // Health status
        long timeSinceLastPong = System.currentTimeMillis() - lastPongReceived.get();
        long timeSinceLastSend = System.currentTimeMillis() - lastSuccessfulSend.get();
        Log.d(TAG, "Last Pong: " + timeSinceLastPong + "ms ago");
        Log.d(TAG, "Last Send: " + timeSinceLastSend + "ms ago");

        // Component status
        Log.d(TAG, "Recording Active: " + isRecording());
        Log.d(TAG, "AI Responding: " + isAIResponding());
        Log.d(TAG, "Audio Injection: " + getAudioInjectionStatus());

        if (audioStreamer != null) {
            OptimizedAudioStreamer.StreamingStats stats = audioStreamer.getStats();
            Log.d(TAG, "Streaming Stats: " + stats.totalChunks + " total, " +
                    stats.voiceChunks + " voice, " + stats.droppedChunks + " dropped");
        }
    }

    public String getAudioInjectionStatus() {
        StringBuilder status = new StringBuilder();

        if (audioInjector != null && audioInjector.isCurrentlyInjecting()) {
            status.append("Direct: ACTIVE");
        }

        if (sequentialInjector != null) {
            if (status.length() > 0) status.append(", ");
            status.append("Sequential: ").append(sequentialInjector.getStatus());
        }

        if (audioStreamInjector != null) {
            if (status.length() > 0) status.append(", ");
            status.append("Stream: INITIALIZED");
        }

        return status.length() > 0 ? status.toString() : "INACTIVE";
    }

    // Callback helper
    private void notifyCallback(CallbackAction action) {
        if (callback != null) {
            mainHandler.post(() -> {
                try {
                    action.execute(callback);
                } catch (Exception e) {
                    Log.e(TAG, "Callback error: " + e.getMessage());
                }
            });
        }
    }

    @FunctionalInterface
    private interface CallbackAction {
        void execute(AIRecordingCallback callback);
    }

    /**
     * FIXED: Comprehensive cleanup with proper resource management
     */
    public void cleanup() {
        Log.d(TAG, "Starting Enhanced Cleanup...");

        // Stop recording and AI features
        stopRecording();

        // Reset initialization state
        isInitialized.set(false);
        isInitializing.set(false);

        // Clear all handlers
        if (reconnectHandler != null) {
            reconnectHandler.removeCallbacksAndMessages(null);
        }
        if (healthCheckHandler != null) {
            healthCheckHandler.removeCallbacksAndMessages(null);
        }

        // Cleanup components
        if (audioStreamer != null) {
            audioStreamer.cleanup();
        }
        if (audioProcessor != null) {
            audioProcessor.reset();
        }
        if (responseBuffer != null) {
            responseBuffer.destroy();
        }
        if (audioAccumulator != null) {
            audioAccumulator.cleanup();
        }
        if (audioInjector != null) {
            audioInjector.cleanup();
        }
        if (sequentialInjector != null) {
            sequentialInjector.cleanup();
        }
        if (audioStreamInjector != null) {
            audioStreamInjector.stopStreaming();
        }

        // Shutdown executors properly
        shutdownExecutor(sharedExecutor, "SharedExecutor");
        shutdownExecutor(connectionExecutor, "ConnectionExecutor");

        // Close HTTP client
        if (httpClient != null) {
            try {
                httpClient.dispatcher().executorService().shutdown();
                httpClient.connectionPool().evictAll();
            } catch (Exception e) {
                Log.w(TAG, "Error closing HTTP client: " + e.getMessage());
            }
        }

        Log.d(TAG, "Enhanced Cleanup completed");
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor != null && !executor.isShutdown()) {
            Log.d(TAG, "Shutting down " + name + "...");
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    Log.w(TAG, name + " didn't terminate gracefully, forcing shutdown");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Log.w(TAG, name + " shutdown interrupted");
                executor.shutdownNow();
            }
        }
    }

    /**
     * Get comprehensive statistics about the AI system
     */
    public AIStatistics getAIStatistics() {
        return new AIStatistics(
                totalMessagesReceived.get(),
                totalAudioChunksReceived.get(),
                totalErrorsEncountered.get(),
                connectionAttempts.get(),
                isAIConnected.get(),
                System.currentTimeMillis() - lastSuccessfulSend.get(),
                audioStreamer != null ? audioStreamer.getStats() : null
        );
    }

    public static class AIStatistics {
        public final long totalMessages;
        public final long totalAudioChunks;
        public final long totalErrors;
        public final int connectionAttempts;
        public final boolean isConnected;
        public final long timeSinceLastSend;
        public final OptimizedAudioStreamer.StreamingStats streamingStats;

        public AIStatistics(long totalMessages, long totalAudioChunks, long totalErrors,
                            int connectionAttempts, boolean isConnected, long timeSinceLastSend,
                            OptimizedAudioStreamer.StreamingStats streamingStats) {
            this.totalMessages = totalMessages;
            this.totalAudioChunks = totalAudioChunks;
            this.totalErrors = totalErrors;
            this.connectionAttempts = connectionAttempts;
            this.isConnected = isConnected;
            this.timeSinceLastSend = timeSinceLastSend;
            this.streamingStats = streamingStats;
        }
    }
}