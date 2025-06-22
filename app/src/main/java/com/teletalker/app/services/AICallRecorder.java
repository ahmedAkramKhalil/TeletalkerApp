package com.teletalker.app.services;


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
 * Enhanced CallRecorder with ElevenLabs Conversational AI integration
 * Records calls while streaming audio to AI and injecting responses in real-time
 */
public class AICallRecorder {
    private static final String TAG = "AICallRecorder";

    // Audio configuration optimized for low latency
    private static final int SAMPLE_RATE = 16000; // ElevenLabs optimized
    private static final int CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BYTES_PER_SAMPLE = 2;

    // Streaming configuration for low latency
    private static final int CHUNK_SIZE_MS = 100; // 100ms chunks for low latency
    private static final int BUFFER_SIZE_MULTIPLIER = 4;
    private static final int MAX_QUEUE_SIZE = 10;

    // ElevenLabs Configuration
    private static final String ELEVENLABS_WS_URL = "wss://api.elevenlabs.io/v1/convai/conversation";

    // Audio sources prioritized for call recording
    private static final int[] AUDIO_SOURCES = {
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
    };

    // Recording modes
    public enum RecordingMode {
        VOICE_CALL_TWO_WAY("Two-way call + AI"),
        VOICE_COMMUNICATION("Call + AI (optimized)"),
        MICROPHONE_ONLY("Microphone + AI"),
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

    // Callback interfaces
    public interface RecordingCallback {
        void onRecordingStarted(RecordingMode mode);
        void onRecordingFailed(String reason);
        void onRecordingStopped(String filename, long duration);
        void onAIConnected();
        void onAIDisconnected();
        void onAIError(String error);
        void onAIResponse(String transcript, boolean isPlaying);
    }

    // Instance variables
    private Context context;
    private RecordingCallback callback;
    private ExecutorService executorService;
    private Handler mainHandler;

    // Recording components
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private FileOutputStream recordingFileStream;
    private String currentRecordingFile;
    private long recordingStartTime;

    // AI Integration
    private WebSocket elevenLabsSocket;
    private OkHttpClient httpClient;
    private String elevenLabsApiKey;
    private String agentId;
    private AIMode currentAIMode = AIMode.SMART_ASSISTANT;

    // State management
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private final AtomicBoolean isAIConnected = new AtomicBoolean(false);
    private final AtomicBoolean isStreamingToAI = new AtomicBoolean(false);
    private final AtomicBoolean isAIResponding = new AtomicBoolean(false);

    private RecordingMode currentRecordingMode = RecordingMode.FAILED;
    private int currentAudioSource;

    // Audio processing
    private ArrayBlockingQueue<byte[]> audioChunkQueue;
    private ArrayBlockingQueue<byte[]> aiResponseQueue;
    private int bufferSize;
    private int chunkSize;

    // Performance monitoring
    private long totalChunksProcessed = 0;
    private long totalLatency = 0;
    private long lastChunkTime = 0;

    public AICallRecorder(Context context) {
        this.context = context;
        this.executorService = Executors.newFixedThreadPool(4); // Multiple threads for real-time processing
        this.mainHandler = new Handler(Looper.getMainLooper());

        initializeAudioConfiguration();
        initializeHttpClient();
    }

    public void setCallback(RecordingCallback callback) {
        this.callback = callback;
    }

    public void setElevenLabsConfig(String apiKey, String agentId) {
        this.elevenLabsApiKey = apiKey;
        this.agentId = agentId;
    }

