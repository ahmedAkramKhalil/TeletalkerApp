package com.teletalker.app.services.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ✅ FIXED: Accumulates AI audio chunks and injects complete responses
 * Now uses background thread (ExecutorService) instead of main thread (Handler)
 * This restores the original working thread context for injection
 */
public class AudioResponseAccumulator {
    private static final String TAG = "AudioAccumulator";

    // Timing constants for detecting end of response
    private static final long SILENCE_TIMEOUT_MS = 3000; // 3 seconds silence = end of response
    private static final long MAX_RESPONSE_DURATION_MS = 45000; // 45 seconds max response
    private static final int MIN_CHUNK_SIZE = 320; // Minimum chunk size to consider (0.01s at 16kHz)
    private static final long MIN_RESPONSE_GAP_MS = 500; // Minimum gap between responses

    public interface AccumulatorCallback {
        void onResponseStarted();
        void onChunkAccumulated(int chunkSize, int totalSize);
        void onResponseCompleted(byte[] completeAudio, long durationMs);
        void onResponseTimeout(byte[] partialAudio, long durationMs);
        void onAccumulatorError(String error);
    }

    // ✅ FIXED: Use ExecutorService for callbacks instead of Handler
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
     * ✅ FIXED: Constructor now takes ExecutorService for proper thread context
     */
    public AudioResponseAccumulator(ExecutorService callbackExecutor) {
        this.callbackExecutor = callbackExecutor;
        this.timeoutHandler = new Handler(Looper.getMainLooper());
        this.audioAccumulator = new ByteArrayOutputStream();

        Log.d(TAG, "✅ AudioResponseAccumulator initialized with ExecutorService (background thread context)");
    }

    /**
     * ✅ LEGACY: Default constructor for backward compatibility
     */
    public AudioResponseAccumulator() {
        this(null); // Will use Handler for callbacks (old behavior)
        Log.w(TAG, "⚠️ Using legacy Handler-based callbacks - consider passing ExecutorService for better performance");
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

            // ✅ FIXED: Notify callback on background thread
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

        // ✅ FIXED: Notify callback on background thread
        notifyCallback(cb -> cb.onResponseStarted());
    }

    /**
     * ✅ FIXED: Complete the current response and inject accumulated audio
     * This now runs on background thread via ExecutorService (like original working code)
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
        Log.d(TAG, "  ⏰ Reason: " + (isTimeout ? "TIMEOUT" : "SILENCE_DETECTED"));
        Log.d(TAG, "  🔧 Thread: " + Thread.currentThread().getName());

        if (completeAudio.length > 0) {
            // ✅ FIXED: Notify callback on background thread (ExecutorService)
            // This restores the original working thread context for injection
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
        Log.d(TAG, "Callback Executor: " + (callbackExecutor != null ? "ExecutorService (background)" : "Handler (main thread)"));
        Log.d(TAG, "Current Thread: " + Thread.currentThread().getName());

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
        lastResponseCompletedTime = 0;
    }

    /**
     * ✅ FIXED: Cleanup resources with proper executor shutdown
     */
    public void cleanup() {
        Log.d(TAG, "🧹 Cleaning up accumulator");

        reset();
        timeoutHandler.removeCallbacksAndMessages(null);

        // Note: Don't shutdown callbackExecutor here as it's shared
        // The parent class (AICallRecorderRefactored) will shut it down

        try {
            audioAccumulator.close();
        } catch (Exception e) {
            Log.w(TAG, "Error closing accumulator: " + e.getMessage());
        }
    }

    /**
     * ✅ FIXED: Notify callback on appropriate thread
     */
    private void notifyCallback(CallbackAction action) {
        if (callback != null) {
            if (callbackExecutor != null) {
                // ✅ FIXED: Use ExecutorService for background thread (like original working code)
                callbackExecutor.execute(() -> {
                    try {
                        action.execute(callback);
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error in callback execution: " + e.getMessage());
                    }
                });
            } else {
                // Legacy: Use Handler for main thread (old behavior)
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
}