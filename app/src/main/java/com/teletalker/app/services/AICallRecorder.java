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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
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

/**
 * Hybrid AICallRecorder - Uses proven MediaRecorder approach with optional AI features
 * Core recording ALWAYS works, AI features are completely optional
 */
public class AICallRecorder {
    private static final String TAG = "AICallRecorder";

    // Audio configuration - Same as working CallRecorder
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    // Audio sources - Same priority as working CallRecorder
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
    private static final int AI_SAMPLE_RATE = 16000; // Different sample rate for AI streaming

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
        RecordingMode(String description) {
            this.description = description;
        }
        public String getDescription() {
            return description;
        }
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

    // Callback interface
    public interface RecordingCallback {
        void onRecordingStarted(RecordingMode mode);
        void onRecordingFailed(String reason);
        void onRecordingStopped(String filename, long duration);
        void onAIConnected();
        void onAIDisconnected();
        void onAIError(String error);
        void onAIResponse(String transcript, boolean isPlaying);
    }

    // === CORE RECORDING COMPONENTS (Same as working CallRecorder) ===
    private Context context;
    private MediaRecorder mediaRecorder;  // Main recording - ALWAYS works
    private ExecutorService executorService;
    private Handler mainHandler;
    private RecordingCallback callback;

    // State tracking for core recording
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private String currentRecordingFile;
    private long recordingStartTime;
    private RecordingMode currentRecordingMode = RecordingMode.UNKNOWN;
    private int currentAudioSource;

    // System capabilities
    private final AtomicBoolean isRooted = new AtomicBoolean(false);
    private final AtomicBoolean voiceCallAccessible = new AtomicBoolean(false);

    // === AI COMPONENTS (Optional - Independent of core recording) ===
    private AudioRecord aiAudioRecord;  // Separate AudioRecord for AI streaming
    private AudioTrack audioTrack;      // For AI response playback
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

    public AICallRecorder(Context context) {
        this.context = context;
        this.executorService = Executors.newFixedThreadPool(3); // Reduced threads
        this.mainHandler = new Handler(Looper.getMainLooper());

        // Initialize core capabilities (same as CallRecorder)
        executorService.execute(this::initializeCapabilities);

        // Initialize AI components
        initializeAIComponents();
    }

    public void setCallback(RecordingCallback callback) {
        this.callback = callback;
    }

    public void setElevenLabsConfig(String apiKey, String agentId) {
        this.elevenLabsApiKey = apiKey;
        this.agentId = agentId;
        this.isAIEnabled.set(apiKey != null && agentId != null);

        Log.d(TAG, "ElevenLabs config - AI Enabled: " + isAIEnabled.get() +
                ", API Key: " + (apiKey != null ? "***set***" : "null") +
                ", Agent ID: " + agentId);
    }

    public void setAIMode(AIMode mode) {
        this.currentAIMode = mode;
        Log.d(TAG, "AI Mode set to: " + mode.getDescription());
    }

    // ============================================================================
    // MAIN RECORDING METHODS - Core functionality (Same as CallRecorder)
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

            Log.d(TAG, "🚀 Starting call recording: " + filename);
            Log.d(TAG, "AI Features: " + (isAIEnabled.get() ? "Enabled" : "Disabled"));
            logDeviceInfo();

            // Apply system fixes if needed (same as CallRecorder)
            if (isRooted.get()) {
                applyRecordingFixes();
            }

            // === CORE RECORDING (ALWAYS WORKS) ===
            boolean coreRecordingStarted = attemptRecordingWithBestSource();

            if (coreRecordingStarted) {
                isRecording.set(true);

                // === OPTIONAL AI FEATURES (Don't affect core recording) ===
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
            Log.d(TAG, "🛑 Stopping call recording...");

            long recordingDuration = System.currentTimeMillis() - recordingStartTime;
            isRecording.set(false);

            // Stop core recording (same as CallRecorder)
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            }

            // Stop AI features (independent of core recording)
            if (isAIEnabled.get()) {
                stopAIFeatures();
            }

            // Validate and finalize recording (same as CallRecorder)
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
    // CORE RECORDING IMPLEMENTATION - Exact same as working CallRecorder
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

//    private boolean attemptRecordingWithSource(int audioSource) {
//        try {
//            Log.d(TAG, "🧪 Trying audio source: " + getAudioSourceName(audioSource));
//
//            mediaRecorder = new MediaRecorder();
//            mediaRecorder.setAudioSource(audioSource);
//            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
//            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
//
//            // Configure based on audio source (same as CallRecorder)
//            configureRecordingSettings(audioSource);
//
//            mediaRecorder.setOutputFile(currentRecordingFile);
//
//            // Prepare and start
//            mediaRecorder.prepare();
//            Thread.sleep(200); // Brief delay for stability
//            mediaRecorder.start();
//
//            Log.d(TAG, "✅ Core recording started with " + getAudioSourceName(audioSource));
//            return true;
//
//        } catch (Exception e) {
//            Log.w(TAG, "❌ Failed with " + getAudioSourceName(audioSource) + ": " + e.getMessage());
//
//            if (mediaRecorder != null) {
//                try {
//                    mediaRecorder.reset();
//                    mediaRecorder.release();
//                } catch (Exception ignored) {}
//                mediaRecorder = null;
//            }
//
//            return false;
//        }
//    }