    public void setAIMode(AIMode mode) {
        this.currentAIMode = mode;
        Log.d(TAG, "AI Mode set to: " + mode.getDescription());
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

        if (elevenLabsApiKey == null || agentId == null) {
            notifyCallback(cb -> cb.onRecordingFailed("ElevenLabs API key and Agent ID required"));
            return false;
        }

        try {
            recordingStartTime = System.currentTimeMillis();
            currentRecordingFile = createRecordingPath(filename);

            Log.d(TAG, "🚀 Starting AI-enhanced call recording: " + filename);

            // Initialize audio recording
            if (!initializeAudioRecording()) {
                return false;
            }

            // Initialize audio playback for AI responses
            initializeAudioPlayback();

            // Setup file recording
            setupFileRecording();

            // Connect to ElevenLabs
            connectToElevenLabs();

            // Start processing threads
            startAudioProcessingThreads();

            isRecording.set(true);
            notifyCallback(cb -> cb.onRecordingStarted(currentRecordingMode));

            Log.d(TAG, "✅ AI-enhanced recording started: " + currentRecordingMode.getDescription());
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Exception starting AI recording: " + e.getMessage());
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
            Log.d(TAG, "🛑 Stopping AI-enhanced recording...");

            long recordingDuration = System.currentTimeMillis() - recordingStartTime;
            isRecording.set(false);
            isStreamingToAI.set(false);

            // Stop audio recording
            if (audioRecord != null && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop();
            }

            // Stop audio playback
            if (audioTrack != null && audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack.stop();
            }

            // Disconnect from ElevenLabs
            disconnectFromElevenLabs();

            // Close file recording
            if (recordingFileStream != null) {
                recordingFileStream.close();
                recordingFileStream = null;
            }

            // Log performance metrics
            logPerformanceMetrics();

            String finalFilename = validateRecording();
            notifyCallback(cb -> cb.onRecordingStopped(finalFilename, recordingDuration));

            Log.d(TAG, "✅ AI recording stopped. Duration: " + (recordingDuration / 1000) + "s");

        } catch (Exception e) {
            Log.e(TAG, "Error stopping AI recording: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    // ============================================================================
    // AUDIO INITIALIZATION
    // ============================================================================

    private void initializeAudioConfiguration() {
        chunkSize = (SAMPLE_RATE * CHUNK_SIZE_MS / 1000) * BYTES_PER_SAMPLE;
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT);

        if (bufferSize < chunkSize * BUFFER_SIZE_MULTIPLIER) {
            bufferSize = chunkSize * BUFFER_SIZE_MULTIPLIER;
        }

        audioChunkQueue = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE);
        aiResponseQueue = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE);

        Log.d(TAG, "Audio config - Buffer: " + bufferSize + ", Chunk: " + chunkSize);
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private boolean initializeAudioRecording() {
        for (int audioSource : AUDIO_SOURCES) {
            try {
                Log.d(TAG, "🧪 Trying audio source: " + getAudioSourceName(audioSource));

                audioRecord = new AudioRecord(
                        audioSource,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG_IN,
                        AUDIO_FORMAT,
                        bufferSize
                );

                if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    currentAudioSource = audioSource;
                    currentRecordingMode = getRecordingModeFromSource(audioSource);

                    Log.d(TAG, "✅ Audio recording initialized with " + getAudioSourceName(audioSource));
                    return true;
                }

                audioRecord.release();
                audioRecord = null;

            } catch (Exception e) {
                Log.w(TAG, "❌ Failed with " + getAudioSourceName(audioSource) + ": " + e.getMessage());
                if (audioRecord != null) {
                    audioRecord.release();
                    audioRecord = null;
                }
            }
        }

        currentRecordingMode = RecordingMode.FAILED;
        return false;
    }

    private void initializeAudioPlayback() {
        try {
            int playbackBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                    .build();

            AudioFormat audioFormat = new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG_OUT)
                    .setEncoding(AUDIO_FORMAT)
                    .build();

            audioTrack = new AudioTrack(
                    audioAttributes,
                    audioFormat,
                    playbackBufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
            );

            Log.d(TAG, "✅ Audio playback initialized");

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize audio playback: " + e.getMessage());
        }
    }

