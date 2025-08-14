package com.teletalker.app.services.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Accumulates AI audio chunks and injects complete responses
 * Prevents fragmented audio injection by buffering until response is complete
 */
public class AudioResponseAccumulator {
    private static final String TAG = "AudioAccumulator";

    // Timing constants for detecting end of response
    private static final long SILENCE_TIMEOUT_MS = 3000; // 3 seconds silence = end of response (increased)
    private static final long MAX_RESPONSE_DURATION_MS = 45000; // 45 seconds max response (increased)
    private static final int MIN_CHUNK_SIZE = 320; // Minimum chunk size to consider (0.01s at 16kHz)
    private static final long MIN_RESPONSE_GAP_MS = 500; // Minimum gap between responses

    public interface AccumulatorCallback {
        void onResponseStarted();
        void onChunkAccumulated(int chunkSize, int totalSize);
        void onResponseCompleted(byte[] completeAudio, long durationMs);
        void onResponseTimeout(byte[] partialAudio, long durationMs);
        void onAccumulatorError(String error);
    }

    private final Handler timeoutHandler;
    private AccumulatorCallback callback;

    // Accumulation state
    private final ByteArrayOutputStream audioAccumulator;
    private final AtomicBoolean isAccumulating = new AtomicBoolean(false);
    private long responseStartTime = 0;
    private long lastChunkTime = 0;
    private long lastResponseCompletedTime = 0; // NEW: Track when last response completed
    private int totalChunksReceived = 0;

    // Timeout handling
    private Runnable silenceTimeoutRunnable;
    private Runnable maxDurationTimeoutRunnable;

    public AudioResponseAccumulator() {
        this.timeoutHandler = new Handler(Looper.getMainLooper());
        this.audioAccumulator = new ByteArrayOutputStream();
    }

    public void setCallback(AccumulatorCallback callback) {
        this.callback = callback;
    }

    /**
     * Add an audio chunk to the accumulator
     */
    public synchronized void addAudioChunk(byte[] audioChunk) {
        if (audioChunk == null || audioChunk.length < MIN_CHUNK_SIZE) {
            Log.v(TAG, "⚠️ Skipping tiny/null chunk: " + (audioChunk != null ? audioChunk.length : 0) + " bytes");
            return;
        }

        long currentTime = System.currentTimeMillis();

        // NEW: Prevent starting new response too quickly after previous one
        if (!isAccumulating.get() && lastResponseCompletedTime > 0) {
            long timeSinceLastResponse = currentTime - lastResponseCompletedTime;
            if (timeSinceLastResponse < MIN_RESPONSE_GAP_MS) {
                Log.d(TAG, "📦 Extending previous response - gap too short: " + timeSinceLastResponse + "ms");
                // Continue accumulating instead of starting new response
                isAccumulating.set(true);
            }
        }

        // Start new response if not accumulating
        if (!isAccumulating.get()) {
            startNewResponse(currentTime);
        }

        // Add chunk to accumulator
        try {
            audioAccumulator.write(audioChunk);
            totalChunksReceived++;
            lastChunkTime = currentTime;

            Log.d(TAG, "📦 Chunk accumulated: " + audioChunk.length + " bytes (Total: " +
                    audioAccumulator.size() + " bytes, Chunks: " + totalChunksReceived +
                    ", Elapsed: " + (currentTime - responseStartTime) + "ms)");

            // Notify callback
            notifyCallback(cb -> cb.onChunkAccumulated(audioChunk.length, audioAccumulator.size()));

            // Reset silence timeout
            resetSilenceTimeout();

        } catch (Exception e) {
            Log.e(TAG, "❌ Error accumulating audio chunk", e);
            notifyCallback(cb -> cb.onAccumulatorError("Failed to accumulate chunk: " + e.getMessage()));
        }
    }

    /**
     * Start accumulating a new response
     */
    private void startNewResponse(long currentTime) {
        Log.d(TAG, "🎬 Starting new AI response accumulation");

        isAccumulating.set(true);
        responseStartTime = currentTime;
        lastChunkTime = currentTime;
        totalChunksReceived = 0;

        // Clear previous data
        audioAccumulator.reset();

        // Set maximum duration timeout
        setMaxDurationTimeout();

        // Notify callback
        notifyCallback(cb -> cb.onResponseStarted());
    }