    private void configureRecordingSettings(int audioSource) {
        // Same configuration as working CallRecorder
        boolean isAndroid12Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;

        if (audioSource == MediaRecorder.AudioSource.VOICE_CALL && isAndroid12Plus) {
            // High quality stereo for two-way recording
            mediaRecorder.setAudioSamplingRate(48000);
            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setAudioChannels(2); // Stereo for both call sides
        } else if (audioSource == MediaRecorder.AudioSource.VOICE_COMMUNICATION) {
            // Optimized for call recording
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setAudioEncodingBitRate(96000);
            mediaRecorder.setAudioChannels(1); // Mono
        } else {
            // Default settings for other sources
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setAudioEncodingBitRate(64000);
            mediaRecorder.setAudioChannels(1); // Mono
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
    // AI FEATURES - Completely independent of core recording
    // ============================================================================

    private void initializeAIComponents() {
        try {
            // Initialize AI audio processing queue
            aiResponseQueue = new ArrayBlockingQueue<>(10);

            // Initialize HTTP client for WebSocket
            httpClient = new OkHttpClient.Builder()
                    .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                    .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)      // Added: Keep connection alive
                    .retryOnConnectionFailure(true)                               // Added: Auto retry on failures
                    .build();

            Log.d(TAG, "✅ AI components initialized");
        } catch (Exception e) {
            Log.e(TAG, "⚠️ Failed to initialize AI components: " + e.getMessage());
            // This is OK - core recording will still work
        }
    }

    private void startAIFeatures() {
        Log.d(TAG, "🤖 Starting AI features...");

        try {
            // Initialize AI audio streaming (separate from core recording)
            if (initializeAIAudioStreaming()) {
                // Initialize AI audio playback
                initializeAIAudioPlayback();

                // Connect to ElevenLabs
                connectToElevenLabs();

                // Start AI processing threads
                startAIThreads();

                Log.d(TAG, "✅ AI features started successfully");
            } else {
                Log.w(TAG, "⚠️ AI audio streaming failed - continuing with recording only");
                notifyCallback(cb -> cb.onAIError("AI audio streaming unavailable"));
            }
        } catch (Exception e) {
            Log.e(TAG, "⚠️ AI features failed to start: " + e.getMessage());
            notifyCallback(cb -> cb.onAIError("AI initialization failed: " + e.getMessage()));
            // Core recording continues regardless
        }
    }

    private boolean initializeAIAudioStreaming() {
        try {
            // Use separate AudioRecord for AI streaming (different sample rate)
            aiBufferSize = AudioRecord.getMinBufferSize(AI_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

            // Try to create AudioRecord for AI streaming using same source as core recording
            aiAudioRecord = new AudioRecord(
                    currentAudioSource,  // Use same source as successful core recording
                    AI_SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    aiBufferSize
            );

            if (aiAudioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                Log.d(TAG, "✅ AI audio streaming initialized");
                return true;
            } else {
                Log.w(TAG, "⚠️ AI audio streaming not available");
                if (aiAudioRecord != null) {
                    aiAudioRecord.release();
                    aiAudioRecord = null;
                }
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "⚠️ AI audio streaming failed: " + e.getMessage());
            if (aiAudioRecord != null) {
                aiAudioRecord.release();
                aiAudioRecord = null;
            }
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
            // This is OK - AI won't be able to inject responses but will still listen
        }
    }

//    private void connectToElevenLabs() {
//        if (elevenLabsApiKey == null || agentId == null) {
//            Log.w(TAG, "⚠️ ElevenLabs credentials not set");
//            return;
//        }
//
//        executorService.execute(() -> {
//            try {
//                Log.d(TAG, "🔌 Connecting to ElevenLabs...");
//                String wsUrl = ELEVENLABS_WS_URL + "?agent_id=" + agentId;
//
//                Request request = new Request.Builder()
//                        .url(wsUrl)
//                        .addHeader("xi-api-key", elevenLabsApiKey)
//                        .build();
//
//                elevenLabsSocket = httpClient.newWebSocket(request, new ElevenLabsWebSocketListener());
//
//            } catch (Exception e) {
//                Log.e(TAG, "⚠️ Failed to connect to ElevenLabs: " + e.getMessage());
//                notifyCallback(cb -> cb.onAIError("ElevenLabs connection failed: " + e.getMessage()));
//            }
//        });
//    }

    private void startAIThreads() {
        // Only start AI threads if AI audio is available
        if (aiAudioRecord != null) {
            executorService.execute(this::aiStreamingThread);
        }

        if (audioTrack != null) {
            executorService.execute(this::aiResponsePlaybackThread);
        }
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

            // 2. START SEPARATE AUDIORECORD FOR AI (using different source if needed)
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

    // NEW: Start AI audio capture with fallback sources
    private void startAIAudioCapture(int mainAudioSource) {
        Log.d(TAG, "🎤 Starting AI audio capture...");

        // Try different audio sources for AI (avoid conflict with main recording)
        int[] aiAudioSources = {
                MediaRecorder.AudioSource.MIC,                  // Try microphone first
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,  // Communication optimized
                mainAudioSource,                                // Same as main (might work)
                MediaRecorder.AudioSource.DEFAULT               // Default fallback
        };

        for (int aiSource : aiAudioSources) {
            if (initializeAIAudioWithSource(aiSource)) {
                Log.d(TAG, "✅ AI audio started with: " + getAudioSourceName(aiSource));
                return;
            }
        }

        Log.w(TAG, "⚠️ Could not start AI audio capture - continuing with recording only");
    }

    // NEW: Initialize AI AudioRecord with specific source
    private boolean initializeAIAudioWithSource(int audioSource) {
        try {
            aiBufferSize = AudioRecord.getMinBufferSize(AI_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

            aiAudioRecord = new AudioRecord(
                    audioSource,
                    AI_SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    aiBufferSize
            );

            if (aiAudioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                // Test if this source captures real audio
                aiAudioRecord.startRecording();

                byte[] testBuffer = new byte[320];
                int bytesRead = aiAudioRecord.read(testBuffer, 0, testBuffer.length);

                // Check if we got real audio (not silence)
                boolean hasRealAudio = false;
                if (bytesRead > 0) {
                    for (int i = 0; i < bytesRead - 1; i += 2) {
                        short sample = (short) ((testBuffer[i + 1] << 8) | (testBuffer[i] & 0xFF));
                        if (Math.abs(sample) > 100) { // Above noise threshold
                            hasRealAudio = true;
                            break;
                        }
                    }
                }

                if (hasRealAudio) {
                    Log.d(TAG, "🎯 AI audio source " + getAudioSourceName(audioSource) + " - HAS REAL AUDIO");
                    return true;
                } else {
                    Log.w(TAG, "⚠️ AI audio source " + getAudioSourceName(audioSource) + " - SILENCE ONLY");
                    aiAudioRecord.stop();
                    aiAudioRecord.release();
                    aiAudioRecord = null;
                    return false;
                }
            } else {
                Log.w(TAG, "❌ AI audio source " + getAudioSourceName(audioSource) + " - NOT AVAILABLE");
                if (aiAudioRecord != null) {
                    aiAudioRecord.release();
                    aiAudioRecord = null;
                }
                return false;
            }

        } catch (Exception e) {
            Log.w(TAG, "❌ AI audio source " + getAudioSourceName(audioSource) + " failed: " + e.getMessage());
            if (aiAudioRecord != null) {
                try {
                    aiAudioRecord.release();
                } catch (Exception ignored) {}
                aiAudioRecord = null;
            }
            return false;
        }
    }

    private boolean isAudioSilence(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return true;
        }

        int silenceThreshold = 100; // Amplitude threshold for "silence"
        int totalSamples = 0;
        int silentSamples = 0;

        // Process 16-bit PCM samples (2 bytes per sample)
        for (int i = 0; i < audioData.length - 1; i += 2) {
            // Convert 2 bytes to 16-bit signed integer (little-endian)
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));

            totalSamples++;

            // Check if sample amplitude is below silence threshold
            if (Math.abs(sample) <= silenceThreshold) {
                silentSamples++;
            }
        }

        if (totalSamples == 0) {
            return true;
        }

        // Consider it silence if more than 90% of samples are below threshold
        double silencePercentage = (double) silentSamples / totalSamples;
        return silencePercentage > 0.90;
    }



    private void aiStreamingThread() {
        if (aiAudioRecord == null) return;

        byte[] buffer = new byte[aiBufferSize / 4];
        int totalChunksSent = 0;
        int chunksWithRealAudio = 0;
        long lastLogTime = System.currentTimeMillis();

        try {
            // AudioRecord should already be started from initialization
            isAIStreaming.set(true);
            Log.d(TAG, "🤖 AI streaming thread started");

            while (isRecording.get() && isAIStreaming.get()) {
                try {
                    int bytesRead = aiAudioRecord.read(buffer, 0, buffer.length);

                    if (bytesRead > 0 && isAIConnected.get()) {
                        byte[] audioChunk = new byte[bytesRead];
                        System.arraycopy(buffer, 0, audioChunk, 0, bytesRead);

                        // Check if this chunk has real audio
                        boolean hasRealAudio = !isAudioSilence(audioChunk);
                        if (hasRealAudio) {
                            chunksWithRealAudio++;
                        }

                        streamChunkToAI(audioChunk);
                        totalChunksSent++;
                    }

                    // Enhanced periodic logging
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastLogTime >= 5000) {
                        int percentRealAudio = totalChunksSent > 0 ? (chunksWithRealAudio * 100 / totalChunksSent) : 0;

                        Log.d(TAG, "📡 AI Streaming Status:");
                        Log.d(TAG, "  📤 Total chunks sent: " + totalChunksSent);
                        Log.d(TAG, "  🎵 Chunks with real audio: " + chunksWithRealAudio + " (" + percentRealAudio + "%)");
                        Log.d(TAG, "  🔗 AI Connected: " + isAIConnected.get());

                        // Reset counters
                        totalChunksSent = 0;
                        chunksWithRealAudio = 0;
                        lastLogTime = currentTime;
                    }

                } catch (Exception e) {
                    Log.w(TAG, "AI streaming error: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "AI streaming thread error: " + e.getMessage());
        } finally {
            isAIStreaming.set(false);
            Log.d(TAG, "🤖 AI streaming thread stopped");
        }
    }

    // 5. Add this method to check AI status during call
    public void logAIStatus() {
        Log.d(TAG, "=== AI STATUS CHECK ===");
        Log.d(TAG, "AI Enabled: " + isAIEnabled.get());
        Log.d(TAG, "AI Connected: " + isAIConnected.get());
        Log.d(TAG, "AI Streaming: " + isAIStreaming.get());
        Log.d(TAG, "AI Responding: " + isAIResponding.get());
        Log.d(TAG, "Recording Active: " + isRecording.get());
        Log.d(TAG, "Response Queue Size: " + (aiResponseQueue != null ? aiResponseQueue.size() : "null"));
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

    private boolean firstAudioMessageLogged = false;

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

    /**
     * Handle connection failures with retry logic
     */
    private void handleConnectionFailure(Exception error) {
        isConnecting.set(false);
        isAIConnected.set(false);

        // Close failed socket
        if (elevenLabsSocket != null) {
            try {
                elevenLabsSocket.close(1000, "Reconnecting");
            } catch (Exception e) {
                // Ignore close errors
            }
            elevenLabsSocket = null;
        }

        // Retry if we haven't exceeded max attempts and recording is still active
        if (connectionAttempts < MAX_CONNECTION_ATTEMPTS && isRecording.get()) {
            long delay = RECONNECT_DELAY_MS * connectionAttempts; // Exponential backoff
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

    /**
     * Enhanced connection method with retry logic
     */
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

                // Wait for connection result with timeout
                waitForConnectionResult();

            } catch (Exception e) {
                Log.e(TAG, "❌ Connection attempt " + connectionAttempts + " failed: " + e.getMessage());
                handleConnectionFailure(e);
            }
        });
    }

    /**
     * Wait for connection to establish with timeout
     */
    private void waitForConnectionResult() {
        try {
            // Wait up to 15 seconds for connection
            for (int i = 0; i < 30; i++) {
                Thread.sleep(500);

                if (isAIConnected.get()) {
                    Log.d(TAG, "✅ Connected successfully on attempt " + connectionAttempts);
                    connectionAttempts = 0; // Reset on success
                    isConnecting.set(false);
                    return;
                }

                if (!isRecording.get()) {
                    Log.d(TAG, "🛑 Recording stopped, canceling connection");
                    isConnecting.set(false);
                    return;
                }
            }

            // Timeout reached
            Log.w(TAG, "⏰ Connection timeout on attempt " + connectionAttempts);
            handleConnectionFailure(new Exception("Connection timeout"));

        } catch (InterruptedException e) {
            Log.w(TAG, "🛑 Connection wait interrupted");
            isConnecting.set(false);
        }
    }

    /**
     * Connection health monitoring
     */
    private Runnable healthCheckRunnable = new Runnable() {
        @Override
        public void run() {
            checkConnectionHealth();
            if (isRecording.get() && isAIConnected.get()) {
                reconnectHandler.postDelayed(this, CONNECTION_HEALTH_INTERVAL);
            }
        }
    };

    /**
     * Start connection health monitoring
     */
    private void startConnectionHealthCheck() {
        lastMessageTime = System.currentTimeMillis();
        reconnectHandler.postDelayed(healthCheckRunnable, CONNECTION_HEALTH_INTERVAL);
    }

    /**
     * Check if connection is still healthy
     */
    private void checkConnectionHealth() {
        long timeSinceLastMessage = System.currentTimeMillis() - lastMessageTime;

        if (timeSinceLastMessage > 60000 && isAIConnected.get()) { // No message for 60 seconds
            Log.w(TAG, "💔 Connection appears dead, reconnecting...");

            // Force reconnection
            isAIConnected.set(false);
            if (elevenLabsSocket != null) {
                elevenLabsSocket.close(1000, "Health check timeout");
            }

            // Attempt reconnection
            if (isRecording.get()) {
                handleConnectionFailure(new Exception("Connection health check failed"));
            }
        }
    }

    // UPDATED: Use the new connection method in your existing connectToElevenLabs method
    private void connectToElevenLabs() {
        connectToElevenLabsWithRetry();
    }

    // REPLACE your streamChunkToAI method with this corrected version
    private void streamChunkToAI(byte[] audioChunk) {
        if (elevenLabsSocket == null || !isAIConnected.get()) {
            return;
        }

        try {
            String base64Audio = Base64.getEncoder().encodeToString(audioChunk);

            // ✅ CORRECT JSON STRUCTURE FOR ELEVENLABS
            JSONObject audioMessage = new JSONObject();
            audioMessage.put("type", "user_audio_chunk");
            audioMessage.put("audio_chunk", base64Audio);  // Fixed: "audio_chunk" not "chunk"

            // LOG THE FIRST MESSAGE ONLY (using class field, not static)
            if (!firstAudioMessageLogged) {
                Log.d(TAG, "✅ FIRST CORRECTED AUDIO MESSAGE:");
                Log.d(TAG, "📤 JSON Structure: {\"type\":\"user_audio_chunk\",\"audio_chunk\":\"BASE64...\"}");
                Log.d(TAG, "📤 Base64 Length: " + base64Audio.length());
                Log.d(TAG, "📤 Audio Chunk Size: " + audioChunk.length + " bytes");
                firstAudioMessageLogged = true;
            }

            boolean sent = elevenLabsSocket.send(audioMessage.toString());

            if (!sent) {
                Log.e(TAG, "❌ Failed to send audio chunk to WebSocket!");
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error streaming to AI: " + e.getMessage());
        }
    }
    private void stopAIFeatures() {
        Log.d(TAG, "🛑 Stopping AI features...");

        // Stop health monitoring
        reconnectHandler.removeCallbacks(healthCheckRunnable);

        // Reset connection state
        isAIStreaming.set(false);
        isAIResponding.set(false);
        isAIConnected.set(false);
        isConnecting.set(false);
        connectionAttempts = 0;

        // Stop audio components
        if (aiAudioRecord != null) {
            try {
                if (aiAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    aiAudioRecord.stop();
                }
                aiAudioRecord.release();
                aiAudioRecord = null;
            } catch (Exception e) {
                Log.w(TAG, "Error stopping AI audio: " + e.getMessage());
            }
        }

        if (audioTrack != null) {
            try {
                if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack.stop();
                }
                audioTrack.release();
                audioTrack = null;
            } catch (Exception e) {
                Log.w(TAG, "Error stopping AI playback: " + e.getMessage());
            }
        }

        // Close WebSocket properly
        if (elevenLabsSocket != null) {
            try {
                // Send end conversation message first
                JSONObject endMessage = new JSONObject();
                endMessage.put("type", "conversation_end");
                elevenLabsSocket.send(endMessage.toString());

                Thread.sleep(500); // Brief delay
            } catch (Exception e) {
                Log.w(TAG, "Error sending end message: " + e.getMessage());
            }

            elevenLabsSocket.close(1000, "Call ended normally");
            elevenLabsSocket = null;
            Log.d(TAG, "🔌 ElevenLabs connection properly closed");
        }
    }

    // ============================================================================
    // ELEVENLABS WEBSOCKET LISTENER
    // ============================================================================


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

    private void sendPong(WebSocket webSocket) {
        try {
            JSONObject pong = new JSONObject();
            pong.put("type", "pong");
            webSocket.send(pong.toString());
        } catch (JSONException e) {
            Log.w(TAG, "Error sending pong: " + e.getMessage());
        }
    }

//    private void handleAgentResponse(JSONObject message) {
//        try {
//            String transcript = message.optString("agent_response_audio_transcript", "");
//            if (!transcript.isEmpty()) {
//                // ENHANCE THIS LOGGING:
//                Log.d(TAG, "🤖 AI Response Received: '" + transcript + "'");
//                Log.d(TAG, "🔊 AI Speaking: " + (isAIResponding.get() ? "YES" : "NO"));
//                notifyCallback(cb -> cb.onAIResponse(transcript, isAIResponding.get()));
//            }
//
//            // ADD: Log other message types
//            String messageType = message.optString("type", "unknown");
//            Log.d(TAG, "📨 AI Message Type: " + messageType);
//
//        } catch (Exception e) {
//            Log.w(TAG, "Error handling AI response: " + e.getMessage());
//        }
//    }

    private void handleAIAudioResponse(byte[] audioData) {
        if (audioData != null && audioData.length > 0) {
            // ADD THIS LOGGING:
            Log.d(TAG, "🔊 AI Audio Response: " + audioData.length + " bytes");

            if (!aiResponseQueue.offer(audioData)) {
                Log.w(TAG, "⚠️ AI response queue full, dropping old audio");
                aiResponseQueue.poll();
                aiResponseQueue.offer(audioData);
            }
        }
    }

    // ============================================================================
    // SYSTEM FIXES AND INITIALIZATION - Same as working CallRecorder
    // ============================================================================

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private void initializeCapabilities() {
        // Check root access
        isRooted.set(checkRootAccess());

        // Test VOICE_CALL accessibility
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

                // Essential AppOps fixes
                String[] commands = {
                        "appops set " + packageName + " RECORD_AUDIO allow",
                        "appops set " + packageName + " PHONE_CALL_MICROPHONE allow"
                };

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+ specific fixes
                    String[] android12Commands = {
                            "appops set " + packageName + " RECORD_AUDIO_OUTPUT allow",
                            "setprop persist.vendor.radio.enable_voicecall_recording true"
                    };
                    commands = concatenateArrays(commands, android12Commands);
                }

                executeRootCommands(commands);

                // Brief delay for changes to take effect
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
    // FILE MANAGEMENT - Same as working CallRecorder
    // ============================================================================

    private String createRecordingPath(String filename) {
        // Create TeleTalker directory in Music/Downloads
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

        if (fileSize < 1024) { // Less than 1KB
            Log.w(TAG, "⚠️ Recording file too small, likely empty");
            showToast("Recording may be empty or corrupted");
            // Don't delete, let user decide
        }

        // Test if file is playable
        if (!isFilePlayable(recordingFile)) {
            Log.w(TAG, "⚠️ Recording file may be corrupted");
        }

        // Return filename only (not full path)
        String fileName = recordingFile.getName();
        String aiStatus = isAIEnabled.get() ? (isAIConnected.get() ? " + AI" : " (AI failed)") : "";
        String message = "✅ Call recorded: " + fileName + " (" + currentRecordingMode.getDescription() + aiStatus + ")";
        showToast(message);

        return currentRecordingFile; // Return full path for callback
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
    // QUALITY ANALYSIS - Same as working CallRecorder
    // ============================================================================

    private void analyzeRecordingQuality() {
        // Delay analysis to let recording stabilize
        new Handler().postDelayed(() -> {
            executorService.execute(() -> {
                try {
                    // Brief quality analysis
                    String qualityInfo = analyzeCurrentRecording();
                    Log.d(TAG, "📊 Recording quality: " + qualityInfo);

                    // Show user-friendly message
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
            } else {
                baseMessage += " (🤖 AI Offline)";
            }
        }

        return baseMessage;
    }

    // ============================================================================
    // UTILITY METHODS - Same as working CallRecorder
    // ============================================================================

    private void cleanup() {
        isRecording.set(false);

        if (mediaRecorder != null) {
            try {
                mediaRecorder.release();
            } catch (Exception ignored) {}
            mediaRecorder = null;
        }

        // Clean up AI components
        stopAIFeatures();
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
    }

    // ============================================================================
    // PUBLIC GETTERS
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

    // Enhanced ElevenLabsWebSocketListener with COMPLETE response logging
// COMPLETE ELEVENLABS WEBSOCKET MESSAGE HANDLING

    private int connectionAttempts = 0;
    private static final int MAX_CONNECTION_ATTEMPTS = 5;
    private static final long RECONNECT_DELAY_MS = 3000;
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private Handler reconnectHandler = new Handler(Looper.getMainLooper());

    // Connection health monitoring
    private long lastMessageTime = 0;
    private static final long CONNECTION_HEALTH_INTERVAL = 30000; // 30 seconds


    // Enhanced WebSocket listener with all ElevenLabs event types
    private class RobustElevenLabsWebSocketListener extends WebSocketListener {

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.d(TAG, "✅ Connected to ElevenLabs AI");
            Log.d(TAG, "🔗 Response Code: " + response.code());

            isAIConnected.set(true);
            isConnecting.set(false);
            connectionAttempts = 0;

            notifyCallback(cb -> cb.onAIConnected());
            sendInitialConfiguration(webSocket);
            startConnectionHealthCheck();
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            lastMessageTime = System.currentTimeMillis();

            try {
                Log.d(TAG, "═══════════════════════════════════════════════");
                Log.d(TAG, "📩 ELEVENLABS MESSAGE:");
                Log.d(TAG, text);
                Log.d(TAG, "═══════════════════════════════════════════════");

                JSONObject message = new JSONObject(text);
                String type = message.optString("type");

                switch (type) {
                    case "audio":
                        handleAudioEvent(message);
                        break;

                    case "error":
                        handleErrorEvent(message);
                        break;

                    case "ping":
                        handlePingEvent(webSocket, message);
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

                    case "client_tool_call":
                        handleClientToolCallEvent(message);
                        break;

                    case "conversation_initiation_metadata":
                        handleConversationInitiationEvent(message);
                        break;

                    default:
                        Log.w(TAG, "⚠️ Unknown message type: " + type);
                        Log.d(TAG, "📄 Full message: " + text);
                        break;
                }

            } catch (JSONException e) {
                Log.e(TAG, "❌ Error parsing ElevenLabs message: " + e.getMessage());
                Log.e(TAG, "📄 Problematic message: " + text);
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            lastMessageTime = System.currentTimeMillis();

            byte[] audioData = bytes.toByteArray();
            Log.d(TAG, "═══════════════════════════════════════════════");
            Log.d(TAG, "🔊 ELEVENLABS BINARY AUDIO:");
            Log.d(TAG, "📊 Size: " + audioData.length + " bytes");
            Log.d(TAG, "═══════════════════════════════════════════════");

            handleAIAudioResponse(audioData);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            Log.e(TAG, "═══════════════════════════════════════════════");
            Log.e(TAG, "❌ ELEVENLABS CONNECTION FAILURE:");
            Log.e(TAG, "💥 Error: " + t.getMessage());
            Log.e(TAG, "📋 Error Type: " + t.getClass().getSimpleName());

            if (response != null) {
                Log.e(TAG, "📄 Response Code: " + response.code());
                Log.e(TAG, "📄 Response Message: " + response.message());
            }
            Log.e(TAG, "═══════════════════════════════════════════════");

            if (isNetworkError(t)) {
                Log.w(TAG, "🌐 Network error detected, attempting reconnection...");
                handleConnectionFailure(new Exception(t.getMessage()));
            } else {
                isAIConnected.set(false);
                isConnecting.set(false);
                notifyCallback(cb -> cb.onAIError("AI connection failed: " + t.getMessage()));
            }
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "🔌 ElevenLabs connection closed: " + code + " - " + reason);
            isAIConnected.set(false);
            isConnecting.set(false);

            if (isRecording.get() && code != 1000) {
                Log.w(TAG, "🔄 Unexpected closure during recording, attempting reconnection...");
                handleConnectionFailure(new Exception("Connection closed unexpectedly: " + reason));
            } else {
                notifyCallback(cb -> cb.onAIDisconnected());
            }
        }
    }

// =================================================================
// INDIVIDUAL EVENT HANDLERS
// =================================================================

    /**
     * Handle audio events from ElevenLabs
     * Format: {"type": "audio", "audio_event": {"audio_base_64": "...", "event_id": 123}}
     */
    private void handleAudioEvent(JSONObject message) {
        try {
            Log.d(TAG, "🔊 Processing audio event...");

            JSONObject audioEvent = message.optJSONObject("audio_event");
            if (audioEvent == null) {
                Log.w(TAG, "⚠️ Audio message missing audio_event");
                return;
            }

            String base64Audio = audioEvent.optString("audio_base_64", "");
            int eventId = audioEvent.optInt("event_id", -1);

            if (base64Audio.isEmpty()) {
                Log.w(TAG, "⚠️ Audio event missing audio_base_64 data");
                return;
            }

            Log.d(TAG, "🔊 Audio Event ID: " + eventId);
            Log.d(TAG, "🔊 Base64 Audio Length: " + base64Audio.length() + " chars");

            // Decode base64 to audio bytes
            byte[] audioData = Base64.getDecoder().decode(base64Audio);
            Log.d(TAG, "🔊 Decoded Audio: " + audioData.length + " bytes");

            // Queue for playback
            handleAIAudioResponse(audioData);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling audio event: " + e.getMessage());
        }
    }

    /**
     * Handle error events from ElevenLabs
     * Format: {"type": "error", "message": "An error occurred: Invalid agent ID."}
     */
    private void handleErrorEvent(JSONObject message) {
        try {
            String errorMessage = message.optString("message", "Unknown error");
            String errorCode = message.optString("code", "");

            Log.e(TAG, "❌ ELEVENLABS ERROR:");
            Log.e(TAG, "💥 Message: " + errorMessage);
            if (!errorCode.isEmpty()) {
                Log.e(TAG, "🔢 Code: " + errorCode);
            }

            // Notify callback about the error
            notifyCallback(cb -> cb.onAIError("ElevenLabs Error: " + errorMessage));

            // If it's a critical error, close connection
            if (errorMessage.toLowerCase().contains("invalid") ||
                    errorMessage.toLowerCase().contains("unauthorized") ||
                    errorMessage.toLowerCase().contains("forbidden")) {
                Log.e(TAG, "💀 Critical error detected, closing connection");
                isAIConnected.set(false);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error processing error event: " + e.getMessage());
        }
    }

    /**
     * Handle ping events and send proper pong responses
     * Format: {"type": "ping", "ping_event": {"event_id": 2, "ping_ms": null}}
     */
    private void handlePingEvent(WebSocket webSocket, JSONObject message) {
        try {
            JSONObject pingEvent = message.optJSONObject("ping_event");
            int eventId = 0;

            if (pingEvent != null) {
                eventId = pingEvent.optInt("event_id", 0);
            }

            Log.d(TAG, "🏓 Ping received (event_id: " + eventId + ")");

            // Send proper pong response
            JSONObject pongResponse = new JSONObject();
            pongResponse.put("type", "pong");
            pongResponse.put("event_id", eventId);

            boolean sent = webSocket.send(pongResponse.toString());
            Log.d(TAG, "🏓 Pong sent (event_id: " + eventId + "): " + sent);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling ping event: " + e.getMessage());
        }
    }

    /**
     * Handle user transcript events (finalized speech-to-text results)
     * This tells you what the user actually said
     */
    private void handleUserTranscriptEvent(JSONObject message) {
        try {
            String transcript = message.optString("transcript", "");
            boolean isFinal = message.optBoolean("is_final", false);

            if (!transcript.isEmpty()) {
                Log.d(TAG, "👤 User Said: '" + transcript + "' (final: " + isFinal + ")");

                // You can use this to show what the user said in your UI
                // notifyCallback(cb -> cb.onUserTranscript(transcript, isFinal));
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling user transcript: " + e.getMessage());
        }
    }

    /**
     * Handle agent response events (full text of what the agent is saying)
     */
    private void handleAgentResponseEvent(JSONObject message) {
        try {
            Log.d(TAG, "🤖 Processing agent response event...");

            String transcript = "";

            // Try different possible field names for the response text
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
                Log.d(TAG, "🔊 AI Currently Speaking: " + isAIResponding.get());

                // Notify your app about the AI response
                String finalTranscript = transcript;
                notifyCallback(cb -> cb.onAIResponse(finalTranscript, isAIResponding.get()));
            } else {
                Log.w(TAG, "⚠️ Agent response event has no transcript");
                Log.d(TAG, "📄 Full message: " + message.toString());
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling agent response: " + e.getMessage());
        }
    }

    /**
     * Handle agent response correction events
     * This happens when the agent's response gets cut off due to user interruption
     */
    private void handleAgentResponseCorrectionEvent(JSONObject message) {
        try {
            String correctedText = message.optString("corrected_text", "");
            String originalText = message.optString("original_text", "");

            Log.d(TAG, "🔄 Agent Response Correction:");
            Log.d(TAG, "  📝 Original: '" + originalText + "'");
            Log.d(TAG, "  ✏️ Corrected: '" + correctedText + "'");

            // You might want to update your UI to show the corrected response
            if (!correctedText.isEmpty()) {
                notifyCallback(cb -> cb.onAIResponse(correctedText, false));
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling response correction: " + e.getMessage());
        }
    }

    /**
     * Handle client tool call events
     * This is when the AI agent needs your app to perform some action
     */
    private void handleClientToolCallEvent(JSONObject message) {
        try {
            String toolName = message.optString("tool_name", "");
            JSONObject parameters = message.optJSONObject("parameters");
            String callId = message.optString("call_id", "");

            Log.d(TAG, "🔧 Client Tool Call:");
            Log.d(TAG, "  🛠️ Tool: " + toolName);
            Log.d(TAG, "  📋 Call ID: " + callId);
            Log.d(TAG, "  ⚙️ Parameters: " + (parameters != null ? parameters.toString() : "none"));

            // Handle different tool calls
            handleToolCall(toolName, parameters, callId);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling tool call: " + e.getMessage());
        }
    }

    /**
     * Handle conversation initiation metadata
     */
    private void handleConversationInitiationEvent(JSONObject message) {
        try {
            JSONObject metadata = message.optJSONObject("conversation_initiation_metadata_event");
            if (metadata != null) {
                String conversationId = metadata.optString("conversation_id", "");
                String agentOutputFormat = metadata.optString("agent_output_audio_format", "");
                String userInputFormat = metadata.optString("user_input_audio_format", "");

                Log.d(TAG, "🔧 Conversation Initialized:");
                Log.d(TAG, "  🆔 Conversation ID: " + conversationId);
                Log.d(TAG, "  🔊 Agent Output Format: " + agentOutputFormat);
                Log.d(TAG, "  🎤 User Input Format: " + userInputFormat);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling conversation initiation: " + e.getMessage());
        }
    }

    /**
     * Handle tool calls that the AI agent requests
     */
    private void handleToolCall(String toolName, JSONObject parameters, String callId) {
        try {
            Log.d(TAG, "🔧 Executing tool: " + toolName);

            // Example tool implementations
            switch (toolName) {
                case "get_weather":
                    handleWeatherTool(parameters, callId);
                    break;

                case "send_message":
                    handleSendMessageTool(parameters, callId);
                    break;

                case "get_time":
                    handleGetTimeTool(parameters, callId);
                    break;

                default:
                    Log.w(TAG, "⚠️ Unknown tool requested: " + toolName);
                    sendToolResult(callId, false, "Tool not supported: " + toolName);
                    break;
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error executing tool: " + e.getMessage());
            sendToolResult(callId, false, "Tool execution error: " + e.getMessage());
        }
    }

    /**
     * Example tool: Get weather information
     */
    private void handleWeatherTool(JSONObject parameters, String callId) {
        try {
            String location = parameters != null ? parameters.optString("location", "unknown") : "unknown";
            Log.d(TAG, "🌤️ Getting weather for: " + location);

            // In a real app, you'd make an API call here
            String weatherResult = "The weather in " + location + " is sunny, 22°C";

            sendToolResult(callId, true, weatherResult);

        } catch (Exception e) {
            sendToolResult(callId, false, "Weather lookup failed: " + e.getMessage());
        }
    }

    /**
     * Example tool: Send a message
     */
    private void handleSendMessageTool(JSONObject parameters, String callId) {
        try {
            String message = parameters != null ? parameters.optString("message", "") : "";
            String recipient = parameters != null ? parameters.optString("recipient", "") : "";

            Log.d(TAG, "📱 Sending message to " + recipient + ": " + message);

            // In a real app, you'd send the message here
            String result = "Message sent to " + recipient;

            sendToolResult(callId, true, result);

        } catch (Exception e) {
            sendToolResult(callId, false, "Message sending failed: " + e.getMessage());
        }
    }

    /**
     * Example tool: Get current time
     */
    private void handleGetTimeTool(JSONObject parameters, String callId) {
        try {
            String timezone = parameters != null ? parameters.optString("timezone", "local") : "local";

            // Get current time
            String currentTime = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(new java.util.Date());

            String result = "Current time is " + currentTime;
            Log.d(TAG, "⏰ " + result);

            sendToolResult(callId, true, result);

        } catch (Exception e) {
            sendToolResult(callId, false, "Time lookup failed: " + e.getMessage());
        }
    }

    /**
     * Send tool execution result back to ElevenLabs
     */
    private void sendToolResult(String callId, boolean success, String result) {
        if (elevenLabsSocket == null || !isAIConnected.get()) {
            Log.w(TAG, "⚠️ Cannot send tool result - not connected");
            return;
        }

        try {
            JSONObject toolResult = new JSONObject();
            toolResult.put("type", "client_tool_result");
            toolResult.put("call_id", callId);
            toolResult.put("result", result);
            toolResult.put("success", success);

            boolean sent = elevenLabsSocket.send(toolResult.toString());
            Log.d(TAG, "🔧 Tool result sent: " + sent + " (success: " + success + ")");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error sending tool result: " + e.getMessage());
        }
    }
    // Helper method to convert bytes to hex for logging
    private String bytesToHex(byte[] bytes, int maxBytes) {
        if (bytes == null || bytes.length == 0) return "empty";

        int length = Math.min(bytes.length, maxBytes);
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < length; i++) {
            hex.append(String.format("%02X ", bytes[i]));
        }
        if (bytes.length > maxBytes) {
            hex.append("...");
        }
        return hex.toString();
    }

    // Enhanced handleAgentResponse with complete field logging
    private void handleAgentResponse(JSONObject message) {
        try {
            Log.d(TAG, "🎯 PROCESSING AGENT RESPONSE:");

            // Check for transcript
            String transcript = message.optString("agent_response_audio_transcript", "");
            if (!transcript.isEmpty()) {
                Log.d(TAG, "🗣️ AI Transcript: '" + transcript + "'");
                notifyCallback(cb -> cb.onAIResponse(transcript, isAIResponding.get()));
            } else {
                Log.w(TAG, "⚠️ No transcript in response");
            }

            // Check for other common ElevenLabs fields
            String agentId = message.optString("agent_id", "");
            String conversationId = message.optString("conversation_id", "");
            String audioFormat = message.optString("audio_format", "");
            boolean isFinal = message.optBoolean("is_final", false);

            Log.d(TAG, "🆔 Agent ID: " + agentId);
            Log.d(TAG, "💬 Conversation ID: " + conversationId);
            Log.d(TAG, "🎵 Audio Format: " + audioFormat);
            Log.d(TAG, "✅ Is Final: " + isFinal);

            // Check for audio data in the text response
            boolean hasAudioInText = message.has("audio") || message.has("audio_data") || message.has("audio_base64");
            Log.d(TAG, "🔊 Contains Audio Data: " + hasAudioInText);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error processing agent response: " + e.getMessage());
        }
    }
}