    private void initializeHttpClient() {
        httpClient = new OkHttpClient.Builder()
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    // ============================================================================
    // ELEVENLABS INTEGRATION
    // ============================================================================

    private void connectToElevenLabs() {
        if (elevenLabsApiKey == null || agentId == null) {
            Log.e(TAG, "ElevenLabs credentials not set");
            return;
        }

        executorService.execute(() -> {
            try {
                String wsUrl = ELEVENLABS_WS_URL + "?agent_id=" + agentId;

                Request request = new Request.Builder()
                        .url(wsUrl)
                        .addHeader("xi-api-key", elevenLabsApiKey)
                        .build();

                elevenLabsSocket = httpClient.newWebSocket(request, new ElevenLabsWebSocketListener());

                Log.d(TAG, "🔌 Connecting to ElevenLabs...");

            } catch (Exception e) {
                Log.e(TAG, "Failed to connect to ElevenLabs: " + e.getMessage());
                notifyCallback(cb -> cb.onAIError("Connection failed: " + e.getMessage()));
            }
        });
    }

    private void disconnectFromElevenLabs() {
        if (elevenLabsSocket != null) {
            isAIConnected.set(false);
            elevenLabsSocket.close(1000, "Recording stopped");
            elevenLabsSocket = null;
            Log.d(TAG, "🔌 Disconnected from ElevenLabs");
        }
    }

    private class ElevenLabsWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.d(TAG, "✅ Connected to ElevenLabs AI");
            isAIConnected.set(true);
            isStreamingToAI.set(true);
            notifyCallback(cb -> cb.onAIConnected());

            // Send initial configuration
            sendInitialConfiguration(webSocket);
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                JSONObject message = new JSONObject(text);
                String type = message.optString("type");

                switch (type) {
                    case "conversation_initiation_metadata":
                        handleConversationInit(message);
                        break;
                    case "agent_response":
                        handleAgentResponse(message);
                        break;
                    case "interruption":
                        handleInterruption(message);
                        break;
                    case "ping":
                        // Respond to ping to keep connection alive
                        sendPong(webSocket);
                        break;
                    default:
                        Log.d(TAG, "Unknown message type: " + type);
                }

            } catch (JSONException e) {
                Log.e(TAG, "Error parsing ElevenLabs message: " + e.getMessage());
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            // Handle binary audio response from AI
            handleAIAudioResponse(bytes.toByteArray());
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            Log.e(TAG, "ElevenLabs WebSocket failed: " + t.getMessage());
            isAIConnected.set(false);
            notifyCallback(cb -> cb.onAIError("Connection failed: " + t.getMessage()));
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "ElevenLabs WebSocket closed: " + reason);
            isAIConnected.set(false);
            notifyCallback(cb -> cb.onAIDisconnected());
        }
    }

    private void sendInitialConfiguration(WebSocket webSocket) {
        try {
            JSONObject config = new JSONObject();
            config.put("type", "conversation_initiation_client_data");

            JSONObject conversationConfig = new JSONObject();
            conversationConfig.put("agent_id", agentId);
            conversationConfig.put("override_agent_settings", new JSONObject()
                    .put("tts", new JSONObject()
                            .put("model_id", "eleven_turbo_v2_5")
                            .put("voice_settings", new JSONObject()
                                    .put("stability", 0.5)
                                    .put("similarity_boost", 0.8)
                                    .put("style", 0.2)
                            )
                    )
            );

            config.put("conversation_config", conversationConfig);

            webSocket.send(config.toString());
            Log.d(TAG, "📤 Sent initial configuration to ElevenLabs");

        } catch (JSONException e) {
            Log.e(TAG, "Error creating initial configuration: " + e.getMessage());
        }
    }

    private void sendPong(WebSocket webSocket) {
        try {
            JSONObject pong = new JSONObject();
            pong.put("type", "pong");
            webSocket.send(pong.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error sending pong: " + e.getMessage());
        }
    }

    private void handleConversationInit(JSONObject message) {
        Log.d(TAG, "🎯 Conversation initialized with ElevenLabs AI");
    }

    private void handleAgentResponse(JSONObject message) {
        try {
            String transcript = message.optString("agent_response_audio_transcript", "");

            if (!transcript.isEmpty()) {
                Log.d(TAG, "🤖 AI Response: " + transcript);
                notifyCallback(cb -> cb.onAIResponse(transcript, isAIResponding.get()));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling agent response: " + e.getMessage());
        }
    }

    private void handleInterruption(JSONObject message) {
        Log.d(TAG, "⚡ AI interrupted");
        // Stop current AI playback if any
        if (isAIResponding.get()) {
            isAIResponding.set(false);
            if (audioTrack != null && audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack.pause();
                audioTrack.flush();
            }
        }
    }

    private void handleAIAudioResponse(byte[] audioData) {
        if (!isRecording.get() || audioData == null || audioData.length == 0) {
            return;
        }

        // Add to response queue for playback
        if (!aiResponseQueue.offer(audioData)) {
            // Queue full, remove oldest and add new
            aiResponseQueue.poll();
            aiResponseQueue.offer(audioData);
        }

        // Start playback if not already playing
        if (!isAIResponding.get()) {
            isAIResponding.set(true);
            startAIAudioPlayback();
        }
    }

    // ============================================================================
    // AUDIO PROCESSING THREADS
    // ============================================================================

    private void startAudioProcessingThreads() {
        // Thread 1: Audio capture and streaming to AI
        executorService.execute(this::audioStreamingThread);

        // Thread 2: AI response playback
        executorService.execute(this::aiResponsePlaybackThread);

        // Thread 3: File recording
        executorService.execute(this::fileRecordingThread);

        // Thread 4: Performance monitoring
        executorService.execute(this::performanceMonitoringThread);
    }

    private void audioStreamingThread() {
        byte[] buffer = new byte[chunkSize];

        try {
            audioRecord.startRecording();
            Log.d(TAG, "🎙️ Audio streaming thread started");

            while (isRecording.get() && isStreamingToAI.get()) {
                int bytesRead = audioRecord.read(buffer, 0, buffer.length);

                if (bytesRead > 0) {
                    long chunkTime = System.currentTimeMillis();

                    // Clone buffer for processing
                    byte[] audioChunk = new byte[bytesRead];
                    System.arraycopy(buffer, 0, audioChunk, 0, bytesRead);

                    // Add to queue for file recording
                    if (!audioChunkQueue.offer(audioChunk)) {
                        audioChunkQueue.poll(); // Remove oldest if queue full
                        audioChunkQueue.offer(audioChunk);
                    }

                    // Stream to AI if connected
                    if (isAIConnected.get() && shouldStreamToAI()) {
                        streamChunkToAI(audioChunk);
                    }

                    // Update performance metrics
                    updateLatencyMetrics(chunkTime);
                    totalChunksProcessed++;
                    lastChunkTime = chunkTime;
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in audio streaming thread: " + e.getMessage());
        } finally {
            Log.d(TAG, "🎙️ Audio streaming thread stopped");
        }
    }

    private void streamChunkToAI(byte[] audioChunk) {
        if (elevenLabsSocket == null || !isAIConnected.get()) {
            return;
        }

        try {
            // Convert to base64 for streaming
            String base64Audio = Base64.getEncoder().encodeToString(audioChunk);

            JSONObject audioMessage = new JSONObject();
            audioMessage.put("type", "user_audio_chunk");
            audioMessage.put("chunk", base64Audio);

            elevenLabsSocket.send(audioMessage.toString());

        } catch (Exception e) {
            Log.e(TAG, "Error streaming audio to AI: " + e.getMessage());
        }
    }

    private boolean shouldStreamToAI() {
        // Logic to determine when to stream based on AI mode
        switch (currentAIMode) {
            case LISTEN_ONLY:
                return true; // Always listen but don't expect responses
            case RESPOND_TO_USER:
                // Only stream user's audio (would need voice activity detection)
                return true;
            case RESPOND_TO_CALLER:
                // Only stream caller's audio (would need voice activity detection)
                return true;
            case RESPOND_TO_BOTH:
            case SMART_ASSISTANT:
            default:
                return true;
        }
    }

    private void startAIAudioPlayback() {
        if (audioTrack != null) {
            try {
                audioTrack.play();
            } catch (Exception e) {
                Log.e(TAG, "Error starting AI audio playback: " + e.getMessage());
            }
        }
    }

    private void aiResponsePlaybackThread() {
        Log.d(TAG, "🔊 AI response playback thread started");

        while (isRecording.get()) {
            try {
                // Wait for AI response audio
                byte[] responseAudio = aiResponseQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);

                if (responseAudio != null && audioTrack != null) {
                    // Play AI response through call audio
                    int bytesWritten = audioTrack.write(responseAudio, 0, responseAudio.length);

                    if (bytesWritten < 0) {
                        Log.w(TAG, "Error writing AI audio to track: " + bytesWritten);
                    }
                }

                // Check if playback should stop
                if (aiResponseQueue.isEmpty() && isAIResponding.get()) {
                    isAIResponding.set(false);
                    if (audioTrack != null) {
                        audioTrack.pause();
                    }
                }

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                Log.e(TAG, "Error in AI playback thread: " + e.getMessage());
            }
        }

        Log.d(TAG, "🔊 AI response playback thread stopped");
    }

    private void setupFileRecording() throws IOException {
        recordingFileStream = new FileOutputStream(currentRecordingFile);
        Log.d(TAG, "📁 File recording setup: " + currentRecordingFile);
    }

    private void fileRecordingThread() {
        Log.d(TAG, "💾 File recording thread started");

        while (isRecording.get()) {
            try {
                byte[] audioChunk = audioChunkQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);

                if (audioChunk != null && recordingFileStream != null) {
                    recordingFileStream.write(audioChunk);
                }

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                Log.e(TAG, "Error in file recording thread: " + e.getMessage());
            }
        }

        Log.d(TAG, "💾 File recording thread stopped");
    }

    private void performanceMonitoringThread() {
        Log.d(TAG, "📊 Performance monitoring thread started");

        while (isRecording.get()) {
            try {
                Thread.sleep(5000); // Monitor every 5 seconds
                logCurrentPerformance();

            } catch (InterruptedException e) {
                break;
            }
        }

        Log.d(TAG, "📊 Performance monitoring thread stopped");
    }

    // ============================================================================
    // UTILITY METHODS
    // ============================================================================

    private void updateLatencyMetrics(long chunkTime) {
        if (lastChunkTime > 0) {
            long latency = chunkTime - lastChunkTime;
            totalLatency += latency;
        }
    }

    private void logCurrentPerformance() {
        if (totalChunksProcessed > 0) {
            long avgLatency = totalLatency / totalChunksProcessed;
            Log.d(TAG, "📊 Performance - Chunks: " + totalChunksProcessed +
                    ", Avg Latency: " + avgLatency + "ms" +
                    ", AI Connected: " + isAIConnected.get() +
                    ", AI Responding: " + isAIResponding.get());
        }
    }

    private void logPerformanceMetrics() {
        Log.d(TAG, "=== FINAL PERFORMANCE METRICS ===");
        Log.d(TAG, "Total chunks processed: " + totalChunksProcessed);
        if (totalChunksProcessed > 0) {
            Log.d(TAG, "Average latency: " + (totalLatency / totalChunksProcessed) + "ms");
        }
        Log.d(TAG, "AI connection uptime: " + isAIConnected.get());
    }

    private RecordingMode getRecordingModeFromSource(int audioSource) {
        switch (audioSource) {
            case MediaRecorder.AudioSource.VOICE_CALL:
                return RecordingMode.VOICE_CALL_TWO_WAY;
            case MediaRecorder.AudioSource.VOICE_COMMUNICATION:
                return RecordingMode.VOICE_COMMUNICATION;
            case MediaRecorder.AudioSource.MIC:
                return RecordingMode.MICROPHONE_ONLY;
            default:
                return RecordingMode.FAILED;
        }
    }

    private String createRecordingPath(String filename) {
        File recordingDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MUSIC), "TeleTalker/AI_Calls");

        if (!recordingDir.exists()) {
            recordingDir.mkdirs();
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
            return null;
        }

        long fileSize = recordingFile.length();
        Log.d(TAG, "📁 Recording file size: " + fileSize + " bytes");

        return currentRecordingFile;
    }

    private void cleanup() {
        isRecording.set(false);
        isStreamingToAI.set(false);
        isAIConnected.set(false);
        isAIResponding.set(false);

        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing AudioRecord: " + e.getMessage());
            }
            audioRecord = null;
        }

        if (audioTrack != null) {
            try {
                if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack.stop();
                }
                audioTrack.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing AudioTrack: " + e.getMessage());
            }
            audioTrack = null;
        }

        if (recordingFileStream != null) {
            try {
                recordingFileStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing file stream: " + e.getMessage());
            }
            recordingFileStream = null;
        }

        disconnectFromElevenLabs();

        if (audioChunkQueue != null) {
            audioChunkQueue.clear();
        }
        if (aiResponseQueue != null) {
            aiResponseQueue.clear();
        }
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

    private String getAudioSourceName(int audioSource) {
        switch (audioSource) {
            case MediaRecorder.AudioSource.VOICE_CALL: return "VOICE_CALL";
            case MediaRecorder.AudioSource.VOICE_COMMUNICATION: return "VOICE_COMMUNICATION";
            case MediaRecorder.AudioSource.MIC: return "MIC";
            default: return "UNKNOWN(" + audioSource + ")";
        }
    }

    // ============================================================================
    // PUBLIC GETTERS AND SETTERS
    // ============================================================================

    public boolean isRecording() { return isRecording.get(); }
    public boolean isAIConnected() { return isAIConnected.get(); }
    public boolean isAIResponding() { return isAIResponding.get(); }
    public RecordingMode getCurrentRecordingMode() { return currentRecordingMode; }
    public AIMode getCurrentAIMode() { return currentAIMode; }
    public String getCurrentRecordingFile() { return currentRecordingFile; }

    public long getTotalChunksProcessed() { return totalChunksProcessed; }
    public long getAverageLatency() {
        return totalChunksProcessed > 0 ? totalLatency / totalChunksProcessed : 0;
    }
}