    /**
     * Complete the current response and inject accumulated audio
     */
    private synchronized void completeResponse(boolean isTimeout) {
        if (!isAccumulating.get()) {
            return;
        }

        isAccumulating.set(false);
        lastResponseCompletedTime = System.currentTimeMillis(); // NEW: Track completion time

        // Cancel all timeouts
        cancelTimeouts();

        byte[] completeAudio = audioAccumulator.toByteArray();
        long responseDuration = lastResponseCompletedTime - responseStartTime;

        Log.d(TAG, "🎯 AI Response " + (isTimeout ? "TIMEOUT" : "COMPLETED") + ":");
        Log.d(TAG, "  📊 Audio size: " + completeAudio.length + " bytes");
        Log.d(TAG, "  ⏱️ Duration: " + responseDuration + "ms");
        Log.d(TAG, "  📦 Chunks: " + totalChunksReceived);
        Log.d(TAG, "  🎵 Est. audio length: " + (completeAudio.length * 1000 / (16000 * 2)) + "ms");
        Log.d(TAG, "  ⏰ Reason: " + (isTimeout ? "TIMEOUT" : "SILENCE_DETECTED"));

        if (completeAudio.length > 0) {
            if (isTimeout) {
                notifyCallback(cb -> cb.onResponseTimeout(completeAudio, responseDuration));
            } else {
                notifyCallback(cb -> cb.onResponseCompleted(completeAudio, responseDuration));
            }
        } else {
            Log.w(TAG, "⚠️ No audio accumulated for response");
        }

        // Reset accumulator but keep lastResponseCompletedTime
        audioAccumulator.reset();
    }

    /**
     * Set timeout for silence detection (end of response)
     */
    private void resetSilenceTimeout() {
        // Cancel existing timeout
        if (silenceTimeoutRunnable != null) {
            timeoutHandler.removeCallbacks(silenceTimeoutRunnable);
        }

        // Set new timeout
        silenceTimeoutRunnable = () -> {
            Log.d(TAG, "⏰ Silence timeout - AI response complete");
            completeResponse(false);
        };

        timeoutHandler.postDelayed(silenceTimeoutRunnable, SILENCE_TIMEOUT_MS);
    }

    /**
     * Set maximum duration timeout (prevent infinite accumulation)
     */
    private void setMaxDurationTimeout() {
        maxDurationTimeoutRunnable = () -> {
            Log.w(TAG, "⏰ Maximum duration timeout - forcing response completion");
            completeResponse(true);
        };

        timeoutHandler.postDelayed(maxDurationTimeoutRunnable, MAX_RESPONSE_DURATION_MS);
    }

    /**
     * Cancel all timeouts
     */
    private void cancelTimeouts() {
        if (silenceTimeoutRunnable != null) {
            timeoutHandler.removeCallbacks(silenceTimeoutRunnable);
            silenceTimeoutRunnable = null;
        }

        if (maxDurationTimeoutRunnable != null) {
            timeoutHandler.removeCallbacks(maxDurationTimeoutRunnable);
            maxDurationTimeoutRunnable = null;
        }
    }

    /**
     * Force complete current response (if any)
     */
    public synchronized void forceCompleteResponse() {
        if (isAccumulating.get()) {
            Log.d(TAG, "🔧 Force completing current response");
            completeResponse(false);
        }
    }

    /**
     * Check if currently accumulating a response
     */
    public boolean isAccumulating() {
        return isAccumulating.get();
    }

    /**
     * Get current accumulation status
     */
    public String getAccumulationStatus() {
        if (!isAccumulating.get()) {
            return "IDLE";
        }

        long elapsedTime = System.currentTimeMillis() - responseStartTime;
        long timeSinceLastChunk = System.currentTimeMillis() - lastChunkTime;

        return String.format("ACCUMULATING (Size: %d bytes, Chunks: %d, Elapsed: %dms, Last chunk: %dms ago)",
                audioAccumulator.size(), totalChunksReceived, elapsedTime, timeSinceLastChunk);
    }

    /**
     * Get detailed status for debugging
     */
    public void logStatus() {
        Log.d(TAG, "=== AUDIO ACCUMULATOR STATUS ===");
        Log.d(TAG, "Status: " + getAccumulationStatus());
        Log.d(TAG, "Accumulating: " + isAccumulating.get());
        Log.d(TAG, "Buffer size: " + audioAccumulator.size() + " bytes");
        Log.d(TAG, "Total chunks: " + totalChunksReceived);

        if (isAccumulating.get()) {
            long elapsedTime = System.currentTimeMillis() - responseStartTime;
            long timeSinceLastChunk = System.currentTimeMillis() - lastChunkTime;
            Log.d(TAG, "Response started: " + elapsedTime + "ms ago");
            Log.d(TAG, "Last chunk: " + timeSinceLastChunk + "ms ago");
        }
    }

    /**
     * Reset accumulator state
     */
    public synchronized void reset() {
        Log.d(TAG, "🔄 Resetting accumulator");

        isAccumulating.set(false);
        cancelTimeouts();
        audioAccumulator.reset();
        totalChunksReceived = 0;
        responseStartTime = 0;
        lastChunkTime = 0;
        lastResponseCompletedTime = 0; // NEW: Reset completion time
    }

    /**
     * Cleanup resources
     */
    public void cleanup() {
        Log.d(TAG, "🧹 Cleaning up accumulator");

        reset();
        timeoutHandler.removeCallbacksAndMessages(null);

        try {
            audioAccumulator.close();
        } catch (Exception e) {
            Log.w(TAG, "Error closing accumulator: " + e.getMessage());
        }
    }

    private void notifyCallback(CallbackAction action) {
        if (callback != null) {
            action.execute(callback);
        }
    }

    @FunctionalInterface
    private interface CallbackAction {
        void execute(AccumulatorCallback callback);
    }
}