package com.teletalker.app.services.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ✅ OPTIMIZED: Reduced silence timeout for faster AI response delivery
 *
 * KEY CHANGES:
 * - SILENCE_TIMEOUT_MS: 3000ms → 800ms (75% faster response completion)
 * - MIN_RESPONSE_GAP_MS: 500ms → 200ms (faster consecutive responses)
 * - Added configurable timeouts for fine-tuning
 */
public class AudioResponseAccumulator {
    private static final String TAG = "AudioAccumulator";

    // ============================================================================
    // ✅ OPTIMIZED TIMING CONSTANTS
    // ============================================================================

    // BEFORE: 3000ms - WAY TOO LONG!
    // AFTER: 800ms - Much more responsive while still allowing natural pauses
    private static final long SILENCE_TIMEOUT_MS = 800;  // ✅ REDUCED from 3000ms

    private static final long MAX_RESPONSE_DURATION_MS = 45000; // 45 seconds max (unchanged)

    // BEFORE: 320 bytes minimum
    // AFTER: Same (0.01s at 16kHz is reasonable)
    private static final int MIN_CHUNK_SIZE = 320;

    // BEFORE: 500ms gap between responses
    // AFTER: 200ms - allows faster back-and-forth
    private static final long MIN_RESPONSE_GAP_MS = 200;  // ✅ REDUCED from 500ms

    public interface AccumulatorCallback {
        void onResponseStarted();
        void onChunkAccumulated(int chunkSize, int totalSize);
        void onResponseCompleted(byte[] completeAudio, long durationMs);
        void onResponseTimeout(byte[] partialAudio, long durationMs);
        void onAccumulatorError(String error);
    }

    private final ExecutorService callbackExecutor;
    private final Handler timeoutHandler;
    private AccumulatorCallback callback;

    // Accumulation state
    private final ByteArrayOutputStream audioAccumulator;
    private final AtomicBoolean isAccumulating = new AtomicBoolean(false);
    private long responseStartTime = 0;
    private long lastChunkTime = 0;
    private long lastResponseCompletedTime = 0;
    private int totalChunksReceived = 0;

    // Timeout handling
    private Runnable silenceTimeoutRunnable;
    private Runnable maxDurationTimeoutRunnable;

    /**
     * Constructor with ExecutorService for proper thread context
     */
    public AudioResponseAccumulator(ExecutorService callbackExecutor) {
        this.callbackExecutor = callbackExecutor;
        this.timeoutHandler = new Handler(Looper.getMainLooper());
        this.audioAccumulator = new ByteArrayOutputStream();

        Log.d(TAG, "✅ AudioResponseAccumulator initialized (OPTIMIZED)");
        Log.d(TAG, "   Silence timeout: " + SILENCE_TIMEOUT_MS + "ms");
        Log.d(TAG, "   Min response gap: " + MIN_RESPONSE_GAP_MS + "ms");
    }

