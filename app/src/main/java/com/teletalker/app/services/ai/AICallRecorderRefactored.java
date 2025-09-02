package com.teletalker.app.services.ai;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;

import com.teletalker.app.services.CallAudioInjector;

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
 *
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
    private static final int MAX_CONNECTION_ATTEMPTS = 5;
    private static final long INITIAL_RECONNECT_DELAY = 2000L; // 2 seconds
    private static final long MAX_RECONNECT_DELAY = 30000L; // 30 seconds
    private static final long CONNECTION_TIMEOUT = 30000L; // 30 seconds
    private static final long PING_INTERVAL = 20000L; // 20 seconds

    // Audio Configuration
    private static final int OPTIMIZED_SAMPLE_RATE = 16000;
    private static final int OPTIMIZED_CHUNK_SIZE_MS = 100;
    private static final boolean USE_ENHANCED_PROCESSING = true;

    // Enhanced injection options
    private SequentialAudioInjector sequentialInjector;
    private AudioStreamInjector audioStreamInjector;
    private boolean useRealTimeInjection = true;

    // AI Response modes
    public enum AIMode {
        LISTEN_ONLY("AI listens but doesn't respond"),
        RESPOND_TO_USER("AI responds only to user"),
        RESPOND_TO_CALLER("AI responds only to caller"),
        RESPOND_TO_BOTH("AI responds to both parties"),
        SMART_ASSISTANT("AI acts as smart assistant");

        private final String description;
        AIMode(String description) { this.description = description; }
        public String getDescription() { return description; }
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

    public AICallRecorderRefactored(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());

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

    /**
     * FIXED: Start recording with proper initialization and state reset
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public boolean startRecording(String filename) {
        Log.d(TAG, "Starting Enhanced AI Call Recording: " + filename);

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

            // === ENHANCED AI FEATURES ===
            if (isAIEnabled.get()) {
                // FIXED: Start AI features with delay to avoid race conditions
                mainHandler.postDelayed(() -> {
                    if (coreRecorder.isRecording()) {
                        startEnhancedAIFeatures();
                    } else {
                        Log.w(TAG, "Core recording stopped before AI features could start");
                    }
                }, 2000); // 2 second delay for stability
            } else {
                Log.d(TAG, "AI features disabled - recording without AI enhancement");
            }

            return true;
        } else {
            Log.e(TAG, "Failed to start core recording");
            return false;
        }
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
    public void stopRecording() {
        Log.d(TAG, "Stopping Enhanced AI Call Recording...");

        // Stop AI features first
        if (isAIEnabled.get()) {
            stopEnhancedAIFeatures();
        }

        // Stop core recording
        if (coreRecorder != null) {
            coreRecorder.stopRecording();
        }

        Log.d(TAG, "Enhanced AI Call Recording stopped");
    }

    // ============================================================================
    // ENHANCED AI FEATURES
    // ============================================================================

    private void initializeEnhancedAIComponents() {
        try {
            // HTTP client with better configuration
            httpClient = new OkHttpClient.Builder()
                    .readTimeout(45, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
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
                    Log.d(TAG, "AI response playback started");
                }

                @Override
                public void onResponseStopped() {
                    Log.d(TAG, "AI response playback stopped");
                }

                @Override
                public void onResponseError(String error) {
                    Log.e(TAG, "AI response error: " + error);
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
                    Log.v(TAG,  " processed, " +
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
                        try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
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
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                hasActiveConnection.set(false);
                return;
            }

            // Verify core recording is still active
            if (!coreRecorder.isRecording()) {
                Log.w(TAG, "Core recording not active, skipping AI connection");
                hasActiveConnection.set(false);
                return;
            }

            attemptConnection();
        });
    }

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
            delay += (long) (Math.random() * 1000);

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
            }, 3000);

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

            isAIConnected.set(true);
            isConnecting.set(false);
            connectionAttempts.set(0);
            lastPongReceived.set(System.currentTimeMillis());
            lastSuccessfulSend.set(System.currentTimeMillis());

            // Send initial configuration
            try {
                ElevenLabsWebSocketConfig.sendInitialConfiguration(webSocket, agentId);
                Log.d(TAG, "Initial configuration sent");
            } catch (Exception e) {
                Log.e(TAG, "Failed to send initial configuration: " + e.getMessage());
            }

            // Start audio streaming with delay and verification
            mainHandler.postDelayed(() -> {
                if (coreRecorder.isRecording() && isAIConnected.get()) {
                    startEnhancedAudioStreaming();
                } else {
                    Log.w(TAG, "Skipping audio streaming - call not active or connection lost");
                }
            }, 2000); // 2 second delay

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
                                    if (sequentialInjector != null) {
                                        sequentialInjector.queueAudioChunk(audioData);
                                    }
                                } else {
                                    audioAccumulator.addAudioChunk(audioData);
                                }
                            }

                            @Override
                            public void onAgentResponse(String transcript) {
                                Log.d(TAG, "AI Response: '" + transcript + "'");
                                notifyCallback(cb -> cb.onAIResponse(transcript, responseBuffer.isCurrentlyPlaying()));
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
                Log.d(TAG, "Complete audio injection started");
                notifyCallback(cb -> cb.onAudioInjectionStarted("Complete Response"));
            }

            @Override
            public void onInjectionCompleted(boolean success) {
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
                Log.e(TAG, "Complete audio injection error: " + error);
                totalErrorsEncountered.incrementAndGet();
                notifyCallback(cb -> cb.onAudioInjectionError(error));
                notifyCallback(cb -> cb.onAudioInjectionStopped());
            }

        };

        try {
            audioInjector.injectAudio16kMono(completeAudio, injectionCallback);
        } catch (Exception e) {
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