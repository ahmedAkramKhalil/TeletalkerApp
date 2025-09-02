package com.teletalker.app.services.ai;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.WebSocket;

// FIXED: OptimizedAudioStreamer with single WebSocket connection support
public class OptimizedAudioStreamer {
    private static final String TAG = "OptimizedStreamer";

    // Optimized audio parameters
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNELS = 1;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int CHUNK_DURATION_MS = 100;
    private static final int CHUNK_SIZE_BYTES = (SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8) * CHUNK_DURATION_MS / 1000;

    // Streaming parameters
    private static final int MAX_QUEUE_SIZE = 50;
    private static final long STREAM_TIMEOUT_MS = 30000;

    public interface StreamerCallback {
        void onStreamingStarted();
        void onStreamingStopped();
        void onChunkSent(int chunkSize, boolean hasVoice);
        void onStreamingError(String error);
        void onQueueOverflow(int droppedChunks);
        void onConnectionHealthChanged(boolean healthy);
    }

    private final Context context;
    private final EnhancedAudioProcessor audioProcessor;
    private final ExecutorService streamingExecutor;
    private final Handler mainHandler;

    // Audio capture
    private AudioRecord audioRecord;
    private final AtomicBoolean isStreaming = new AtomicBoolean(false);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);

    // Chunk management
    private final BlockingQueue<AudioChunkData> chunkQueue = new LinkedBlockingQueue<>();
    private final AtomicInteger droppedChunks = new AtomicInteger(0);
    private final AtomicLong totalChunksSent = new AtomicLong(0);
    private final AtomicLong chunksWithVoice = new AtomicLong(0);

    // FIXED: External WebSocket connection management
    private WebSocket externalWebSocket;
    private volatile boolean connectionHealthy = false;
    private long lastSuccessfulSend = 0;

    private StreamerCallback callback;

    public OptimizedAudioStreamer(Context context) {
        this.context = context;
        this.audioProcessor = new EnhancedAudioProcessor();
        this.streamingExecutor = Executors.newFixedThreadPool(2); // REDUCED: Only 2 threads needed
        this.mainHandler = new Handler(Looper.getMainLooper());

        setupAudioProcessor();
    }

    public void setCallback(StreamerCallback callback) {
        this.callback = callback;
    }

    public StreamerCallback getCallback() {
        return this.callback;
    }

    // FIXED: Set external WebSocket (prevents duplicate connections)
    public void setExistingWebSocket(WebSocket webSocket) {
        this.externalWebSocket = webSocket;
        this.connectionHealthy = (webSocket != null);

        Log.d(TAG, "External WebSocket set: " + (webSocket != null ? "AVAILABLE" : "NULL"));

        if (callback != null) {
            callback.onConnectionHealthChanged(connectionHealthy);
        }
    }

    private void setupAudioProcessor() {
        audioProcessor.setCallback(new EnhancedAudioProcessor.AudioProcessorCallback() {
            @Override
            public void onSpeechStarted() {
                Log.v(TAG, "Speech detection: STARTED");
            }

            @Override
            public void onSpeechEnded(long durationMs) {
                Log.v(TAG, "Speech detection: ENDED (" + durationMs + "ms)");
            }

            @Override
            public void onSilenceDetected(long durationMs) {
                // Could reduce streaming rate during long silence
            }

            @Override
            public void onAudioQualityChanged(EnhancedAudioProcessor.AudioQuality quality) {
                Log.v(TAG, "Audio quality: " + quality.level + " - " + quality.reason);
            }

            @Override
            public void onChunkProcessed(EnhancedAudioProcessor.AudioChunk chunk, boolean hasVoice) {
                queueAudioChunk(chunk, hasVoice);
            }
        });
    }

    // FIXED: New method for audio capture only (no connection creation)
    public void startAudioCaptureOnly(StreamerCallback callback) {
        this.callback = callback;

        Log.d(TAG, "Starting audio capture only (using external WebSocket)...");

        // Check if external WebSocket is available
        if (externalWebSocket == null) {
            Log.e(TAG, "No external WebSocket provided");
            if (callback != null) {
                callback.onStreamingError("No external WebSocket connection available");
            }
            return;
        }

        // Check permissions
        if (!checkAudioPermission()) {
            if (callback != null) {
                callback.onStreamingError("Audio permission not granted");
            }
            return;
        }

        // Initialize audio capture
        if (!initializeAudioCapture()) {
            if (callback != null) {
                callback.onStreamingError("Failed to initialize audio capture");
            }
            return;
        }

        // Start streaming threads
        isStreaming.set(true);
        startAudioCaptureThread();
        startStreamingThread();

        if (callback != null) {
            callback.onStreamingStarted();
        }

        Log.d(TAG, "Audio capture started successfully with external WebSocket");
    }

    // DEPRECATED: Old method (creates duplicate connections - use startAudioCaptureOnly instead)
    @Deprecated
    public void startStreaming(String wsUrl, String apiKey, String agentId, StreamerCallback callback) {
        Log.w(TAG, "WARNING: startStreaming() creates duplicate WebSocket connections!");
        Log.w(TAG, "Use setExistingWebSocket() + startAudioCaptureOnly() instead");
        Log.w(TAG, "This method is deprecated and should not be used");

        if (callback != null) {
            callback.onStreamingError("Method deprecated - creates duplicate connections. Use startAudioCaptureOnly()");
        }
    }

    private boolean checkAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int permission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO);
            if (permission != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Audio recording permission not granted");
                return false;
            }
        }
        return true;
    }

    private boolean initializeAudioCapture() {
        try {
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

            if (bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid audio parameters for AudioRecord");
                return false;
            }

            // Use larger buffer for better stability
            bufferSize = Math.max(bufferSize * 2, CHUNK_SIZE_BYTES * 4);

            // Try different audio sources in priority order
            int[] audioSources = {
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Best for calls
                    MediaRecorder.AudioSource.MIC,                 // General microphone
                    MediaRecorder.AudioSource.VOICE_CALL,          // Call audio (may conflict)
                    MediaRecorder.AudioSource.DEFAULT              // Last resort
            };

            for (int source : audioSources) {
                try {
                    // Check permission before each attempt
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                            != PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "Missing RECORD_AUDIO permission");
                        return false;
                    }

                    audioRecord = new AudioRecord(source, SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

                    if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                        Log.d(TAG, "Audio initialized with source: " + getSourceName(source));
                        audioRecord.startRecording();

                        // Test if audio source actually captures audio
                        if (testAudioSource()) {
                            Log.d(TAG, "Audio source test passed: " + getSourceName(source));
                            return true;
                        } else {
                            Log.w(TAG, "Audio source test failed: " + getSourceName(source));
                            stopAudioRecord();
                        }
                    } else {
                        Log.w(TAG, "AudioRecord not initialized for source: " + getSourceName(source));
                        stopAudioRecord();
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "Security exception for audio source " + getSourceName(source) + ": " + e.getMessage());
                    stopAudioRecord();
                } catch (Exception e) {
                    Log.w(TAG, "Failed to initialize audio source " + getSourceName(source) + ": " + e.getMessage());
                    stopAudioRecord();
                }
            }

            Log.e(TAG, "All audio sources failed to initialize");
            return false;

        } catch (Exception e) {
            Log.e(TAG, "Audio initialization error: " + e.getMessage());
            return false;
        }
    }

    private boolean testAudioSource() {
        if (audioRecord == null || audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            return false;
        }

        try {
            // Give audio time to stabilize
            Thread.sleep(200);

            byte[] testBuffer = new byte[1600]; // 50ms at 16kHz
            int bytesRead = audioRecord.read(testBuffer, 0, testBuffer.length);

            if (bytesRead > 0) {
                // Check for any non-zero audio samples
                for (int i = 0; i < bytesRead - 1; i += 2) {
                    short sample = (short) ((testBuffer[i + 1] << 8) | (testBuffer[i] & 0xFF));
                    if (Math.abs(sample) > 10) { // Very low threshold
                        return true; // Found audio activity
                    }
                }

                // Even if silent, consider it valid for call audio (might be quiet initially)
                Log.d(TAG, "Audio source captures data but appears silent (may be normal for calls)");
                return true;
            }

            return false;
        } catch (Exception e) {
            Log.e(TAG, "Audio source test failed: " + e.getMessage());
            return false;
        }
    }

    private void stopAudioRecord() {
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception e) {
                Log.w(TAG, "Error stopping AudioRecord: " + e.getMessage());
            } finally {
                audioRecord = null;
            }
        }
    }

    private void startAudioCaptureThread() {
        streamingExecutor.submit(() -> {
            byte[] buffer = new byte[CHUNK_SIZE_BYTES];
            long lastLogTime = System.currentTimeMillis();
            int chunksProcessed = 0;

            Log.d(TAG, "Audio capture thread started");

            while (isStreaming.get() && audioRecord != null) {
                try {
                    if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                        Log.w(TAG, "AudioRecord not recording, attempting to restart");
                        break;
                    }

                    int bytesRead = audioRecord.read(buffer, 0, buffer.length);

                    if (bytesRead > 0 && !isPaused.get()) {
                        chunksProcessed++;

                        byte[] chunk = new byte[bytesRead];
                        System.arraycopy(buffer, 0, chunk, 0, bytesRead);

                        // Process through enhanced audio processor
                        audioProcessor.processAudioChunk(chunk);
                    } else if (bytesRead < 0) {
                        Log.e(TAG, "Audio read error: " + bytesRead);
                        break;
                    }

                    // Periodic logging
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastLogTime >= 10000) { // Every 10 seconds
                        Log.d(TAG, "Audio capture status: " + chunksProcessed + " chunks processed");
                        chunksProcessed = 0;
                        lastLogTime = currentTime;
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Audio capture error: " + e.getMessage());
                    break;
                }
            }

            Log.d(TAG, "Audio capture thread stopped");
        });
    }

    private void queueAudioChunk(EnhancedAudioProcessor.AudioChunk chunk, boolean hasVoice) {
        if (!isStreaming.get() || isPaused.get()) {
            return;
        }

        AudioChunkData chunkData = new AudioChunkData(chunk.data, hasVoice, chunk.timestamp);

        // Check queue size and manage overflow
        if (chunkQueue.size() >= MAX_QUEUE_SIZE) {
            AudioChunkData dropped = chunkQueue.poll();
            if (dropped != null) {
                int dropped_count = droppedChunks.incrementAndGet();

                if (dropped_count % 10 == 0) {
                    Log.w(TAG, "Queue overflow - dropped " + dropped_count + " chunks total");
                    if (callback != null) {
                        callback.onQueueOverflow(dropped_count);
                    }
                }
            }
        }

        chunkQueue.offer(chunkData);
    }

    private void startStreamingThread() {
        streamingExecutor.submit(() -> {
            Log.d(TAG, "Streaming thread started (using external WebSocket)");

            while (isStreaming.get()) {
                try {
                    AudioChunkData chunkData = chunkQueue.poll(1, TimeUnit.SECONDS);

                    if (chunkData != null) {
                        if (connectionHealthy && externalWebSocket != null) {
                            sendChunkToWebSocket(chunkData);
                        } else {
                            Log.v(TAG, "Skipping chunk - external WebSocket not available");
                        }
                    }

                    // Check connection health
                    checkConnectionHealth();

                } catch (InterruptedException e) {
                    Log.d(TAG, "Streaming thread interrupted");
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Streaming error: " + e.getMessage());
                }
            }

            Log.d(TAG, "Streaming thread stopped");
        });
    }

    // FIXED: Send chunks via external WebSocket
    private void sendChunkToWebSocket(AudioChunkData chunkData) {
        try {
            // Create the correct ElevenLabs message format
            JSONObject message = new JSONObject();
            String base64Audio = Base64.getEncoder().encodeToString(chunkData.audioData);
            message.put("user_audio_chunk", base64Audio);

            boolean sent = externalWebSocket.send(message.toString());

            if (sent) {
                totalChunksSent.incrementAndGet();
                if (chunkData.hasVoice) {
                    chunksWithVoice.incrementAndGet();
                }

                lastSuccessfulSend = System.currentTimeMillis();

                if (callback != null) {
                    callback.onChunkSent(chunkData.audioData.length, chunkData.hasVoice);
                }

                Log.v(TAG, "Chunk sent via external WebSocket: " + chunkData.audioData.length + " bytes");

            } else {
                Log.w(TAG, "Failed to send chunk via external WebSocket");
                connectionHealthy = false;

                if (callback != null) {
                    callback.onConnectionHealthChanged(false);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error sending chunk via external WebSocket: " + e.getMessage());
            connectionHealthy = false;

            if (callback != null) {
                callback.onStreamingError("Send error: " + e.getMessage());
                callback.onConnectionHealthChanged(false);
            }
        }
    }

    private void checkConnectionHealth() {
        if (externalWebSocket == null) {
            if (connectionHealthy) {
                connectionHealthy = false;
                Log.w(TAG, "External WebSocket became unavailable");
                if (callback != null) {
                    callback.onConnectionHealthChanged(false);
                }
            }
            return;
        }

        long timeSinceLastSend = System.currentTimeMillis() - lastSuccessfulSend;

        if (timeSinceLastSend > STREAM_TIMEOUT_MS && connectionHealthy) {
            Log.w(TAG, "No successful sends for " + timeSinceLastSend + "ms via external WebSocket");
            connectionHealthy = false;

            if (callback != null) {
                callback.onConnectionHealthChanged(false);
            }
        } else if (timeSinceLastSend <= STREAM_TIMEOUT_MS && !connectionHealthy) {
            Log.d(TAG, "External WebSocket connection appears healthy again");
            connectionHealthy = true;

            if (callback != null) {
                callback.onConnectionHealthChanged(true);
            }
        }
    }

    public void pauseStreaming() {
        isPaused.set(true);
        Log.d(TAG, "Streaming paused");
    }

    public void resumeStreaming() {
        isPaused.set(false);
        Log.d(TAG, "Streaming resumed");
    }

    public void stopStreaming() {
        Log.d(TAG, "Stopping streaming...");

        isStreaming.set(false);
        isPaused.set(false);

        // Stop audio capture
        stopAudioRecord();

        // Clear queue
        chunkQueue.clear();

        // Reset processor
        if (audioProcessor != null) {
            audioProcessor.reset();
        }

        // Reset connection reference
        externalWebSocket = null;
        connectionHealthy = false;

        if (callback != null) {
            callback.onStreamingStopped();
        }

        Log.d(TAG, "Streaming stopped");
    }

    // Status and statistics
    public StreamingStats getStats() {
        return new StreamingStats(
                totalChunksSent.get(),
                chunksWithVoice.get(),
                droppedChunks.get(),
                chunkQueue.size(),
                connectionHealthy,
                isStreaming.get(),
                isPaused.get()
        );
    }

    public void logStatus() {
        StreamingStats stats = getStats();

        Log.d(TAG, "=== STREAMING STATUS ===");
        Log.d(TAG, "Active: " + stats.isStreaming);
        Log.d(TAG, "Paused: " + stats.isPaused);
        Log.d(TAG, "External WebSocket: " + (externalWebSocket != null ? "AVAILABLE" : "NULL"));
        Log.d(TAG, "Connection: " + (stats.connectionHealthy ? "HEALTHY" : "UNHEALTHY"));
        Log.d(TAG, "Total chunks: " + stats.totalChunks);
        Log.d(TAG, "Voice chunks: " + stats.voiceChunks + " (" +
                (stats.totalChunks > 0 ? (stats.voiceChunks * 100 / stats.totalChunks) : 0) + "%)");
        Log.d(TAG, "Dropped chunks: " + stats.droppedChunks);
        Log.d(TAG, "Queue size: " + stats.queueSize + "/" + MAX_QUEUE_SIZE);
        Log.d(TAG, "Audio processor: " + (audioProcessor != null ? audioProcessor.getProcessingStatus() : "null"));
        Log.d(TAG, "Last successful send: " + (System.currentTimeMillis() - lastSuccessfulSend) + "ms ago");
    }

    // Supporting classes
    private static class AudioChunkData {
        final byte[] audioData;
        final boolean hasVoice;
        final long timestamp;

        AudioChunkData(byte[] audioData, boolean hasVoice, long timestamp) {
            this.audioData = audioData;
            this.hasVoice = hasVoice;
            this.timestamp = timestamp;
        }
    }

    public static class StreamingStats {
        public final long totalChunks;
        public final long voiceChunks;
        public final int droppedChunks;
        public final int queueSize;
        public final boolean connectionHealthy;
        public final boolean isStreaming;
        public final boolean isPaused;

        StreamingStats(long totalChunks, long voiceChunks, int droppedChunks,
                       int queueSize, boolean connectionHealthy, boolean isStreaming, boolean isPaused) {
            this.totalChunks = totalChunks;
            this.voiceChunks = voiceChunks;
            this.droppedChunks = droppedChunks;
            this.queueSize = queueSize;
            this.connectionHealthy = connectionHealthy;
            this.isStreaming = isStreaming;
            this.isPaused = isPaused;
        }
    }

    private String getSourceName(int source) {
        switch (source) {
            case MediaRecorder.AudioSource.VOICE_CALL: return "VOICE_CALL";
            case MediaRecorder.AudioSource.VOICE_COMMUNICATION: return "VOICE_COMMUNICATION";
            case MediaRecorder.AudioSource.MIC: return "MIC";
            case MediaRecorder.AudioSource.DEFAULT: return "DEFAULT";
            default: return "UNKNOWN(" + source + ")";
        }
    }

    public void cleanup() {
        Log.d(TAG, "Cleaning up OptimizedAudioStreamer...");

        stopStreaming();

        if (streamingExecutor != null && !streamingExecutor.isShutdown()) {
            streamingExecutor.shutdown();
            try {
                if (!streamingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    Log.w(TAG, "StreamingExecutor didn't terminate gracefully, forcing shutdown");
                    streamingExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Log.w(TAG, "StreamingExecutor shutdown interrupted");
                streamingExecutor.shutdownNow();
            }
        }

        Log.d(TAG, "OptimizedAudioStreamer cleanup completed");
    }
}