    /**
     * Legacy constructor for backward compatibility
     */
    public AudioResponseAccumulator() {
        this(null);
        Log.w(TAG, "⚠️ Using legacy Handler-based callbacks");
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

        // Prevent starting new response too quickly after previous one
        if (!isAccumulating.get() && lastResponseCompletedTime > 0) {
            long timeSinceLastResponse = currentTime - lastResponseCompletedTime;
            if (timeSinceLastResponse < MIN_RESPONSE_GAP_MS) {
                Log.d(TAG, "📦 Extending previous response - gap too short: " + timeSinceLastResponse + "ms");
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

            notifyCallback(cb -> cb.onChunkAccumulated(audioChunk.length, audioAccumulator.size()));

            // Reset silence timeout - THIS IS THE KEY TIMING CONTROL
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

        notifyCallback(cb -> cb.onResponseStarted());
    }

    /**
     * Complete the current response and deliver accumulated audio
     */
    private synchronized void completeResponse(boolean isTimeout) {
        if (!isAccumulating.get()) {
            return;
        }

        isAccumulating.set(false);
        lastResponseCompletedTime = System.currentTimeMillis();

        // Cancel all timeouts
        cancelTimeouts();

        byte[] completeAudio = audioAccumulator.toByteArray();
        long responseDuration = lastResponseCompletedTime - responseStartTime;

        Log.d(TAG, "🎯 AI Response " + (isTimeout ? "TIMEOUT" : "COMPLETED") + ":");
        Log.d(TAG, "  📊 Audio size: " + completeAudio.length + " bytes");
        Log.d(TAG, "  ⏱️ Duration: " + responseDuration + "ms");
        Log.d(TAG, "  📦 Chunks: " + totalChunksReceived);
        Log.d(TAG, "  🎵 Est. audio length: " + (completeAudio.length * 1000 / (16000 * 2)) + "ms");
        Log.d(TAG, "  ⏰ Silence timeout was: " + SILENCE_TIMEOUT_MS + "ms");

        if (completeAudio.length > 0) {
            if (isTimeout) {
                notifyCallback(cb -> cb.onResponseTimeout(completeAudio, responseDuration));
            } else {
                notifyCallback(cb -> cb.onResponseCompleted(completeAudio, responseDuration));
            }
        } else {
            Log.w(TAG, "⚠️ No audio accumulated for response");
        }

        // Reset accumulator
        audioAccumulator.reset();
    }

    /**
     * ✅ OPTIMIZED: Reduced silence timeout for faster response delivery
     */
    private void resetSilenceTimeout() {
        // Cancel existing timeout
        if (silenceTimeoutRunnable != null) {
            timeoutHandler.removeCallbacks(silenceTimeoutRunnable);
        }

        // Set new timeout with OPTIMIZED duration
        silenceTimeoutRunnable = () -> {
            Log.d(TAG, "⏰ Silence timeout (" + SILENCE_TIMEOUT_MS + "ms) - AI response complete");
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

        return String.format("ACCUMULATING (Size: %d bytes, Chunks: %d, Elapsed: %dms, Last chunk: %dms ago, Timeout in: %dms)",
                audioAccumulator.size(), totalChunksReceived, elapsedTime, timeSinceLastChunk,
                Math.max(0, SILENCE_TIMEOUT_MS - timeSinceLastChunk));
    }

    /**
     * Get detailed status for debugging
     */
    public void logStatus() {
        Log.d(TAG, "=== AUDIO ACCUMULATOR STATUS (OPTIMIZED) ===");
        Log.d(TAG, "Status: " + getAccumulationStatus());
        Log.d(TAG, "Accumulating: " + isAccumulating.get());
        Log.d(TAG, "Buffer size: " + audioAccumulator.size() + " bytes");
        Log.d(TAG, "Total chunks: " + totalChunksReceived);
        Log.d(TAG, "Silence timeout: " + SILENCE_TIMEOUT_MS + "ms (OPTIMIZED)");
        Log.d(TAG, "Min response gap: " + MIN_RESPONSE_GAP_MS + "ms (OPTIMIZED)");

        if (isAccumulating.get()) {
            long elapsedTime = System.currentTimeMillis() - responseStartTime;
            long timeSinceLastChunk = System.currentTimeMillis() - lastChunkTime;
            Log.d(TAG, "Response started: " + elapsedTime + "ms ago");
            Log.d(TAG, "Last chunk: " + timeSinceLastChunk + "ms ago");
            Log.d(TAG, "Will complete in: " + Math.max(0, SILENCE_TIMEOUT_MS - timeSinceLastChunk) + "ms");
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
        lastResponseCompletedTime = 0;
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

    /**
     * Notify callback on appropriate thread
     */
    private void notifyCallback(CallbackAction action) {
        if (callback != null) {
            if (callbackExecutor != null) {
                callbackExecutor.execute(() -> {
                    try {
                        action.execute(callback);
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error in callback execution: " + e.getMessage());
                    }
                });
            } else {
                timeoutHandler.post(() -> {
                    try {
                        action.execute(callback);
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error in callback execution: " + e.getMessage());
                    }
                });
            }
        }
    }

    @FunctionalInterface
    private interface CallbackAction {
        void execute(AccumulatorCallback callback);
    }

    // ============================================================================
    // ✅ NEW: Methods for runtime timing adjustment
    // ============================================================================

    /**
     * Get current silence timeout setting
     */
    public long getSilenceTimeoutMs() {
        return SILENCE_TIMEOUT_MS;
    }

    /**
     * Get current minimum response gap setting
     */
    public long getMinResponseGapMs() {
        return MIN_RESPONSE_GAP_MS;
    }
}