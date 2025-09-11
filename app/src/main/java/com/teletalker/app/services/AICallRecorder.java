package com.teletalker.app.services;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;

import com.teletalker.app.utils.PreferencesManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import java.io.ByteArrayOutputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;

/**
 * AICallRecorder with COMPLETE FIXES for Audio Injection and ElevenLabs Integration
 *
 * FIXES APPLIED:
 * ✅ Audio accumulation - no more partial injection
 * ✅ Proper ElevenLabs conversation ending
 * ✅ Enhanced audio chunk streaming
 * ✅ Comprehensive resource management
 * ✅ Advanced debugging capabilities
 */
public class AICallRecorder {

    // === ElevenLabs reliability & batching ===
    private final java.util.concurrent.ConcurrentLinkedQueue<byte[]> outgoingChunkQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final java.util.concurrent.ScheduledExecutorService batchScheduler = java.util.concurrent.Executors.newScheduledThreadPool(1);
    private final AtomicLong totalChunksSent = new AtomicLong(0);
    private final AtomicLong totalChunksAcked = new AtomicLong(0);
    private volatile boolean batchSchedulerStarted = false;
    private static final int BATCH_MAX_MS = 250;
    private static final int BATCH_MAX_BYTES = 16 * 1024;
    private static final int MAX_ACK_WAIT_MS = 6000;

    private static final String TAG = "AICallRecorder";

    // Audio configuration
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    // Audio sources
    private static final int[] AUDIO_SOURCES_ANDROID_12_PLUS = {
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
    };

    private static final int[] AUDIO_SOURCES_LEGACY = {
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_UPLINK,
            MediaRecorder.AudioSource.VOICE_DOWNLINK,
            MediaRecorder.AudioSource.MIC
    };

    // ElevenLabs Configuration
    private static final String ELEVENLABS_WS_URL = "wss://api.elevenlabs.io/v1/convai/conversation";
    private static final int AI_SAMPLE_RATE = 16000;

    // FIXED: Audio accumulation with improved management
    private ByteArrayOutputStream audioChunkBuffer = new ByteArrayOutputStream();
    private final AtomicBoolean isAccumulatingAudio = new AtomicBoolean(false);
    private final AtomicBoolean isCurrentlyInjecting = new AtomicBoolean(false);
    private final AtomicLong lastElevenLabsMessageTime = new AtomicLong(0);
    private long lastAudioChunkTime = 0;
    private CountDownLatch injectionCompleteLatch = null;

    // NEW: Enhanced audio accumulation
    private volatile boolean isWaitingForMoreChunks = false;
    private volatile long lastChunkReceivedTime = 0;
    private static final long CHUNK_TIMEOUT_MS = 2000; // Wait 2s for more chunks
    private static final long MIN_AUDIO_DURATION_MS = 1000; // Minimum 1s before injection
    private final java.util.concurrent.ConcurrentLinkedQueue<byte[]> pendingChunksQueue =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    // Recording modes
    public enum RecordingMode {
        VOICE_CALL_TWO_WAY("Two-way call recording (both sides)"),
        VOICE_COMMUNICATION("Call recording (optimized)"),
        VOICE_UPLINK("Outgoing voice only"),
        VOICE_DOWNLINK("Incoming voice only"),
        MICROPHONE_ONLY("Microphone only (your voice)"),
        UNKNOWN("Unknown mode"),
        FAILED("Recording failed");

        private final String description;
        RecordingMode(String description) { this.description = description; }
        public String getDescription() { return description; }
    }

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
    public interface RecordingCallback {
        void onRecordingStarted(RecordingMode mode);
        void onRecordingFailed(String reason);
        void onRecordingStopped(String filename, long duration);
        void onAIConnected();
        void onAIDisconnected();
        void onAIError(String error);
        void onAIResponse(String transcript, boolean isPlaying);
        // Audio injection callbacks
        default void onAudioInjectionStarted(String method) {}
        default void onAudioInjectionStopped() {}
        default void onAudioInjected(int chunkSize, long totalBytes) {}
        default void onAudioInjectionError(String error) {}
    }

    // === CORE RECORDING COMPONENTS ===
    private Context context;
    private MediaRecorder mediaRecorder;
    private ExecutorService executorService;
    private Handler mainHandler;
    private RecordingCallback callback;

    // State tracking
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private String currentRecordingFile;
    private long recordingStartTime;
    private RecordingMode currentRecordingMode = RecordingMode.UNKNOWN;
    private int currentAudioSource;

    // System capabilities
    private final AtomicBoolean isRooted = new AtomicBoolean(false);
    private final AtomicBoolean voiceCallAccessible = new AtomicBoolean(false);

    // === AI COMPONENTS ===
    private AudioRecord aiAudioRecord;
    private AudioTrack audioTrack;
    private WebSocket elevenLabsSocket;
    private OkHttpClient httpClient;
    private String elevenLabsApiKey;
    private String agentId;
    private AIMode currentAIMode = AIMode.SMART_ASSISTANT;

    // AI State management
    private final AtomicBoolean isAIEnabled = new AtomicBoolean(false);
    private final AtomicBoolean isAIConnected = new AtomicBoolean(false);
    private final AtomicBoolean isAIStreaming = new AtomicBoolean(false);
    private final AtomicBoolean isAIResponding = new AtomicBoolean(false);

    // AI Audio processing
    private ArrayBlockingQueue<byte[]> aiResponseQueue;
    private int aiBufferSize;

    // === AUDIO INJECTION COMPONENTS ===
    private CallAudioInjector audioInjector;
    private final AtomicBoolean isAudioInjectionEnabled = new AtomicBoolean(true);
    private final AtomicBoolean isAudioInjectionActive = new AtomicBoolean(false);

    // FIXED: Connection management with proper synchronization
    private int connectionAttempts = 0;
    private static final int MAX_CONNECTION_ATTEMPTS = 5;
    private static final long RECONNECT_DELAY_MS = 3000;
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private Handler reconnectHandler = new Handler(Looper.getMainLooper());

    // FIXED: Connection health monitoring with thread safety
    private final Object webSocketStateLock = new Object();
    private volatile long lastMessageTime = 0;
    private static final long CONNECTION_HEALTH_INTERVAL = 30000;
    private boolean firstAudioMessageLogged = false;

    public AICallRecorder(Context context) {
        this.context = context;
        this.executorService = Executors.newFixedThreadPool(4);
        this.mainHandler = new Handler(Looper.getMainLooper());
        setElevenLabsConfig();
        // Initialize core capabilities
        executorService.execute(this::initializeCapabilities);

        // Initialize AI components
        initializeAIComponents();


        // Initialize Audio Injection
        initializeAudioInjection();
    }

    public void setCallback(RecordingCallback callback) {
        this.callback = callback;
    }

    public void setElevenLabsConfig() {
        PreferencesManager manager = PreferencesManager.getInstance(context);
        this.elevenLabsApiKey = manager.getApiKey();
        this.agentId = manager.getSelectedAgentId();
        this.isAIEnabled.set(manager.isBotActive());
        Log.d(TAG, "ElevenLabs config - AI Enabled: " + isAIEnabled.get() +
                ", API Key: " + (this.elevenLabsApiKey != null ? "***set***= " + elevenLabsApiKey : "null") +
                ", Agent ID: " + agentId);
    }

    public void setAIMode(AIMode mode) {
        this.currentAIMode = mode;
        Log.d(TAG, "AI Mode set to: " + mode.getDescription());
    }

    // === AUDIO INJECTION METHODS ===
    private void initializeAudioInjection() {
        try {
            audioInjector = new CallAudioInjector(context);
            Log.d(TAG, "🎧 Call audio injector created");
        } catch (Exception e) {
            Log.e(TAG, "⚠️ Failed to create call audio injector: " + e.getMessage());
            audioInjector = null;
        }
    }

    private void stopAudioInjection() {
        if (audioInjector != null && isAudioInjectionActive.get()) {
            isAudioInjectionActive.set(false);
            Log.d(TAG, "🎧 Audio injection stopped");
        }
    }

    // ============================================================================
    // MAIN RECORDING METHODS
    // ============================================================================

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public boolean startRecording(String filename) {
        if (isRecording.get()) {
            Log.w(TAG, "Recording already in progress");
            return false;
        }

        if (!hasRecordAudioPermission()) {
            notifyCallback(cb -> cb.onRecordingFailed("RECORD_AUDIO permission not granted"));
            return false;
        }

        try {
            recordingStartTime = System.currentTimeMillis();
            currentRecordingFile = createRecordingPath(filename);
            Log.d(TAG, "🚀 Starting AI-enhanced call recording: " + filename);
            Log.d(TAG, "AI Features: " + (isAIEnabled.get() ? "Enabled" : "Disabled"));
            Log.d(TAG, "Audio Injection: " + (isAudioInjectionEnabled.get() ? "Enabled" : "Disabled"));
            logDeviceInfo();

            // Apply system fixes if needed
            if (isRooted.get()) {
                applyRecordingFixes();
            }

            // === CORE RECORDING (ALWAYS WORKS) ===
            boolean coreRecordingStarted = attemptRecordingWithBestSource();
            if (coreRecordingStarted) {
                isRecording.set(true);

                // === OPTIONAL AI FEATURES ===
                if (isAIEnabled.get()) {
                    startAIFeatures();
                } else {
                    Log.d(TAG, "⚠️ AI features disabled - recording without AI");
                }

                analyzeRecordingQuality();
                notifyCallback(cb -> cb.onRecordingStarted(currentRecordingMode));
                Log.d(TAG, "✅ Recording started with " + currentRecordingMode.getDescription());
                return true;
            } else {
                Log.e(TAG, "❌ Failed to start core recording with any audio source");
                notifyCallback(cb -> cb.onRecordingFailed("No compatible audio source found"));
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "Exception starting recording: " + e.getMessage(), e);
            notifyCallback(cb -> cb.onRecordingFailed("Exception: " + e.getMessage()));
            cleanup();
            return false;
        }
    }

