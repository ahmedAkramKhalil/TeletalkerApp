package com.teletalker.app.services.ai;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages AI audio response buffering and playback
 */
public class AIResponseBuffer {
    private static final String TAG = "AIResponseBuffer";

    // Audio configuration for AI responses
    private static final int AI_SAMPLE_RATE = 16000;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    public interface ResponseCallback {
        void onResponseStarted();
        void onResponseStopped();
        void onResponseError(String error);
        void onAudioReceived(int audioSize);
    }

    private final ExecutorService executorService;
    private ResponseCallback callback;

    // Audio playback components
    private AudioTrack audioTrack;
    private ArrayBlockingQueue<byte[]> responseQueue;

    // State tracking
    private final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    // Statistics
    private int totalAudioReceived = 0;
    private long lastResponseTime = 0;

    public AIResponseBuffer() {
        this.executorService = Executors.newSingleThreadExecutor();
        this.responseQueue = new ArrayBlockingQueue<>(20); // Buffer up to 20 audio chunks
    }

    public void setCallback(ResponseCallback callback) {
        this.callback = callback;
    }

    /**
     * Initialize AI audio playback system
     */
    public boolean initialize() {
        try {
            Log.d(TAG, "🔊 Initializing AI response buffer...");

            int playbackBufferSize = AudioTrack.getMinBufferSize(
                    AI_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AUDIO_FORMAT
            );

            // Configure for voice communication
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

            if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                Log.d(TAG, "✅ AI audio playback initialized");
                startProcessingThread();
                return true;
            } else {
                Log.e(TAG, "❌ Failed to initialize AI audio playback");
                cleanup();
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error initializing AI response buffer: " + e.getMessage());
            cleanup();
            return false;
        }
    }

    /**
     * Add AI audio response to buffer for playback
     */
    public void addAudioResponse(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            Log.w(TAG, "⚠️ Empty audio data received");
            return;
        }

        totalAudioReceived++;
        lastResponseTime = System.currentTimeMillis();

        Log.d(TAG, "🔊 AI Audio Response: " + audioData.length + " bytes (total: " + totalAudioReceived + ")");

        // Add to queue, dropping oldest if full
        if (!responseQueue.offer(audioData)) {
            Log.w(TAG, "⚠️ Response queue full, dropping old audio");
            responseQueue.poll(); // Remove oldest
            responseQueue.offer(audioData); // Add new
        }

        notifyCallback(cb -> cb.onAudioReceived(audioData.length));
    }

    /**
     * Start the audio processing thread
     */
    private void startProcessingThread() {
        isProcessing.set(true);
        executorService.execute(this::audioPlaybackLoop);
    }

    /**
     * Main audio playback loop
     */
    private void audioPlaybackLoop() {
        Log.d(TAG, "🔊 AI response playback thread started");

        while (isProcessing.get()) {
            try {
                // Wait for audio data with timeout
                byte[] responseAudio = responseQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);

                if (responseAudio != null && audioTrack != null) {
                    // Start playing if not already
                    if (!isPlaying.get()) {
                        startPlayback();
                    }

                    // Write audio data to track
                    int bytesWritten = audioTrack.write(responseAudio, 0, responseAudio.length);

                    if (bytesWritten < 0) {
                        Log.e(TAG, "❌ Error writing audio: " + bytesWritten);
                        notifyCallback(cb -> cb.onResponseError("Audio write error: " + bytesWritten));
                    }
                }

                // Stop playing if queue is empty and we were playing
                if (responseQueue.isEmpty() && isPlaying.get()) {
                    stopPlayback();
                }

            } catch (InterruptedException e) {
                Log.d(TAG, "Audio playback thread interrupted");
                break;
            } catch (Exception e) {
                Log.e(TAG, "❌ AI playback error: " + e.getMessage());
                notifyCallback(cb -> cb.onResponseError("Playback error: " + e.getMessage()));
            }
        }

        Log.d(TAG, "🔊 AI response playback thread stopped");
    }

    /**
     * Start audio playback
     */
    private void startPlayback() {
        try {
            if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                audioTrack.play();
                isPlaying.set(true);
                Log.d(TAG, "🔊 AI response playback started");
                notifyCallback(cb -> cb.onResponseStarted());
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error starting playback: " + e.getMessage());
            notifyCallback(cb -> cb.onResponseError("Start playback error: " + e.getMessage()));
        }
    }

    /**
     * Stop audio playback
     */
    private void stopPlayback() {
        try {
            if (audioTrack != null && isPlaying.get()) {
                audioTrack.pause();
                audioTrack.flush(); // Clear any remaining audio
                isPlaying.set(false);
                Log.d(TAG, "🔊 AI response playback stopped");
                notifyCallback(cb -> cb.onResponseStopped());
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error stopping playback: " + e.getMessage());
        }
    }

    /**
     * Clear all buffered audio
     */
    public void clearBuffer() {
        Log.d(TAG, "🗑️ Clearing AI response buffer");
        responseQueue.clear();

        if (isPlaying.get()) {
            stopPlayback();
        }
    }

    /**
     * Stop all processing and cleanup
     */
    public void shutdown() {
        Log.d(TAG, "🛑 Shutting down AI response buffer...");

        isProcessing.set(false);

        // Stop playback
        if (isPlaying.get()) {
            stopPlayback();
        }

        // Clear buffer
        clearBuffer();

        // Cleanup audio track
        cleanup();

        Log.d(TAG, "✅ AI response buffer shutdown complete");
    }

    /**
     * Get status information for debugging
     */
    public void logResponseStatus() {
        Log.d(TAG, "=== AI RESPONSE STATUS ===");
        Log.d(TAG, "Processing: " + isProcessing.get());
        Log.d(TAG, "Playing: " + isPlaying.get());
        Log.d(TAG, "Queue Size: " + responseQueue.size());
        Log.d(TAG, "Total Audio Received: " + totalAudioReceived);
        Log.d(TAG, "Audio Track: " + (audioTrack != null ? "Available" : "null"));
        Log.d(TAG, "Last Response: " + (System.currentTimeMillis() - lastResponseTime) + "ms ago");
    }

    /**
     * Check if AI is currently responding
     */
    public boolean isCurrentlyPlaying() {
        return isPlaying.get();
    }

    /**
     * Get queue status
     */
    public int getQueueSize() {
        return responseQueue.size();
    }

    /**
     * Get total audio received count
     */
    public int getTotalAudioReceived() {
        return totalAudioReceived;
    }

    /**
     * Get time since last response
     */
    public long getTimeSinceLastResponse() {
        return lastResponseTime > 0 ? System.currentTimeMillis() - lastResponseTime : -1;
    }

    /**
     * Reset statistics
     */
    public void resetStatistics() {
        totalAudioReceived = 0;
        lastResponseTime = 0;
        Log.d(TAG, "📊 Statistics reset");
    }

    private void cleanup() {
        if (audioTrack != null) {
            try {
                if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack.stop();
                }
                audioTrack.release();
                audioTrack = null;
            } catch (Exception e) {
                Log.w(TAG, "Error cleaning up audio track: " + e.getMessage());
            }
        }
    }

    private void notifyCallback(CallbackAction action) {
        if (callback != null) {
            action.execute(callback);
        }
    }

    @FunctionalInterface
    private interface CallbackAction {
        void execute(ResponseCallback callback);
    }

    /**
     * Cleanup resources when done
     */
    public void destroy() {
        shutdown();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}