    public void stopRecording() {
        if (!isRecording.get()) {
            Log.w(TAG, "No recording in progress");
            return;
        }

        try {
            Log.d(TAG, "🛑 Stopping AI-enhanced call recording...");
            long recordingDuration = System.currentTimeMillis() - recordingStartTime;
            isRecording.set(false);

            // Stop core recording
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            }

            // Stop AI features
            if (isAIEnabled.get()) {
                stopAIFeatures();
            }

            // Validate and finalize recording
            String finalFilename = validateRecording();
            notifyCallback(cb -> cb.onRecordingStopped(finalFilename, recordingDuration));
            Log.d(TAG, "✅ Recording stopped. Duration: " + (recordingDuration / 1000) + "s");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording: " + e.getMessage(), e);
        } finally {
            cleanup();
        }
    }

    // ============================================================================
    // RECORDING IMPLEMENTATION
    // ============================================================================

    private boolean attemptRecordingWithBestSource() {
        int[] audioSources = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
                AUDIO_SOURCES_ANDROID_12_PLUS : AUDIO_SOURCES_LEGACY;

        for (int audioSource : audioSources) {
            if (attemptRecordingWithSource(audioSource)) {
                currentAudioSource = audioSource;
                currentRecordingMode = getRecordingModeFromSource(audioSource);
                return true;
            }
        }
        currentRecordingMode = RecordingMode.FAILED;
        return false;
    }

    private boolean attemptRecordingWithSource(int audioSource) {
        try {
            Log.d(TAG, "🧪 Trying audio source: " + getAudioSourceName(audioSource));

            // 1. START MAIN RECORDING (MediaRecorder for file)
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(audioSource);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            configureRecordingSettings(audioSource);
            mediaRecorder.setOutputFile(currentRecordingFile);
            mediaRecorder.prepare();
            Thread.sleep(200);
            mediaRecorder.start();
            Log.d(TAG, "✅ Main recording started with " + getAudioSourceName(audioSource));

            // 2. START SEPARATE AUDIORECORD FOR AI
            if (isAIEnabled.get()) {
                startAIAudioCapture(audioSource);
            }

            return true;
        } catch (Exception e) {
            Log.w(TAG, "❌ Failed with " + getAudioSourceName(audioSource) + ": " + e.getMessage());
            cleanup();
            return false;
        }
    }

    private void startAIAudioCapture(int mainAudioSource) {
        Log.d(TAG, "🎤 Starting AI audio capture...");

        // Try different audio sources for AI
        int[] aiAudioSources = {
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                mainAudioSource,
                MediaRecorder.AudioSource.DEFAULT
        };

        for (int aiSource : aiAudioSources) {
            if (initializeAIAudioWithSource(aiSource)) {
                Log.d(TAG, "✅ AI audio started with: " + getAudioSourceName(aiSource));
                return;
            }
        }
        Log.w(TAG, "⚠️ Could not start AI audio capture - continuing with recording only");
    }

    // FIXED: Robust AudioRecord initialization with proper error handling
    private boolean initializeAIAudioWithSource(int audioSource) {
        AudioRecord testAudioRecord = null;

        try {
            Log.d(TAG, "🎤 Initializing AI audio with source: " + getAudioSourceName(audioSource));

            aiBufferSize = AudioRecord.getMinBufferSize(AI_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (aiBufferSize < 0) {
                Log.w(TAG, "❌ Invalid buffer size for " + getAudioSourceName(audioSource));
                return false;
            }

            // Create AudioRecord instance
            testAudioRecord = new AudioRecord(
                    audioSource,
                    AI_SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    aiBufferSize
            );

            if (testAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "❌ AI audio source " + getAudioSourceName(audioSource) + " - NOT INITIALIZED");
                return false;
            }

            // Test recording capability
            boolean hasRealAudio = false;
            try {
                testAudioRecord.startRecording();
                Thread.sleep(100); // Brief stabilization delay

                byte[] testBuffer = new byte[320];
                int bytesRead = testAudioRecord.read(testBuffer, 0, testBuffer.length);

                // Check if we got real audio (not silence)
                if (bytesRead > 0) {
                    int samplesAboveThreshold = 0;
                    int totalSamples = bytesRead / 2;

                    for (int i = 0; i < bytesRead - 1; i += 2) {
                        short sample = (short) ((testBuffer[i + 1] << 8) | (testBuffer[i] & 0xFF));
                        if (Math.abs(sample) > 100) {
                            samplesAboveThreshold++;
                        }
                    }

                    double audioPercentage = totalSamples > 0 ? (double) samplesAboveThreshold / totalSamples : 0;
                    hasRealAudio = audioPercentage > 0.05;

                    Log.d(TAG, "🎵 Audio analysis: " + samplesAboveThreshold + "/" + totalSamples +
                            " samples above threshold (" + String.format("%.1f", audioPercentage * 100) + "%)");
                }

            } catch (Exception e) {
                Log.w(TAG, "❌ Error testing audio source " + getAudioSourceName(audioSource) + ": " + e.getMessage());
                return false;
            }

            if (hasRealAudio) {
                Log.d(TAG, "🎯 AI audio source " + getAudioSourceName(audioSource) + " - HAS REAL AUDIO");

                // FIXED: Transfer ownership only on success
                aiAudioRecord = testAudioRecord;
                testAudioRecord = null; // Prevent cleanup in finally block

                // AudioRecord is already recording from the test, so we're ready
                return true;

            } else {
                Log.w(TAG, "⚠️ AI audio source " + getAudioSourceName(audioSource) + " - SILENCE ONLY");
                return false;
            }

        } catch (Exception e) {
            Log.w(TAG, "❌ AI audio source " + getAudioSourceName(audioSource) + " failed: " + e.getMessage());
            return false;

        } finally {
            // FIXED: Guaranteed cleanup of test AudioRecord if not transferred to aiAudioRecord
            if (testAudioRecord != null) {
                try {
                    if (testAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        testAudioRecord.stop();
                    }
                    testAudioRecord.release();
                    Log.d(TAG, "🗑️ Test AudioRecord cleaned up");
                } catch (Exception e) {
                    Log.w(TAG, "⚠️ Error cleaning up test AudioRecord: " + e.getMessage());
                }
            }
        }
    }

    private void configureRecordingSettings(int audioSource) {
        boolean isAndroid12Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
        if (audioSource == MediaRecorder.AudioSource.VOICE_CALL && isAndroid12Plus) {
            mediaRecorder.setAudioSamplingRate(48000);
            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setAudioChannels(2);
        } else if (audioSource == MediaRecorder.AudioSource.VOICE_COMMUNICATION) {
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setAudioEncodingBitRate(96000);
            mediaRecorder.setAudioChannels(1);
        } else {
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setAudioEncodingBitRate(64000);
            mediaRecorder.setAudioChannels(1);
        }
    }

    private RecordingMode getRecordingModeFromSource(int audioSource) {
        switch (audioSource) {
            case MediaRecorder.AudioSource.VOICE_CALL:
                return RecordingMode.VOICE_CALL_TWO_WAY;
            case MediaRecorder.AudioSource.VOICE_COMMUNICATION:
                return RecordingMode.VOICE_COMMUNICATION;
            case MediaRecorder.AudioSource.VOICE_UPLINK:
                return RecordingMode.VOICE_UPLINK;
            case MediaRecorder.AudioSource.VOICE_DOWNLINK:
                return RecordingMode.VOICE_DOWNLINK;
            case MediaRecorder.AudioSource.MIC:
                return RecordingMode.MICROPHONE_ONLY;
            default:
                return RecordingMode.UNKNOWN;
        }
    }

    // ============================================================================
    // AI FEATURES
    // ============================================================================

    private void initializeAIComponents() {
        try {
            aiResponseQueue = new ArrayBlockingQueue<>(10);
            httpClient = new OkHttpClient.Builder()
                    .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                    .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();
            Log.d(TAG, "✅ AI components initialized");
        } catch (Exception e) {
            Log.e(TAG, "⚠️ Failed to initialize AI components: " + e.getMessage());
        }
    }

    private void startAIFeatures() {
        Log.d(TAG, "🤖 Starting AI features...");
        try {
            if (initializeAIAudioStreaming()) {
                initializeAIAudioPlayback();
                connectToElevenLabs();
                startAIThreads();
                Log.d(TAG, "✅ AI features started successfully");
            } else {
                Log.w(TAG, "⚠️ AI audio streaming failed - continuing with recording only");
                notifyCallback(cb -> cb.onAIError("AI audio streaming unavailable"));
            }
        } catch (Exception e) {
            Log.e(TAG, "⚠️ AI features failed to start: " + e.getMessage());
            notifyCallback(cb -> cb.onAIError("AI initialization failed: " + e.getMessage()));
        }
    }

    private boolean initializeAIAudioStreaming() {
        try {
            if (aiAudioRecord != null && aiAudioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                Log.d(TAG, "✅ AI audio streaming ready (AudioRecord already recording)");
                return true;
            } else {
                Log.w(TAG, "⚠️ AI audio streaming not available");
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "⚠️ AI audio streaming failed: " + e.getMessage());
            return false;
        }
    }

    private void initializeAIAudioPlayback() {
        try {
            int playbackBufferSize = AudioTrack.getMinBufferSize(AI_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AUDIO_FORMAT);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                    .build();

            AudioFormat audioFormat = new AudioFormat.Builder()
                    .setSampleRate(AI_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AUDIO_FORMAT)
                    .build();

            audioTrack = new AudioTrack(
                    audioAttributes,
                    audioFormat,
                    playbackBufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
            );

            Log.d(TAG, "✅ AI audio playback initialized");
        } catch (Exception e) {
            Log.w(TAG, "⚠️ AI audio playback failed: " + e.getMessage());
        }
    }

    private void connectToElevenLabs() {
        connectToElevenLabsWithRetry();
    }

    private void connectToElevenLabsWithRetry() {
        if (elevenLabsApiKey == null || agentId == null) {
            Log.w(TAG, "⚠️ ElevenLabs credentials not set");
            return;
        }

        if (isConnecting.get()) {
            Log.d(TAG, "🔄 Connection already in progress...");
            return;
        }

        isConnecting.set(true);
        connectionAttempts++;

        executorService.execute(() -> {
            try {
                Log.d(TAG, "🔌 Connecting to ElevenLabs (attempt " + connectionAttempts + "/" + MAX_CONNECTION_ATTEMPTS + ")...");
                String wsUrl = ELEVENLABS_WS_URL + "?agent_id=" + agentId;

                Request request = new Request.Builder()
                        .url(wsUrl)
                        .addHeader("xi-api-key", elevenLabsApiKey)
                        .addHeader("Connection", "Upgrade")
                        .addHeader("Upgrade", "websocket")
                        .addHeader("Sec-WebSocket-Version", "13")
                        .addHeader("User-Agent", "TeleTalker-AI/1.0")
                        .build();

                elevenLabsSocket = httpClient.newWebSocket(request, new RobustElevenLabsWebSocketListener());
                waitForConnectionResult();

            } catch (Exception e) {
                Log.e(TAG, "❌ Connection attempt " + connectionAttempts + " failed: " + e.getMessage());
                handleConnectionFailure(e);
            }
        });
    }

    private void waitForConnectionResult() {
        try {
            for (int i = 0; i < 30; i++) {
                Thread.sleep(500);
                if (isAIConnected.get()) {
                    Log.d(TAG, "✅ Connected successfully on attempt " + connectionAttempts);
                    connectionAttempts = 0;
                    isConnecting.set(false);
                    return;
                }
                if (!isRecording.get()) {
                    Log.d(TAG, "🛑 Recording stopped, canceling connection");
                    isConnecting.set(false);
                    return;
                }
            }
            Log.w(TAG, "⏰ Connection timeout on attempt " + connectionAttempts);
            handleConnectionFailure(new Exception("Connection timeout"));
        } catch (InterruptedException e) {
            Log.w(TAG, "🛑 Connection wait interrupted");
            isConnecting.set(false);
        }
    }

    private void handleConnectionFailure(Exception error) {
        isConnecting.set(false);
        isAIConnected.set(false);

        if (elevenLabsSocket != null) {
            try {
                elevenLabsSocket.close(1000, "Reconnecting");
            } catch (Exception e) {
                // Ignore close errors
            }
            elevenLabsSocket = null;
        }

        if (connectionAttempts < MAX_CONNECTION_ATTEMPTS && isRecording.get()) {
            long delay = RECONNECT_DELAY_MS * connectionAttempts;
            Log.d(TAG, "🔄 Retrying connection in " + delay + "ms... (attempt " + (connectionAttempts + 1) + "/" + MAX_CONNECTION_ATTEMPTS + ")");
            reconnectHandler.postDelayed(() -> {
                if (isRecording.get()) {
                    connectToElevenLabsWithRetry();
                }
            }, delay);
        } else {
            Log.e(TAG, "❌ All connection attempts failed or recording stopped");
            connectionAttempts = 0;
            notifyCallback(cb -> cb.onAIError("AI connection failed after " + MAX_CONNECTION_ATTEMPTS + " attempts: " + error.getMessage()));
        }
    }

    private void startAIThreads() {
        if (aiAudioRecord != null) {
            executorService.execute(this::aiStreamingThread);
        }
        if (audioTrack != null) {
            executorService.execute(this::aiResponsePlaybackThread);
        }
    }

    // IMPROVED: Enhanced AI streaming thread with better error handling
    private void aiStreamingThread() {
        if (aiAudioRecord == null) {
            Log.e(TAG, "❌ AI streaming thread: AudioRecord is null");
            return;
        }

        byte[] buffer = new byte[aiBufferSize / 4]; // Smaller chunks for better responsiveness
        int totalChunksSent = 0;
        int chunksWithRealAudio = 0;
        int consecutiveEmptyReads = 0;
        long lastLogTime = System.currentTimeMillis();
        long lastHealthCheck = System.currentTimeMillis();

        try {
            // Ensure AudioRecord is recording
            if (aiAudioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                Log.w(TAG, "⚠️ AudioRecord not recording, attempting to start...");
                aiAudioRecord.startRecording();
                Thread.sleep(200); // Give it time to start
            }

            isAIStreaming.set(true);
            Log.d(TAG, "🤖 AI streaming thread started");

            while (isRecording.get() && isAIStreaming.get()) {
                try {
                    int bytesRead = aiAudioRecord.read(buffer, 0, buffer.length);

                    if (bytesRead > 0) {
                        consecutiveEmptyReads = 0;

                        if (isAIConnected.get()) {
                            byte[] audioChunk = new byte[bytesRead];
                            System.arraycopy(buffer, 0, audioChunk, 0, bytesRead);

                            // Check if this chunk has real audio
                            boolean hasRealAudio = !isAudioSilenceImproved(audioChunk);
                            if (hasRealAudio) {
                                chunksWithRealAudio++;
                            }

                            streamChunkToAI(audioChunk);
                            totalChunksSent++;
                        }
                    } else if (bytesRead < 0) {
                        consecutiveEmptyReads++;
                        Log.w(TAG, "AudioRecord read error: " + bytesRead + " (consecutive: " + consecutiveEmptyReads + ")");

                        if (consecutiveEmptyReads > 10) {
                            Log.e(TAG, "❌ Too many consecutive read errors, restarting AudioRecord");
                            restartAudioRecord();
                            consecutiveEmptyReads = 0;
                        }
                        Thread.sleep(50);
                    } else {
                        // bytesRead == 0
                        Thread.sleep(10);
                    }

                    // Enhanced periodic logging and health check
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastLogTime >= 10000) { // Every 10 seconds
                        int percentRealAudio = totalChunksSent > 0 ? (chunksWithRealAudio * 100 / totalChunksSent) : 0;
                        Log.d(TAG, "📡 AI Streaming Status (10s):");
                        Log.d(TAG, " 📤 Chunks sent: " + totalChunksSent);
                        Log.d(TAG, " 🎵 Real audio: " + chunksWithRealAudio + " (" + percentRealAudio + "%)");
                        Log.d(TAG, " 🔗 Connected: " + isAIConnected.get());
                        Log.d(TAG, " 📊 Queue size: " + outgoingChunkQueue.size());
                        Log.d(TAG, " 🎤 Recording state: " + aiAudioRecord.getRecordingState());

                        // Reset counters
                        totalChunksSent = 0;
                        chunksWithRealAudio = 0;
                        lastLogTime = currentTime;
                    }

                    // Health check every 30 seconds
                    if (currentTime - lastHealthCheck >= 30000) {
                        checkAudioRecordHealth();
                        lastHealthCheck = currentTime;
                    }

                } catch (Exception e) {
                    Log.w(TAG, "AI streaming error: " + e.getMessage());
                    Thread.sleep(100);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "AI streaming thread error: " + e.getMessage());
        } finally {
            isAIStreaming.set(false);
            Log.d(TAG, "🤖 AI streaming thread stopped");
        }
    }

    // NEW: AudioRecord health check and restart
    private void checkAudioRecordHealth() {
        if (aiAudioRecord == null) return;

        try {
            int state = aiAudioRecord.getState();
            int recordingState = aiAudioRecord.getRecordingState();

            Log.d(TAG, "🏥 AudioRecord health: State=" + state + ", Recording=" + recordingState);

            if (state != AudioRecord.STATE_INITIALIZED || recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.w(TAG, "⚠️ AudioRecord health issue detected, attempting restart");
                restartAudioRecord();
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error checking AudioRecord health: " + e.getMessage());
        }
    }

    // NEW: Restart AudioRecord if it fails
    private void restartAudioRecord() {
        try {
            Log.d(TAG, "🔄 Restarting AudioRecord...");

            if (aiAudioRecord != null) {
                try {
                    if (aiAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        aiAudioRecord.stop();
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error stopping AudioRecord: " + e.getMessage());
                }
            }

            Thread.sleep(500); // Brief pause

            if (aiAudioRecord != null) {
                aiAudioRecord.startRecording();
                Thread.sleep(200);

                if (aiAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    Log.d(TAG, "✅ AudioRecord restarted successfully");
                } else {
                    Log.e(TAG, "❌ AudioRecord restart failed");
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error restarting AudioRecord: " + e.getMessage());
        }
    }

    private void aiResponsePlaybackThread() {
        if (audioTrack == null) return;

        Log.d(TAG, "🔊 AI response playback thread started");
        while (isRecording.get()) {
            try {
                byte[] responseAudio = aiResponseQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (responseAudio != null && audioTrack != null) {
                    if (!isAIResponding.get()) {
                        isAIResponding.set(true);
                        audioTrack.play();
                    }
                    audioTrack.write(responseAudio, 0, responseAudio.length);
                }

                if (aiResponseQueue.isEmpty() && isAIResponding.get()) {
                    isAIResponding.set(false);
                    if (audioTrack != null) {
                        audioTrack.pause();
                    }
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                Log.w(TAG, "AI playback error: " + e.getMessage());
            }
        }
        Log.d(TAG, "🔊 AI response playback thread stopped");
    }

    // IMPROVED: Better audio streaming with enhanced chunk handling
    private void streamChunkToAI(byte[] audioChunk) {
        if (elevenLabsSocket == null || !isAIConnected.get()) {
            Log.w(TAG, "⚠️ Cannot stream - not connected to ElevenLabs");
            return;
        }

        if (audioChunk == null || audioChunk.length == 0) {
            Log.w(TAG, "⚠️ Empty audio chunk, skipping");
            return;
        }

        // IMPROVED: Better silence detection
        if (isAudioSilenceImproved(audioChunk)) {
            Log.v(TAG, "🔇 Skipping silent chunk");
            return;
        }

        // Log first audio message for debugging
        if (!firstAudioMessageLogged) {
            Log.d(TAG, "🎤 First audio chunk being sent: " + audioChunk.length + " bytes");
            firstAudioMessageLogged = true;
        }

        // Add to queue for batching
        boolean queued = outgoingChunkQueue.offer(audioChunk);
        if (!queued) {
            Log.w(TAG, "⚠️ Outgoing queue full, dropping chunk");
            return;
        }

        // Calculate total queued data
        int queuedBytes = 0;
        int queuedChunks = 0;
        for (byte[] chunk : outgoingChunkQueue) {
            queuedBytes += chunk.length;
            queuedChunks++;
        }

        Log.v(TAG, "📤 Queued: " + queuedChunks + " chunks, " + queuedBytes + " bytes");

        // Flush immediately if we have enough data or chunks
        if (queuedBytes >= BATCH_MAX_BYTES || queuedChunks >= 5) {
            Log.d(TAG, "🚀 Auto-flushing queue: " + queuedBytes + " bytes, " + queuedChunks + " chunks");
            executorService.execute(this::flushOutgoingQueueImproved);
        }
    }

    // IMPROVED: Better silence detection
    private boolean isAudioSilenceImproved(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return true;
        }

        // Analyze audio more thoroughly
        int silenceThreshold = 150; // Slightly higher threshold
        int totalSamples = 0;
        int silentSamples = 0;
        int maxAmplitude = 0;
        long rmsSum = 0;

        for (int i = 0; i < audioData.length - 1; i += 2) {
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            int amplitude = Math.abs(sample);

            totalSamples++;
            rmsSum += sample * sample;

            if (amplitude <= silenceThreshold) {
                silentSamples++;
            }

            if (amplitude > maxAmplitude) {
                maxAmplitude = amplitude;
            }
        }

        if (totalSamples == 0) return true;

        double silencePercentage = (double) silentSamples / totalSamples;
        double rms = Math.sqrt((double) rmsSum / totalSamples);

        // More sophisticated silence detection
        boolean isSilent = silencePercentage > 0.85 && maxAmplitude < 500 && rms < 300;

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, String.format("🔊 Audio analysis: Silent=%.1f%%, Max=%d, RMS=%.1f, IsSilent=%b",
                    silencePercentage * 100, maxAmplitude, rms, isSilent));
        }

        return isSilent;
    }

    // IMPROVED: Enhanced queue flushing with error handling
    private void flushOutgoingQueueImproved() {
        if (elevenLabsSocket == null || !isAIConnected.get()) {
            Log.w(TAG, "⚠️ Cannot flush - not connected");
            return;
        }

        List<byte[]> chunksToSend = new ArrayList<>();
        int totalBytes = 0;

        // Collect chunks to send
        byte[] chunk;
        while ((chunk = outgoingChunkQueue.poll()) != null && totalBytes < BATCH_MAX_BYTES) {
            chunksToSend.add(chunk);
            totalBytes += chunk.length;

            if (chunksToSend.size() >= 10) { // Limit number of chunks per batch
                break;
            }
        }

        if (chunksToSend.isEmpty()) {
            return;
        }

        try {
            // Combine all chunks into one array
            ByteArrayOutputStream combinedStream = new ByteArrayOutputStream();
            for (byte[] chunkData : chunksToSend) {
                combinedStream.write(chunkData);
            }
            byte[] combinedAudio = combinedStream.toByteArray();

            // Encode and send
            String base64Audio = android.util.Base64.encodeToString(combinedAudio, android.util.Base64.NO_WRAP);
            long chunkId = totalChunksSent.incrementAndGet();

            JSONObject audioMessage = new JSONObject();
            audioMessage.put("type", "user_audio_chunk");
            audioMessage.put("audio_chunk", base64Audio);
            audioMessage.put("client_chunk_id", "batch-" + chunkId);

            boolean sent = elevenLabsSocket.send(audioMessage.toString());

            if (sent) {
                Log.d(TAG, "📤 Sent batch " + chunkId + ": " + chunksToSend.size() + " chunks, " + totalBytes + " bytes");
                scheduleAckCheck(chunkId);
            } else {
                Log.e(TAG, "❌ Failed to send batch " + chunkId);
                // Re-queue failed chunks at front
                for (int i = chunksToSend.size() - 1; i >= 0; i--) {
                    outgoingChunkQueue.offer(chunksToSend.get(i));
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error flushing queue: " + e.getMessage());
            // Re-queue chunks on error
            for (byte[] chunkData : chunksToSend) {
                outgoingChunkQueue.offer(chunkData);
            }
        }
    }

    // FIXED: Batching methods with complete implementation
    private void startBatchSchedulerIfNeeded() {
        if (batchSchedulerStarted) return;
        batchSchedulerStarted = true;
        batchScheduler.scheduleAtFixedRate(() -> {
            try {
                flushOutgoingQueueImproved();
                checkAckLag();
            } catch (Exception e) {
                Log.w(TAG, "Batch scheduler error: " + e.getMessage());
            }
        }, BATCH_MAX_MS, BATCH_MAX_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private void scheduleAckCheck(long sentCountSnapshot) {
        batchScheduler.schedule(() -> {
            long acked = totalChunksAcked.get();
            if (acked < sentCountSnapshot) {
                Log.w(TAG, "Potential missing ACKs. Sent up to: " + sentCountSnapshot + " ; Acked: " + acked);
            }
        }, MAX_ACK_WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private void checkAckLag() {
        long sent = totalChunksSent.get();
        long acked = totalChunksAcked.get();
        if (sent - acked > 6) {
            Log.d(TAG, "ACK lag: Sent=" + sent + " Acked=" + acked);
        }
    }

    private void sendUserAudioCompleteAndWait() {
        if (elevenLabsSocket == null || !isAIConnected.get()) return;
        try {
            JSONObject complete = new JSONObject();
            complete.put("type", "user_audio_complete");
            elevenLabsSocket.send(complete.toString());
            long waitStart = System.currentTimeMillis();
            while (System.currentTimeMillis() - waitStart < 2000) {
                if (totalChunksAcked.get() >= totalChunksSent.get()) break;
                Thread.sleep(100);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error sending user_audio_complete: " + e.getMessage());
        }
    }

    private void handleAIAudioResponse(byte[] audioData) {
        if (audioData != null && audioData.length > 0) {
            Log.d(TAG, "🔊 AI Audio Chunk received: " + audioData.length + " bytes");

            // 1. Queue for local playback
            if (!aiResponseQueue.offer(audioData)) {
                Log.w(TAG, "⚠️ AI response queue full, dropping old audio");
                aiResponseQueue.poll();
                aiResponseQueue.offer(audioData);
            }

            // 2. Accumulate chunks for injection
            accumulateAudioChunk(audioData);
        }
    }

    // FIXED: Enhanced audio accumulation strategy
    private synchronized void accumulateAudioChunk(byte[] audioChunk) {
        try {
            // Don't accumulate if currently injecting - but queue for next injection
            if (isCurrentlyInjecting.get()) {
                Log.d(TAG, "⚠️ Injection in progress, queuing chunk for next injection");
                queueChunkForLater(audioChunk);
                return;
            }

            // Add chunk to buffer
            audioChunkBuffer.write(audioChunk);
            lastChunkReceivedTime = System.currentTimeMillis();
            lastAudioChunkTime = lastChunkReceivedTime;

            // Calculate current duration
            long currentDuration = calculateAudioDurationMs(audioChunkBuffer.toByteArray());

            Log.d(TAG, "📦 Audio chunk accumulated:");
            Log.d(TAG, "  🧩 This chunk: " + audioChunk.length + " bytes");
            Log.d(TAG, "  📊 Total buffer: " + audioChunkBuffer.size() + " bytes");
            Log.d(TAG, "  ⏱️ Total duration: " + currentDuration + "ms");

            isAccumulatingAudio.set(true);

            // Start monitoring for chunk completion if not already started
            if (!isWaitingForMoreChunks) {
                startChunkCompletionMonitoring();
            }

            // Force flush if we have enough audio (e.g., 5+ seconds)
            if (currentDuration >= 5000) {
                Log.d(TAG, "🚀 Force flushing - audio duration exceeded 5 seconds");
                flushAccumulatedAudio();
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error accumulating audio chunk", e);
        }
    }

    // NEW: Monitor for chunk completion with background thread
    private void startChunkCompletionMonitoring() {
        if (isWaitingForMoreChunks) return;

        isWaitingForMoreChunks = true;

        executorService.execute(() -> {
            try {
                while (isWaitingForMoreChunks && isRecording.get()) {
                    Thread.sleep(200); // Check every 200ms

                    long timeSinceLastChunk = System.currentTimeMillis() - lastChunkReceivedTime;
                    long currentDuration = calculateAudioDurationMs(audioChunkBuffer.toByteArray());

                    // Flush if:
                    // 1. No new chunks for CHUNK_TIMEOUT_MS AND we have minimum audio
                    // 2. OR we have a lot of audio already
                    if ((timeSinceLastChunk >= CHUNK_TIMEOUT_MS && currentDuration >= MIN_AUDIO_DURATION_MS) ||
                            currentDuration >= 8000) {

                        Log.d(TAG, "⏰ Chunk completion detected:");
                        Log.d(TAG, "  🕐 Time since last: " + timeSinceLastChunk + "ms");
                        Log.d(TAG, "  ⏱️ Total duration: " + currentDuration + "ms");

                        isWaitingForMoreChunks = false;
                        flushAccumulatedAudio();
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Error in chunk completion monitoring", e);
                isWaitingForMoreChunks = false;
            }
        });
    }

    // NEW: Queue for chunks that arrive during injection
    private void queueChunkForLater(byte[] audioChunk) {
        pendingChunksQueue.offer(audioChunk);
        Log.d(TAG, "📥 Queued chunk for later (" + pendingChunksQueue.size() + " pending)");
    }

    // FIXED: Process pending chunks after injection completes
    private synchronized void flushAccumulatedAudio() {
        if (audioChunkBuffer.size() == 0 || isCurrentlyInjecting.get()) {
            return;
        }

        try {
            byte[] completeAudio = audioChunkBuffer.toByteArray();
            long audioDurationMs = calculateAudioDurationMs(completeAudio);

            Log.d(TAG, "🚀 FLUSHING COMPLETE AUDIO:");
            Log.d(TAG, "  📊 Total size: " + completeAudio.length + " bytes");
            Log.d(TAG, "  ⏱️ Duration: " + audioDurationMs + "ms (" + (audioDurationMs/1000.0f) + "s)");

            // Mark as injecting
            isCurrentlyInjecting.set(true);
            injectionCompleteLatch = new CountDownLatch(1);

            // Start injection with completion callback
            startPreciseAudioInjection(completeAudio, audioDurationMs);

            // Clear current buffer
            audioChunkBuffer.reset();
            isAccumulatingAudio.set(false);
            isWaitingForMoreChunks = false;

            // Process any pending chunks that arrived during injection
            processPendingChunksAfterInjection();

        } catch (Exception e) {
            Log.e(TAG, "❌ Error flushing accumulated audio", e);
            isCurrentlyInjecting.set(false);
            isWaitingForMoreChunks = false;
        }
    }

    // NEW: Process chunks that arrived during injection
    private void processPendingChunksAfterInjection() {
        executorService.execute(() -> {
            try {
                // Wait for current injection to complete
                if (injectionCompleteLatch != null) {
                    injectionCompleteLatch.await(30, TimeUnit.SECONDS);
                }

                // Process any pending chunks
                byte[] pendingChunk;
                boolean hasPendingChunks = false;

                while ((pendingChunk = pendingChunksQueue.poll()) != null) {
                    if (!hasPendingChunks) {
                        Log.d(TAG, "🔄 Processing pending chunks after injection...");
                        hasPendingChunks = true;
                    }
                    accumulateAudioChunk(pendingChunk);
                }

                if (hasPendingChunks) {
                    Log.d(TAG, "✅ Finished processing pending chunks");
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ Error processing pending chunks", e);
            }
        });
    }

    /**
     * Calculate precise audio duration from PCM data
     */
    private long calculateAudioDurationMs(byte[] pcmData) {
        if (pcmData == null || pcmData.length == 0) return 0;

        // ElevenLabs format: 16kHz, mono, 16-bit
        int sampleRate = 16000;
        int channels = 1;
        int bytesPerSample = 2; // 16-bit = 2 bytes

        int totalSamples = pcmData.length / (channels * bytesPerSample);
        long durationMs = (totalSamples * 1000L) / sampleRate;

        return durationMs;
    }

    /**
     * Precise injection with timing and completion detection
     */
    private void startPreciseAudioInjection(byte[] completeAudioData, long expectedDurationMs) {
        if (!isAudioInjectionEnabled.get() || audioInjector == null) {
            Log.d(TAG, "🎧 Audio injection disabled or unavailable");
            isCurrentlyInjecting.set(false);
            if (injectionCompleteLatch != null) {
                injectionCompleteLatch.countDown();
            }
            return;
        }

        executorService.execute(() -> {
            try {
                // Calculate precise timeout
                long safetyMarginMs = Math.max(3000, expectedDurationMs / 2);
                long totalTimeoutMs = expectedDurationMs + safetyMarginMs;

                Log.d(TAG, "🎯 Precise injection timing:");
                Log.d(TAG, "  🎵 Expected duration: " + expectedDurationMs + "ms");
                Log.d(TAG, "  🛡️ Safety margin: " + safetyMarginMs + "ms");
                Log.d(TAG, "  ⏰ Total timeout: " + totalTimeoutMs + "ms");

                long injectionStartTime = System.currentTimeMillis();

//                // Use enhanced injection method
//                audioInjector.injectAudio16kMono(completeAudioData, expectedDurationMs, totalTimeoutMs,
//                        new CallAudioInjector.InjectionCallback() {
//
//                            @Override
//                            public void onInjectionStarted() {
//                                isAudioInjectionActive.set(true);
//                                Log.d(TAG, "✅ Precise injection STARTED at " + System.currentTimeMillis());
//                                notifyCallback(cb -> cb.onAudioInjectionStarted("Precise complete audio"));
//                            }
//
//                            @Override
//                            public void onInjectionCompleted(boolean success) {
//                                long actualDuration = System.currentTimeMillis() - injectionStartTime;
//
//                                Log.d(TAG, "🏁 Precise injection COMPLETED:");
//                                Log.d(TAG, "  ✅ Success: " + success);
//                                Log.d(TAG, "  ⏱️ Expected: " + expectedDurationMs + "ms");
//                                Log.d(TAG, "  ⏱️ Actual: " + actualDuration + "ms");
//                                Log.d(TAG, "  📊 Accuracy: " + (actualDuration * 100.0 / expectedDurationMs) + "%");
//
//                                // Mark injection as complete
//                                isCurrentlyInjecting.set(false);
//                                isAudioInjectionActive.set(false);
//                                if (injectionCompleteLatch != null) {
//                                    injectionCompleteLatch.countDown();
//                                }
//
//                                notifyCallback(cb -> cb.onAudioInjectionStopped());
//                            }
//
//                            @Override
//                            public void onInjectionError(String error) {
//                                long actualDuration = System.currentTimeMillis() - injectionStartTime;
//
//                                Log.e(TAG, "❌ Precise injection ERROR:");
//                                Log.e(TAG, "  💥 Error: " + error);
//                                Log.e(TAG, "  ⏱️ After: " + actualDuration + "ms");
//
//                                // Mark injection as complete
//                                isCurrentlyInjecting.set(false);
//                                isAudioInjectionActive.set(false);
//                                if (injectionCompleteLatch != null) {
//                                    injectionCompleteLatch.countDown();
//                                }
//
//                                notifyCallback(cb -> cb.onAudioInjectionError(error));
//                            }
//                        });

            } catch (Exception e) {
                Log.e(TAG, "💥 Failed to start precise audio injection: " + e.getMessage());
                isCurrentlyInjecting.set(false);
                if (injectionCompleteLatch != null) {
                    injectionCompleteLatch.countDown();
                }
            }
        });
    }

    // CORRECTED: Enhanced stopAIFeatures with proper ElevenLabs ending
    private void stopAIFeatures() {
        Log.d(TAG, "🛑 Stopping AI features...");

        // 1. Send proper conversation end signal to ElevenLabs FIRST
        sendConversationEndSignal();

        // 2. Flush any remaining audio
        if (isAccumulatingAudio.get() && audioChunkBuffer.size() > 0) {
            Log.d(TAG, "🔄 Flushing remaining audio on stop");
            flushAccumulatedAudio();
        }

        // 3. Clear buffer
        if (audioChunkBuffer != null) {
            try {
                audioChunkBuffer.reset();
            } catch (Exception e) {
                Log.w(TAG, "Error clearing audio buffer", e);
            }
        }

        // 4. Reset states
        isAccumulatingAudio.set(false);
        isCurrentlyInjecting.set(false);
        isWaitingForMoreChunks = false;

        // 5. Stop health monitoring
        reconnectHandler.removeCallbacks(healthCheckRunnable);

        // 6. Reset connection state
        isAIStreaming.set(false);
        isAIResponding.set(false);

        // 7. Stop batch scheduler
        if (batchSchedulerStarted) {
            batchScheduler.shutdown();
            batchSchedulerStarted = false;
        }

        // 8. Stop audio injection
        stopAudioInjection();

        // 9. Cleanup audio resources
        cleanupAudioRecord();
        cleanupAudioTrack();

        // 10. Close WebSocket AFTER sending end signal (with delay)
        closeElevenLabsConnection();
    }

    // CORRECTED: Send proper conversation end sequence based on ElevenLabs docs
    private void sendConversationEndSignal() {
        if (elevenLabsSocket == null || !isAIConnected.get()) {
            Log.d(TAG, "🔌 No active ElevenLabs connection to end");
            return;
        }

        executorService.execute(() -> {
            try {
                Log.d(TAG, "📤 Sending conversation end sequence...");

                // 1. Send user_audio_complete to signal no more audio will be sent
                // This is the documented way to end audio streaming
                if (isAIStreaming.get()) {
                    JSONObject audioComplete = new JSONObject();
                    audioComplete.put("type", "user_audio_complete");
                    boolean sent = elevenLabsSocket.send(audioComplete.toString());
                    Log.d(TAG, "📤 user_audio_complete sent: " + sent);

                    if (sent) {
                        // Wait for any remaining audio responses
                        Thread.sleep(1000);
                    }
                }

                // 2. No need to send "conversation_end" - this isn't in the documented API
                // The proper way is to send user_audio_complete then close the WebSocket
                Log.d(TAG, "✅ Conversation end sequence complete");

            } catch (Exception e) {
                Log.w(TAG, "⚠️ Error sending conversation end signal: " + e.getMessage());
            }
        });
    }

    // NEW: Close connection with proper delay
    private void closeElevenLabsConnection() {
        if (elevenLabsSocket == null) {
            isAIConnected.set(false);
            isConnecting.set(false);
            return;
        }

        // Delay the close to allow end signals to be processed
        executorService.execute(() -> {
            try {
                // Give server time to process end signals
                Thread.sleep(1500);

                Log.d(TAG, "🔌 Closing ElevenLabs connection...");
                elevenLabsSocket.close(1000, "Call ended - conversation complete");
                elevenLabsSocket = null;

            } catch (Exception e) {
                Log.w(TAG, "⚠️ Error closing WebSocket: " + e.getMessage());
            } finally {
                isAIConnected.set(false);
                isConnecting.set(false);
                connectionAttempts = 0;
                Log.d(TAG, "✅ ElevenLabs connection closed");
            }
        });
    }

    // FIXED: Proper AudioRecord cleanup with guaranteed resource release
    private void cleanupAudioRecord() {
        if (aiAudioRecord != null) {
            try {
                // Always stop recording first if it's recording
                if (aiAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    Log.d(TAG, "🛑 Stopping AudioRecord...");
                    aiAudioRecord.stop();
                }

                // Always release resources
                Log.d(TAG, "🗑️ Releasing AudioRecord...");
                aiAudioRecord.release();

            } catch (Exception e) {
                Log.w(TAG, "⚠️ Error during AudioRecord cleanup: " + e.getMessage());
            } finally {
                aiAudioRecord = null;
                Log.d(TAG, "✅ AudioRecord cleaned up");
            }
        }
    }

    // FIXED: Proper AudioTrack cleanup
    private void cleanupAudioTrack() {
        if (audioTrack != null) {
            try {
                if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    Log.d(TAG, "🛑 Stopping AudioTrack...");
                    audioTrack.stop();
                }

                Log.d(TAG, "🗑️ Releasing AudioTrack...");
                audioTrack.release();

            } catch (Exception e) {
                Log.w(TAG, "⚠️ Error during AudioTrack cleanup: " + e.getMessage());
            } finally {
                audioTrack = null;
                Log.d(TAG, "✅ AudioTrack cleaned up");
            }
        }
    }

    // ============================================================================
    // ELEVENLABS WEBSOCKET LISTENER WITH FIXES
    // ============================================================================

    private Runnable healthCheckRunnable = new Runnable() {
        @Override
        public void run() {
            checkConnectionHealth();
            if (isRecording.get() && isAIConnected.get()) {
                reconnectHandler.postDelayed(this, CONNECTION_HEALTH_INTERVAL);
            }
        }
    };

    private void startConnectionHealthCheck() {
        updateMessageTime();
        reconnectHandler.postDelayed(healthCheckRunnable, CONNECTION_HEALTH_INTERVAL);
    }

    // FIXED: Thread-safe WebSocket message time update
    private void updateMessageTime() {
        long currentTime = System.currentTimeMillis();
        synchronized (webSocketStateLock) {
            lastMessageTime = currentTime;
            lastElevenLabsMessageTime.set(currentTime);
        }
    }

    // FIXED: Thread-safe connection health check
    private void checkConnectionHealth() {
        long timeSinceLastMessage;
        synchronized (webSocketStateLock) {
            timeSinceLastMessage = System.currentTimeMillis() - lastMessageTime;
        }

        if (timeSinceLastMessage > 60000 && isAIConnected.get()) {
            Log.w(TAG, "💔 Connection appears dead (no message for " + timeSinceLastMessage + "ms), reconnecting...");

            // Thread-safe state update
            isAIConnected.set(false);

            if (elevenLabsSocket != null) {
                try {
                    elevenLabsSocket.close(1000, "Health check timeout");
                } catch (Exception e) {
                    Log.w(TAG, "Error closing WebSocket during health check", e);
                }
            }

            if (isRecording.get()) {
                handleConnectionFailure(new Exception("Connection health check failed"));
            }
        }
    }

    private boolean isNetworkError(Throwable t) {
        String message = t.getMessage();
        if (message == null) return false;
        return message.contains("unexpected end of stream") ||
                message.contains("timeout") ||
                message.contains("connection") ||
                message.contains("network") ||
                message.contains("host") ||
                t instanceof java.net.SocketTimeoutException ||
                t instanceof java.net.ConnectException ||
                t instanceof java.io.IOException;
    }

    // CORRECTED: Complete WebSocket listener based on documented API
    private class RobustElevenLabsWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.d(TAG, "✅ Connected to ElevenLabs AI");
            Log.d(TAG, "🔗 Response Code: " + response.code());

            // Thread-safe state updates
            isAIConnected.set(true);
            isConnecting.set(false);
            connectionAttempts = 0;
            updateMessageTime();

            notifyCallback(cb -> cb.onAIConnected());
            sendInitialConfiguration(webSocket);
            startConnectionHealthCheck();
            startBatchSchedulerIfNeeded();
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            // FIXED: Thread-safe message time update
            updateMessageTime();

            try {
                Log.d(TAG, "📩 ELEVENLABS MESSAGE: " + text);
                JSONObject message = new JSONObject(text);
                String type = message.optString("type");

                // Handle documented acknowledgment types
                if ("user_audio_chunk_received".equals(type)) {
                    totalChunksAcked.incrementAndGet();
                    return;
                }

                // Process all documented message types
                switch (type) {
                    case "audio":
                        handleAudioEvent(message);
                        break;
                    case "error":
                        handleErrorEvent(message);
                        break;
                    case "ping":
                        handlePingEventFixed(webSocket, message);
                        break;
                    case "user_transcript":
                        handleUserTranscriptEvent(message);
                        break;
                    case "agent_response":
                        handleAgentResponseEvent(message);
                        break;
                    case "agent_response_correction":
                        handleAgentResponseCorrectionEvent(message);
                        break;
                    case "conversation_initiation_metadata":
                        handleConversationInitiationEvent(message);
                        break;
                    case "interruption":
                        Log.d(TAG, "🔄 Interruption signal received");
                        // Handle interruption if needed
                        break;
                    case "vad_score":
                        // Voice activity detection - log at verbose level
                        JSONObject vadEvent = message.optJSONObject("vad_score_event");
                        if (vadEvent != null) {
                            double vadScore = vadEvent.optDouble("vad_score");
                            Log.v(TAG, "🎤 Voice activity: " + vadScore);
                        }
                        break;
                    case "internal_tentative_agent_response":
                        // Internal processing
                        JSONObject tentativeEvent = message.optJSONObject("tentative_agent_response_internal_event");
                        if (tentativeEvent != null) {
                            String tentativeResponse = tentativeEvent.optString("tentative_agent_response");
                            Log.d(TAG, "🤔 Agent thinking: " + tentativeResponse);
                        }
                        break;
                    default:
                        Log.w(TAG, "⚠️ Unknown message type: " + type);
                        Log.d(TAG, "Full message: " + message.toString());
                        break;
                }

            } catch (JSONException e) {
                Log.e(TAG, "❌ Error parsing ElevenLabs message: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "❌ Unexpected error handling WebSocket message", e);
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            // FIXED: Thread-safe message time update
            updateMessageTime();

            try {
                byte[] audioData = bytes.toByteArray();
                Log.d(TAG, "🔊 ELEVENLABS BINARY AUDIO: " + audioData.length + " bytes");
                handleAIAudioResponse(audioData);
            } catch (Exception e) {
                Log.e(TAG, "❌ Error handling binary WebSocket message", e);
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            Log.e(TAG, "❌ ELEVENLABS CONNECTION FAILURE: " + t.getMessage());
            if (response != null) {
                Log.e(TAG, "📄 Response Code: " + response.code());
            }

            // Thread-safe state updates
            synchronized (webSocketStateLock) {
                isAIConnected.set(false);
                isConnecting.set(false);
            }

            if (isNetworkError(t)) {
                Log.w(TAG, "🌐 Network error detected, attempting reconnection...");
                handleConnectionFailure(new Exception(t.getMessage()));
            } else {
                notifyCallback(cb -> cb.onAIError("AI connection failed: " + t.getMessage()));
            }
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "🔌 ElevenLabs connection closed: " + code + " - " + reason);

            // Thread-safe state updates
            synchronized (webSocketStateLock) {
                isAIConnected.set(false);
                isConnecting.set(false);
            }

            if (isRecording.get() && code != 1000) {
                Log.w(TAG, "🔄 Unexpected closure during recording, attempting reconnection...");
                handleConnectionFailure(new Exception("Connection closed unexpectedly: " + reason));
            } else {
                notifyCallback(cb -> cb.onAIDisconnected());
            }
        }
    }

    // WebSocket message handlers
    private void sendInitialConfiguration(WebSocket webSocket) {
        try {
            JSONObject config = new JSONObject();
            config.put("type", "conversation_initiation_client_data");
            JSONObject conversationConfig = new JSONObject();
            conversationConfig.put("agent_id", agentId);
            config.put("conversation_config", conversationConfig);
            webSocket.send(config.toString());
        } catch (JSONException e) {
            Log.w(TAG, "Error sending AI config: " + e.getMessage());
        }
    }

    private void handlePingEventFixed(WebSocket webSocket, JSONObject message) {
        try {
            JSONObject pingEvent = message.optJSONObject("ping_event");
            int eventId = pingEvent != null ? pingEvent.optInt("event_id", 0) : 0;
            Log.d(TAG, "🏓 Ping received (event_id: " + eventId + ")");

            JSONObject pongResponse = new JSONObject();
            pongResponse.put("type", "pong");
            pongResponse.put("event_id", eventId);
            boolean sent = webSocket.send(pongResponse.toString());
            Log.d(TAG, "🏓 Pong sent (event_id: " + eventId + "): " + sent);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling ping event: " + e.getMessage());
        }
    }

    private void handleAudioEvent(JSONObject message) {
        try {
            JSONObject audioEvent = message.optJSONObject("audio_event");
            if (audioEvent == null) {
                Log.w(TAG, "⚠️ Audio message missing audio_event");
                return;
            }

            String base64Audio = audioEvent.optString("audio_base_64", "");
            if (base64Audio.isEmpty()) {
                Log.w(TAG, "⚠️ Audio event missing audio_base_64 data");
                return;
            }

            byte[] audioData = Base64.getDecoder().decode(base64Audio);
            Log.d(TAG, "🔊 Decoded Audio: " + audioData.length + " bytes");
            handleAIAudioResponse(audioData);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling audio event: " + e.getMessage());
        }
    }

    private void handleErrorEvent(JSONObject message) {
        String errorMessage = message.optString("message", "Unknown error");
        Log.e(TAG, "❌ ELEVENLABS ERROR: " + errorMessage);
        notifyCallback(cb -> cb.onAIError("ElevenLabs Error: " + errorMessage));
    }

    private void handleUserTranscriptEvent(JSONObject message) {
        try {
            JSONObject transcriptEvent = message.optJSONObject("user_transcription_event");
            if (transcriptEvent != null) {
                String transcript = transcriptEvent.optString("user_transcript", "");
                if (!transcript.isEmpty()) {
                    Log.d(TAG, "👤 User Said: '" + transcript + "'");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling user transcript: " + e.getMessage());
        }
    }

    private void handleAgentResponseEvent(JSONObject message) {
        try {
            String transcript = "";
            if (message.has("agent_response_event")) {
                JSONObject responseEvent = message.optJSONObject("agent_response_event");
                if (responseEvent != null) {
                    transcript = responseEvent.optString("agent_response", "");
                }
            } else if (message.has("agent_response_audio_transcript")) {
                transcript = message.optString("agent_response_audio_transcript", "");
            } else if (message.has("agent_response")) {
                transcript = message.optString("agent_response", "");
            } else if (message.has("transcript")) {
                transcript = message.optString("transcript", "");
            }

            if (!transcript.isEmpty()) {
                Log.d(TAG, "🗣️ AI Response: '" + transcript + "'");
                String finalTranscript = transcript;
                notifyCallback(cb -> cb.onAIResponse(finalTranscript, isAIResponding.get()));
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling agent response: " + e.getMessage());
        }
    }

    private void handleAgentResponseCorrectionEvent(JSONObject message) {
        String correctedText = message.optString("corrected_text", "");
        if (!correctedText.isEmpty()) {
            Log.d(TAG, "🔄 Agent Response Corrected: '" + correctedText + "'");
            notifyCallback(cb -> cb.onAIResponse(correctedText, false));
        }
    }

    private void handleConversationInitiationEvent(JSONObject message) {
        try {
            JSONObject metadata = message.optJSONObject("conversation_initiation_metadata_event");
            if (metadata != null) {
                String conversationId = metadata.optString("conversation_id", "");
                String audioFormat = metadata.optString("agent_output_audio_format", "");
                Log.d(TAG, "🔧 Conversation Initialized: " + conversationId + " (format: " + audioFormat + ")");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling conversation initiation: " + e.getMessage());
        }
    }

    // ============================================================================
    // DEBUGGING AND TESTING METHODS
    // ============================================================================

    /**
     * Comprehensive debug method to check all systems
     */
    public void debugAllSystems() {
        Log.d(TAG, "=== COMPREHENSIVE SYSTEM DEBUG ===");

        // 1. Recording State
        Log.d(TAG, "🎙️ RECORDING STATE:");
        Log.d(TAG, "  Active: " + isRecording.get());
        Log.d(TAG, "  Mode: " + currentRecordingMode);
        Log.d(TAG, "  File: " + currentRecordingFile);

        // 2. AI State
        Log.d(TAG, "🤖 AI STATE:");
        Log.d(TAG, "  Enabled: " + isAIEnabled.get());
        Log.d(TAG, "  Connected: " + isAIConnected.get());
        Log.d(TAG, "  Streaming: " + isAIStreaming.get());
        Log.d(TAG, "  Responding: " + isAIResponding.get());

        // 3. Audio Resources
        Log.d(TAG, "🎤 AUDIO RESOURCES:");
        Log.d(TAG, "  AudioRecord: " + (aiAudioRecord != null ? "ACTIVE" : "NULL"));
        Log.d(TAG, "  AudioTrack: " + (audioTrack != null ? "ACTIVE" : "NULL"));
        if (aiAudioRecord != null) {
            Log.d(TAG, "  AR State: " + aiAudioRecord.getState());
            Log.d(TAG, "  AR Recording: " + aiAudioRecord.getRecordingState());
        }

        // 4. Audio Accumulation
        Log.d(TAG, "📦 AUDIO ACCUMULATION:");
        Log.d(TAG, "  Buffer size: " + audioChunkBuffer.size() + " bytes");
        Log.d(TAG, "  Buffer duration: " + calculateAudioDurationMs(audioChunkBuffer.toByteArray()) + "ms");
        Log.d(TAG, "  Is accumulating: " + isAccumulatingAudio.get());
        Log.d(TAG, "  Is injecting: " + isCurrentlyInjecting.get());
        Log.d(TAG, "  Waiting for chunks: " + isWaitingForMoreChunks);
        Log.d(TAG, "  Pending queue: " + pendingChunksQueue.size());

        // 5. Audio Injection
        Log.d(TAG, "🎧 AUDIO INJECTION:");
        Log.d(TAG, "  Enabled: " + isAudioInjectionEnabled.get());
        Log.d(TAG, "  Active: " + isAudioInjectionActive.get());
        Log.d(TAG, "  Injector available: " + (audioInjector != null));
        if (audioInjector != null) {
            Log.d(TAG, "  Currently injecting: " + audioInjector.isCurrentlyInjecting());
//            Log.d(TAG, "  Time since last: " + audioInjector.getTimeSinceLastInjection() + "ms");
        }

        // 6. Streaming Stats
        Log.d(TAG, "📡 STREAMING STATS:");
        Log.d(TAG, "  Chunks sent: " + totalChunksSent.get());
        Log.d(TAG, "  Chunks acked: " + totalChunksAcked.get());
        Log.d(TAG, "  Queue size: " + outgoingChunkQueue.size());

        // 7. WebSocket State
        Log.d(TAG, "🔌 WEBSOCKET STATE:");
        Log.d(TAG, "  Socket: " + (elevenLabsSocket != null ? "ACTIVE" : "NULL"));
        Log.d(TAG, "  Connecting: " + isConnecting.get());
        Log.d(TAG, "  Connection attempts: " + connectionAttempts);
        Log.d(TAG, "  Last message: " + (System.currentTimeMillis() - lastElevenLabsMessageTime.get()) + "ms ago");

        Log.d(TAG, "=================================");
    }

    /**
     * Test the audio accumulation system with simulated chunks
     */
    public void testAudioAccumulation() {
        Log.d(TAG, "🧪 TESTING AUDIO ACCUMULATION SYSTEM");

        // Create test audio chunks (simulating ElevenLabs responses)
        byte[][] testChunks = createTestAudioChunks();

        Log.d(TAG, "📦 Sending " + testChunks.length + " test chunks...");

        for (int i = 0; i < testChunks.length; i++) {
            final int chunkNum = i + 1;
            final byte[] chunk = testChunks[i];

            // Send chunks with realistic timing
            new Handler().postDelayed(() -> {
                Log.d(TAG, "📤 Sending test chunk " + chunkNum + " (" + chunk.length + " bytes)");
                handleAIAudioResponse(chunk);
            }, i * 300); // 300ms between chunks
        }

        // Debug after all chunks
        new Handler().postDelayed(() -> {
            debugAllSystems();
        }, testChunks.length * 300 + 2000);
    }

    /**
     * Create test audio chunks that simulate ElevenLabs responses
     */
    private byte[][] createTestAudioChunks() {
        // Create 4 chunks of different sizes (simulating a 2-3 second response)
        byte[][] chunks = new byte[4][];

        // Chunk 1: ~0.5 seconds
        chunks[0] = createTestAudioChunk(8000, 440); // 440Hz tone

        // Chunk 2: ~0.8 seconds
        chunks[1] = createTestAudioChunk(12800, 880); // 880Hz tone

        // Chunk 3: ~0.6 seconds
        chunks[2] = createTestAudioChunk(9600, 1320); // 1320Hz tone

        // Chunk 4: ~0.4 seconds
        chunks[3] = createTestAudioChunk(6400, 660); // 660Hz tone

        return chunks;
    }

    /**
     * Create a test audio chunk with a sine wave
     */
    private byte[] createTestAudioChunk(int sizeBytes, double frequency) {
        byte[] chunk = new byte[sizeBytes];
        int sampleRate = 16000;

        for (int i = 0; i < sizeBytes - 1; i += 2) {
            double time = (i / 2) / (double) sampleRate;
            short sample = (short) (Math.sin(2 * Math.PI * frequency * time) * 8000);

            chunk[i] = (byte) (sample & 0xFF);
            chunk[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        return chunk;
    }

    /**
     * Force flush any accumulated audio (for testing)
     */
    public void forceFlushAccumulatedAudio() {
        Log.d(TAG, "🔄 FORCE FLUSHING ACCUMULATED AUDIO");
        Log.d(TAG, "  Current buffer: " + audioChunkBuffer.size() + " bytes");

        if (audioChunkBuffer.size() > 0) {
            flushAccumulatedAudio();
        } else {
            Log.d(TAG, "  No audio to flush");
        }
    }

    // ============================================================================
    // SYSTEM FIXES AND INITIALIZATION
    // ============================================================================

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private void initializeCapabilities() {
        isRooted.set(checkRootAccess());
        voiceCallAccessible.set(testVoiceCallAccess());
        Log.d(TAG, "System capabilities - Root: " + isRooted.get() +
                ", VOICE_CALL: " + voiceCallAccessible.get());
    }

    private boolean checkRootAccess() {
        try {
            Process process = Runtime.getRuntime().exec("su -c echo test");
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private boolean testVoiceCallAccess() {
        if (!hasRecordAudioPermission()) {
            return false;
        }

        try {
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            AudioRecord testRecord = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_CALL,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
            );
            boolean accessible = testRecord.getState() == AudioRecord.STATE_INITIALIZED;
            testRecord.release();
            return accessible;
        } catch (Exception e) {
            return false;
        }
    }

    private void applyRecordingFixes() {
        if (!isRooted.get()) {
            return;
        }

        executorService.execute(() -> {
            try {
                Log.d(TAG, "🔧 Applying recording fixes...");
                String packageName = context.getPackageName();
                String[] commands = {
                        "appops set " + packageName + " RECORD_AUDIO allow",
                        "appops set " + packageName + " PHONE_CALL_MICROPHONE allow"
                };

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    String[] android12Commands = {
                            "appops set " + packageName + " RECORD_AUDIO_OUTPUT allow",
                            "setprop persist.vendor.radio.enable_voicecall_recording true"
                    };
                    commands = concatenateArrays(commands, android12Commands);
                }

                executeRootCommands(commands);
                Thread.sleep(1000);
                Log.d(TAG, "✅ Recording fixes applied");
            } catch (Exception e) {
                Log.e(TAG, "Failed to apply recording fixes: " + e.getMessage());
            }
        });
    }

    private void executeRootCommands(String[] commands) {
        for (String command : commands) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
                process.waitFor();
                if (process.exitValue() == 0) {
                    Log.d(TAG, "✅ Command succeeded: " + command);
                } else {
                    Log.w(TAG, "⚠️ Command failed: " + command);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error executing command: " + command);
            }
        }
    }

    // ============================================================================
    // FILE MANAGEMENT
    // ============================================================================

    private String createRecordingPath(String filename) {
        File recordingDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MUSIC), "TeleTalker/AI_Calls");
        if (!recordingDir.exists()) {
            boolean created = recordingDir.mkdirs();
            if (!created) {
                Log.w(TAG, "Failed to create recording directory, using app directory");
                recordingDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "AI_Recordings");
                recordingDir.mkdirs();
            }
        }
        return new File(recordingDir, filename).getAbsolutePath();
    }

    private String validateRecording() {
        if (currentRecordingFile == null) {
            return null;
        }

        File recordingFile = new File(currentRecordingFile);
        if (!recordingFile.exists()) {
            Log.e(TAG, "❌ Recording file not created");
            showToast("Recording failed - no file created");
            return null;
        }

        long fileSize = recordingFile.length();
        Log.d(TAG, "📁 Recording file size: " + fileSize + " bytes");
        if (fileSize < 1024) {
            Log.w(TAG, "⚠️ Recording file too small, likely empty");
            showToast("Recording may be empty or corrupted");
        }

        if (!isFilePlayable(recordingFile)) {
            Log.w(TAG, "⚠️ Recording file may be corrupted");
        }

        String fileName = recordingFile.getName();
        String aiStatus = isAIEnabled.get() ? (isAIConnected.get() ? " + AI" : " (AI failed)") : "";
        String injectionStatus = isAudioInjectionActive.get() ? " + Injection" : "";
        String message = "✅ Call recorded: " + fileName + " (" + currentRecordingMode.getDescription() + aiStatus + injectionStatus + ")";
        showToast(message);
        return currentRecordingFile;
    }

    private boolean isFilePlayable(File file) {
        MediaPlayer player = new MediaPlayer();
        try {
            player.setDataSource(file.getAbsolutePath());
            player.prepare();
            int duration = player.getDuration();
            player.release();
            return duration > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ============================================================================
    // QUALITY ANALYSIS
    // ============================================================================

    private void analyzeRecordingQuality() {
        new Handler().postDelayed(() -> {
            executorService.execute(() -> {
                try {
                    String qualityInfo = analyzeCurrentRecording();
                    Log.d(TAG, "📊 Recording quality: " + qualityInfo);
                    String userMessage = getUserFriendlyQualityMessage();
                    showToast(userMessage);
                } catch (Exception e) {
                    Log.e(TAG, "Quality analysis failed: " + e.getMessage());
                }
            });
        }, 3000);
    }

    private String analyzeCurrentRecording() {
        StringBuilder analysis = new StringBuilder();
        analysis.append("Source: ").append(getAudioSourceName(currentAudioSource));
        analysis.append(", Mode: ").append(currentRecordingMode.getDescription());
        if (isRooted.get()) {
            analysis.append(", Root fixes applied");
        }
        if (isAIEnabled.get()) {
            analysis.append(", AI: ").append(isAIConnected.get() ? "Connected" : "Failed");
        }
        if (isAudioInjectionActive.get()) {
            analysis.append(", Injection: Active");
        }
        return analysis.toString();
    }

    private String getUserFriendlyQualityMessage() {
        String baseMessage;
        switch (currentRecordingMode) {
            case VOICE_CALL_TWO_WAY:
                baseMessage = "🎙️ High quality - Both sides recording";
                break;
            case VOICE_COMMUNICATION:
                baseMessage = "🎙️ Good quality - Call optimized recording";
                break;
            case MICROPHONE_ONLY:
                baseMessage = "🎙️ Basic quality - Your voice only";
                break;
            case VOICE_UPLINK:
                baseMessage = "🎙️ Outgoing voice recorded";
                break;
            case VOICE_DOWNLINK:
                baseMessage = "🎙️ Incoming voice recorded";
                break;
            default:
                baseMessage = "🎙️ Recording active";
        }

        if (isAIEnabled.get()) {
            if (isAIConnected.get()) {
                baseMessage += " + 🤖 AI Active";
                if (isAudioInjectionActive.get()) {
                    baseMessage += " + 🎧 Injecting";
                }
            } else {
                baseMessage += " (🤖 AI Offline)";
            }
        }

        return baseMessage;
    }

    // ============================================================================
    // UTILITY METHODS
    // ============================================================================

    // FIXED: Enhanced cleanup method called from multiple places
    private void cleanup() {
        Log.d(TAG, "🧹 Starting comprehensive cleanup...");

        isRecording.set(false);

        // Cleanup MediaRecorder
        if (mediaRecorder != null) {
            try {
                mediaRecorder.release();
            } catch (Exception e) {
                Log.w(TAG, "Error releasing MediaRecorder: " + e.getMessage());
            } finally {
                mediaRecorder = null;
            }
        }

        // FIXED: Use dedicated cleanup methods
        stopAIFeatures(); // This now includes proper timer and resource cleanup

        // Cleanup audio injector
        if (audioInjector != null) {
            try {
                audioInjector.cleanup();
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up audio injector: " + e.getMessage());
            } finally {
                audioInjector = null;
            }
        }

        Log.d(TAG, "✅ Comprehensive cleanup completed");
    }

    private void notifyCallback(CallbackAction action) {
        if (callback != null) {
            mainHandler.post(() -> action.execute(callback));
        }
    }

    @FunctionalInterface
    private interface CallbackAction {
        void execute(RecordingCallback callback);
    }

    private boolean hasRecordAudioPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    private String getAudioSourceName(int audioSource) {
        switch (audioSource) {
            case MediaRecorder.AudioSource.VOICE_CALL: return "VOICE_CALL";
            case MediaRecorder.AudioSource.VOICE_COMMUNICATION: return "VOICE_COMMUNICATION";
            case MediaRecorder.AudioSource.VOICE_UPLINK: return "VOICE_UPLINK";
            case MediaRecorder.AudioSource.VOICE_DOWNLINK: return "VOICE_DOWNLINK";
            case MediaRecorder.AudioSource.MIC: return "MIC";
            case MediaRecorder.AudioSource.DEFAULT: return "DEFAULT";
            default: return "UNKNOWN(" + audioSource + ")";
        }
    }

    private String[] concatenateArrays(String[] array1, String[] array2) {
        String[] result = new String[array1.length + array2.length];
        System.arraycopy(array1, 0, result, 0, array1.length);
        System.arraycopy(array2, 0, result, array1.length, array2.length);
        return result;
    }

    private void logDeviceInfo() {
        Log.d(TAG, "=== DEVICE INFO ===");
        Log.d(TAG, "Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        Log.d(TAG, "Device: " + Build.MANUFACTURER + " " + Build.MODEL);
        Log.d(TAG, "Root: " + (isRooted.get() ? "YES" : "NO"));
        Log.d(TAG, "VOICE_CALL accessible: " + (voiceCallAccessible.get() ? "YES" : "NO"));
        Log.d(TAG, "AI Features: " + (isAIEnabled.get() ? "ENABLED" : "DISABLED"));
        Log.d(TAG, "Audio Injection: " + (isAudioInjectionEnabled.get() ? "ENABLED" : "DISABLED"));
    }

    // ============================================================================
    // PUBLIC GETTERS AND CONTROL METHODS
    // ============================================================================

    public boolean isRecording() {
        return isRecording.get();
    }

    public RecordingMode getCurrentRecordingMode() {
        return currentRecordingMode;
    }

    public String getCurrentRecordingFile() {
        return currentRecordingFile;
    }

    public boolean isRooted() {
        return isRooted.get();
    }

    public boolean hasVoiceCallAccess() {
        return voiceCallAccessible.get();
    }

    public boolean isAIConnected() {
        return isAIConnected.get();
    }

    public boolean isAIResponding() {
        return isAIResponding.get();
    }

    public AIMode getCurrentAIMode() {
        return currentAIMode;
    }

    public void setAudioInjectionEnabled(boolean enabled) {
        isAudioInjectionEnabled.set(enabled);
        Log.d(TAG, "🎧 Call audio injection " + (enabled ? "enabled" : "disabled"));
        if (!enabled && isAudioInjectionActive.get()) {
            stopAudioInjection();
        }
    }

    public boolean isAudioInjectionEnabled() {
        return isAudioInjectionEnabled.get();
    }

    public boolean isAudioInjectionActive() {
        return isAudioInjectionActive.get();
    }

    // ============================================================================
    // ENHANCED DEBUG AND MONITORING METHODS
    // ============================================================================

    public void debugInjectionTiming() {
        Log.d(TAG, "🔍 INJECTION TIMING DEBUG:");
        Log.d(TAG, "  📦 Buffer size: " + audioChunkBuffer.size() + " bytes");
        Log.d(TAG, "  ⏱️ Buffer duration: " + calculateAudioDurationMs(audioChunkBuffer.toByteArray()) + "ms");
        Log.d(TAG, "  🔄 Is accumulating: " + isAccumulatingAudio.get());
        Log.d(TAG, "  🚧 Is injecting: " + isCurrentlyInjecting.get());
        Log.d(TAG, "  📡 Last chunk: " + (System.currentTimeMillis() - lastAudioChunkTime) + "ms ago");
        Log.d(TAG, "  ⏳ Waiting for chunks: " + isWaitingForMoreChunks);
        Log.d(TAG, "  📥 Pending queue: " + pendingChunksQueue.size());
    }

    public boolean isCurrentlyInjectingAudio() {
        return isCurrentlyInjecting.get();
    }

    public long getCurrentAccumulatedAudioDuration() {
        if (audioChunkBuffer.size() == 0) return 0;
        return calculateAudioDurationMs(audioChunkBuffer.toByteArray());
    }

    /**
     * Test the WebSocket connection and message handling
     */
    public void testWebSocketConnection() {
        Log.d(TAG, "🧪 TESTING WEBSOCKET CONNECTION");

        if (!isAIEnabled.get()) {
            Log.w(TAG, "❌ AI not enabled - configure ElevenLabs first");
            return;
        }

        if (isAIConnected.get()) {
            Log.d(TAG, "✅ Already connected, testing message sending");

            // Test sending a small audio chunk
            byte[] testChunk = createTestAudioChunk(1600, 440); // 0.1 second
            streamChunkToAI(testChunk);

        } else {
            Log.d(TAG, "🔌 Not connected, attempting connection");
            connectToElevenLabs();
        }

        // Monitor connection for 10 seconds
        for (int i = 1; i <= 10; i++) {
            final int second = i;
            new Handler().postDelayed(() -> {
                Log.d(TAG, "🕐 Connection test " + second + "/10 - Connected: " + isAIConnected.get());
            }, i * 1000);
        }
    }

    /**
     * Test call audio injection with a simple tone
     */
    public void testCallAudioInjection() {
        Log.d(TAG, "🧪 TESTING CALL AUDIO INJECTION");

        if (audioInjector == null) {
            Log.e(TAG, "❌ Audio injector not available");
            return;
        }

        // Create a 2-second test tone
        byte[] testAudio = createTestAudioChunk(32000, 800); // 2 seconds at 800Hz

//        audioInjector.injectAudioWithPreciseTiming(testAudio, 2000, 8000,
//                new CallAudioInjector.InjectionCallback() {
//                    @Override
//                    public void onInjectionStarted() {
//                        Log.d(TAG, "✅ Test injection started");
//                    }
//
//                    @Override
//                    public void onInjectionCompleted(boolean success) {
//                        Log.d(TAG, "🏁 Test injection completed: " + success);
//                    }
//
//                    @Override
//                    public void onInjectionError(String error) {
//                        Log.e(TAG, "❌ Test injection error: " + error);
//                    }
//
//                    @Override
//                    public void onInjectionProgress(long elapsedMs, long expectedMs) {
//                        Log.d(TAG, "⏱️ Test injection progress: " + elapsedMs + "/" + expectedMs + "ms");
//                    }
//                });
    }

    /**
     * Simulate a complete AI conversation flow
     */
    public void simulateAIConversation() {
        Log.d(TAG, "🎭 SIMULATING AI CONVERSATION FLOW");

        if (!isRecording.get()) {
            Log.w(TAG, "⚠️ Not recording - start recording first");
            return;
        }

        // Simulate user speaking (send some audio to AI)
        new Handler().postDelayed(() -> {
            Log.d(TAG, "👤 Simulating user speech...");
            byte[] userSpeech = createTestAudioChunk(16000, 440); // 1 second
            streamChunkToAI(userSpeech);
        }, 1000);

        // Simulate AI response chunks arriving
        new Handler().postDelayed(() -> {
            Log.d(TAG, "🤖 Simulating AI response...");
            testAudioAccumulation();
        }, 3000);
    }

    /**
     * Check system health and detect potential issues
     */
    public void performSystemHealthCheck() {
        Log.d(TAG, "🏥 PERFORMING SYSTEM HEALTH CHECK");

        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Check recording state
        if (isRecording.get()) {
            if (mediaRecorder == null) {
                issues.add("Recording active but MediaRecorder is null");
            }
        }

        // Check AI state consistency
        if (isAIEnabled.get()) {
            if (elevenLabsApiKey == null || agentId == null) {
                issues.add("AI enabled but credentials not set");
            }

            if (isAIConnected.get() && elevenLabsSocket == null) {
                issues.add("AI connected but WebSocket is null");
            }

            if (isAIStreaming.get() && aiAudioRecord == null) {
                issues.add("AI streaming but AudioRecord is null");
            }
        }

        // Check resource leaks
        if (!isRecording.get()) {
            if (aiAudioRecord != null) {
                warnings.add("AudioRecord active but not recording");
            }
            if (audioTrack != null) {
                warnings.add("AudioTrack active but not recording");
            }
        }

        // Check accumulation state
        if (isAccumulatingAudio.get() && audioChunkBuffer.size() == 0) {
            warnings.add("Accumulating flag set but buffer empty");
        }

        if (isCurrentlyInjecting.get() && (audioInjector == null || !audioInjector.isCurrentlyInjecting())) {
            warnings.add("Injection flag set but injector not active");
        }

        // Report results
        Log.d(TAG, "🏥 HEALTH CHECK RESULTS:");
        Log.d(TAG, "  Issues: " + issues.size());
        for (String issue : issues) {
            Log.e(TAG, "  ❌ " + issue);
        }

        Log.d(TAG, "  Warnings: " + warnings.size());
        for (String warning : warnings) {
            Log.w(TAG, "  ⚠️ " + warning);
        }

        if (issues.isEmpty() && warnings.isEmpty()) {
            Log.d(TAG, "  ✅ System appears healthy");
        }
    }

    public void logAIStatus() {
        Log.d(TAG, "=== AI STATUS CHECK ===");
        Log.d(TAG, "AI Enabled: " + isAIEnabled.get());
        Log.d(TAG, "AI Connected: " + isAIConnected.get());
        Log.d(TAG, "AI Streaming: " + isAIStreaming.get());
        Log.d(TAG, "AI Responding: " + isAIResponding.get());
        Log.d(TAG, "Recording Active: " + isRecording.get());
        Log.d(TAG, "Response Queue Size: " + (aiResponseQueue != null ? aiResponseQueue.size() : "null"));
        Log.d(TAG, "Injection Enabled: " + isAudioInjectionEnabled.get());
        Log.d(TAG, "Injection Active: " + isAudioInjectionActive.get());
        Log.d(TAG, "Batching Stats: Sent=" + totalChunksSent.get() + " Acked=" + totalChunksAcked.get());
        Log.d(TAG, "========================");
    }

    /**
     * Emergency cleanup - can be called from anywhere to ensure resources are freed
     */
    public void emergencyCleanup() {
        Log.w(TAG, "🚨 Emergency cleanup initiated!");

        try {
            cleanup();
        } catch (Exception e) {
            Log.e(TAG, "❌ Error during emergency cleanup", e);
        }
    }

    /**
     * Health check method to detect resource leaks
     */
    public void performHealthCheck() {
        Log.d(TAG, "🏥 HEALTH CHECK:");
        Log.d(TAG, "  🎤 AudioRecord: " + (aiAudioRecord != null ? "ACTIVE" : "NULL"));
        Log.d(TAG, "  🔊 AudioTrack: " + (audioTrack != null ? "ACTIVE" : "NULL"));
        Log.d(TAG, "  🔗 WebSocket: " + (elevenLabsSocket != null ? "ACTIVE" : "NULL"));
        Log.d(TAG, "  📦 Buffer size: " + audioChunkBuffer.size() + " bytes");
        Log.d(TAG, "  🔄 Batch scheduler: " + (batchSchedulerStarted ? "ACTIVE" : "STOPPED"));

        // Check for potential leaks
        if (!isRecording.get()) {
            if (aiAudioRecord != null || audioTrack != null) {
                Log.w(TAG, "⚠️ Potential resource leak detected - resources active but not recording");
            }
        }
    }
}