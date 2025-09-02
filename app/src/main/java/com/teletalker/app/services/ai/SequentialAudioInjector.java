package com.teletalker.app.services.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.teletalker.app.services.CallAudioInjector;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enhanced SequentialAudioInjector with Precise Timing Control
 *
 * NEW FEATURES:
 * ✅ Audio chunk duration calculation
 * ✅ Smart timing-based delays between injections
 * ✅ PCM device release buffer time
 * ✅ Configurable timing parameters
 * ✅ Real-time timing adjustments
 */
public class SequentialAudioInjector {
    private static final String TAG = "SequentialInjector";

    // ============================================================================
    // TIMING CONFIGURATION - Adjustable parameters
    // ============================================================================

    // Audio format assumptions (adjust if needed)
    private static final int SAMPLE_RATE = 16000;  // 16kHz
    private static final int CHANNELS = 1;         // Mono
    private static final int BITS_PER_SAMPLE = 16; // 16-bit
    private static final int BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8; // 2 bytes

    // Timing buffers (milliseconds)
    private static final long PCM_DEVICE_RELEASE_BUFFER_MS = 200;  // Extra time for device release
    private static final long MIN_DELAY_BETWEEN_CHUNKS_MS = 50;    // Minimum gap between chunks
    private static final long MAX_DELAY_BETWEEN_CHUNKS_MS = 2000;  // Maximum reasonable delay

    // Processing configuration
    private static final int MAX_QUEUE_SIZE = 50;
    private static final long INJECTION_TIMEOUT_MS = 30000;
    private static final int MAX_RETRIES = 3;
    private static final int MIN_CHUNK_SIZE = 320; // 0.01s at 16kHz mono

    // Core components
    private final CallAudioInjector audioInjector;
    private final ExecutorService injectionExecutor;
    private final ExecutorService monitoringExecutor;
    private final Handler statusHandler;
    private final ConcurrentLinkedQueue<TimedAudioChunk> injectionQueue;

    // State management
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicBoolean isCurrentlyInjecting = new AtomicBoolean(false);
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);

    // ============================================================================
    // TIMING STATE - Track injection timing
    // ============================================================================
    private final AtomicLong lastInjectionStartTime = new AtomicLong(0);
    private final AtomicLong lastInjectionEndTime = new AtomicLong(0);
    private final AtomicLong totalDelayTime = new AtomicLong(0);
    private final AtomicLong totalAudioDuration = new AtomicLong(0);

    // Statistics
    private final AtomicInteger totalChunksQueued = new AtomicInteger(0);
    private final AtomicInteger totalChunksInjected = new AtomicInteger(0);
    private final AtomicInteger totalChunksDropped = new AtomicInteger(0);
    private final AtomicInteger totalRetries = new AtomicInteger(0);
    private final AtomicLong totalBytesQueued = new AtomicLong(0);
    private final AtomicLong totalBytesInjected = new AtomicLong(0);
    private final AtomicLong firstChunkTime = new AtomicLong(0);

    // Callback interface
    public interface SequentialInjectionCallback {
        void onQueueStatusChanged(int queueSize, boolean isProcessing);
        void onInjectionStarted(int chunkNumber, int chunkSize);
        void onInjectionCompleted(int chunkNumber, boolean success, long durationMs);
        void onInjectionError(int chunkNumber, String error, int retryCount);
        void onQueueOverflow(int droppedChunks);
        void onStatisticsUpdate(InjectionStatistics stats);

        // NEW: Timing-specific callbacks
        default void onChunkTimingCalculated(int chunkNumber, long audioDurationMs, long delayMs) {}
        default void onDelayStarted(int chunkNumber, long delayMs, String reason) {}
        default void onDelayCompleted(int chunkNumber, long actualDelayMs) {}
    }

    private SequentialInjectionCallback callback;

    // ============================================================================
    // ENHANCED AUDIO CHUNK WITH TIMING
    // ============================================================================
    private static class TimedAudioChunk {
        final byte[] data;
        final int chunkNumber;
        final long timestamp;
        final int retryCount;
        final long calculatedDurationMs;  // NEW: Calculated audio duration
        final long requiredDelayMs;       // NEW: Required delay before this chunk

        TimedAudioChunk(byte[] data, int chunkNumber, long timestamp, int retryCount,
                        long calculatedDurationMs, long requiredDelayMs) {
            this.data = data;
            this.chunkNumber = chunkNumber;
            this.timestamp = timestamp;
            this.retryCount = retryCount;
            this.calculatedDurationMs = calculatedDurationMs;
            this.requiredDelayMs = requiredDelayMs;
        }

        TimedAudioChunk withRetry() {
            return new TimedAudioChunk(data, chunkNumber, timestamp, retryCount + 1,
                    calculatedDurationMs, requiredDelayMs);
        }
    }

    // Enhanced statistics
    public static class InjectionStatistics {
        public final int chunksQueued;
        public final int chunksInjected;
        public final int chunksDropped;
        public final int retryCount;
        public final long bytesQueued;
        public final long bytesInjected;
        public final double successRate;
        public final double averageInjectionTime;
        public final long totalProcessingTime;
        public final int currentQueueSize;
        public final boolean isProcessing;
        public final boolean isInjecting;

        // NEW: Timing statistics
        public final long totalAudioDuration;
        public final long totalDelayTime;
        public final double delayToAudioRatio;
        public final long averageDelayPerChunk;
        public final long timeSinceLastInjection;

        InjectionStatistics(SequentialAudioInjector injector) {
            this.chunksQueued = injector.totalChunksQueued.get();
            this.chunksInjected = injector.totalChunksInjected.get();
            this.chunksDropped = injector.totalChunksDropped.get();
            this.retryCount = injector.totalRetries.get();
            this.bytesQueued = injector.totalBytesQueued.get();
            this.bytesInjected = injector.totalBytesInjected.get();
            this.successRate = chunksQueued > 0 ? (chunksInjected * 100.0 / chunksQueued) : 0.0;
            this.currentQueueSize = injector.injectionQueue.size();
            this.isProcessing = injector.isProcessing.get();
            this.isInjecting = injector.isCurrentlyInjecting.get();

            // Timing statistics
            this.totalAudioDuration = injector.totalAudioDuration.get();
            this.totalDelayTime = injector.totalDelayTime.get();
            this.delayToAudioRatio = totalAudioDuration > 0 ? (totalDelayTime / (double) totalAudioDuration) : 0.0;
            this.averageDelayPerChunk = chunksInjected > 0 ? (totalDelayTime / chunksInjected) : 0;

            long lastEnd = injector.lastInjectionEndTime.get();
            this.timeSinceLastInjection = lastEnd > 0 ? (System.currentTimeMillis() - lastEnd) : -1;

            long totalTime = System.currentTimeMillis() - injector.firstChunkTime.get();
            this.totalProcessingTime = totalTime > 0 ? totalTime : 0;
            this.averageInjectionTime = chunksInjected > 0 ? (totalTime / (double) chunksInjected) : 0.0;
        }
    }

    // ============================================================================
    // CONSTRUCTOR
    // ============================================================================
    public SequentialAudioInjector(CallAudioInjector audioInjector) {
        this.audioInjector = audioInjector;
        this.injectionExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "SequentialInjector-Main");
            t.setDaemon(true);
            return t;
        });
        this.monitoringExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "SequentialInjector-Monitor");
            t.setDaemon(true);
            return t;
        });
        this.statusHandler = new Handler(Looper.getMainLooper());
        this.injectionQueue = new ConcurrentLinkedQueue<>();

        startMonitoring();

        Log.d(TAG, "✅ Enhanced SequentialAudioInjector with timing control initialized");
        Log.d(TAG, "📊 Timing Config: PCM Buffer=" + PCM_DEVICE_RELEASE_BUFFER_MS + "ms, " +
                "Min Delay=" + MIN_DELAY_BETWEEN_CHUNKS_MS + "ms, Audio Format=" +
                SAMPLE_RATE + "Hz/" + CHANNELS + "ch/" + BITS_PER_SAMPLE + "bit");
    }

    public void setCallback(SequentialInjectionCallback callback) {
        this.callback = callback;
    }

    // ============================================================================
    // MAIN QUEUEING METHOD WITH TIMING CALCULATION
    // ============================================================================
    public void queueAudioChunk(byte[] audioChunk) {
        if (audioChunk == null || audioChunk.length < MIN_CHUNK_SIZE) {
            Log.v(TAG, "⚠️ Skipping invalid chunk: " + (audioChunk != null ? audioChunk.length : 0) + " bytes");
            return;
        }

        if (isShutdown.get()) {
            Log.w(TAG, "⚠️ Rejecting chunk - injector is shut down");
            return;
        }

        if (isPaused.get()) {
            Log.d(TAG, "⏸️ Dropping chunk - injector is paused");
            return;
        }

        // Set first chunk time for statistics
        if (firstChunkTime.get() == 0) {
            firstChunkTime.set(System.currentTimeMillis());
        }

        int chunkNumber = totalChunksQueued.incrementAndGet();
        totalBytesQueued.addAndGet(audioChunk.length);

        // ========================================================================
        // CALCULATE AUDIO DURATION AND REQUIRED DELAY
        // ========================================================================
        long audioDurationMs = calculateAudioDuration(audioChunk);
        long requiredDelayMs = calculateRequiredDelay(audioDurationMs);

        totalAudioDuration.addAndGet(audioDurationMs);

        Log.d(TAG, "🎵 Chunk #" + chunkNumber + " timing:");
        Log.d(TAG, "  📊 Size: " + audioChunk.length + " bytes");
        Log.d(TAG, "  ⏱️ Audio Duration: " + audioDurationMs + "ms");
        Log.d(TAG, "  ⏳ Required Delay: " + requiredDelayMs + "ms");

        // Notify callback about timing calculation
        notifyCallback(cb -> cb.onChunkTimingCalculated(chunkNumber, audioDurationMs, requiredDelayMs));

        // Check queue overflow
        if (injectionQueue.size() >= MAX_QUEUE_SIZE) {
            int dropped = dropOldChunks();
            if (dropped > 0) {
                notifyCallback(cb -> cb.onQueueOverflow(dropped));
            }
        }

        // Create timed chunk and queue it
        TimedAudioChunk timedChunk = new TimedAudioChunk(
                audioChunk, chunkNumber, System.currentTimeMillis(), 0,
                audioDurationMs, requiredDelayMs
        );

        injectionQueue.offer(timedChunk);

        Log.d(TAG, "📦 Queued timed chunk #" + chunkNumber + " (Queue: " +
                injectionQueue.size() + "/" + MAX_QUEUE_SIZE + ")");

        // Start processing if not already running
        if (!isProcessing.get() && !isShutdown.get()) {
            startProcessing();
        }

        notifyCallback(cb -> cb.onQueueStatusChanged(injectionQueue.size(), isProcessing.get()));
    }

    // ============================================================================
    // AUDIO DURATION CALCULATION
    // ============================================================================
    private long calculateAudioDuration(byte[] audioData) {
        // Calculate based on PCM format: 16kHz, 16-bit, mono
        int totalSamples = audioData.length / BYTES_PER_SAMPLE;
        long durationMs = (totalSamples * 1000L) / SAMPLE_RATE;

        Log.v(TAG, "📏 Audio calculation: " + audioData.length + " bytes = " +
                totalSamples + " samples = " + durationMs + "ms");

        return durationMs;
    }

    // ============================================================================
    // DELAY CALCULATION LOGIC
    // ============================================================================
    private long calculateRequiredDelay(long audioDurationMs) {
        long currentTime = System.currentTimeMillis();
        long lastEndTime = lastInjectionEndTime.get();

        // If this is the first chunk or a lot of time has passed, no delay needed
        if (lastEndTime == 0 || (currentTime - lastEndTime) > MAX_DELAY_BETWEEN_CHUNKS_MS) {
            Log.v(TAG, "🚀 No delay needed - first chunk or long gap");
            return MIN_DELAY_BETWEEN_CHUNKS_MS; // Minimum delay for device stability
        }

        // Calculate how much time should have passed since last injection ended
        long timeSinceLastEnd = currentTime - lastEndTime;

        // We want to wait for:
        // 1. The previous audio to finish playing (audioDurationMs)
        // 2. Extra buffer for PCM device release (PCM_DEVICE_RELEASE_BUFFER_MS)
        // 3. Minimum gap between chunks (MIN_DELAY_BETWEEN_CHUNKS_MS)

        long minimumRequiredGap = PCM_DEVICE_RELEASE_BUFFER_MS + MIN_DELAY_BETWEEN_CHUNKS_MS;

        // If enough time has already passed, use minimum delay
        if (timeSinceLastEnd >= minimumRequiredGap) {
            Log.v(TAG, "⚡ Minimum delay - enough time passed (" + timeSinceLastEnd + "ms >= " + minimumRequiredGap + "ms)");
            return MIN_DELAY_BETWEEN_CHUNKS_MS;
        }

        // Calculate remaining delay needed
        long remainingDelay = minimumRequiredGap - timeSinceLastEnd;

        // Cap the delay to prevent excessive waiting
        long finalDelay = Math.min(remainingDelay, MAX_DELAY_BETWEEN_CHUNKS_MS);
        finalDelay = Math.max(finalDelay, MIN_DELAY_BETWEEN_CHUNKS_MS);

        Log.v(TAG, "⏰ Calculated delay: " + finalDelay + "ms (gap needed: " + minimumRequiredGap +
                "ms, time passed: " + timeSinceLastEnd + "ms)");

        return finalDelay;
    }

    // ============================================================================
    // ENHANCED PROCESSING WITH TIMING CONTROL
    // ============================================================================
    private void startProcessing() {
        if (isProcessing.getAndSet(true)) {
            return;
        }

        Log.d(TAG, "🔄 Starting timed sequential processing thread");

        injectionExecutor.execute(() -> {
            try {
                processQueueWithTiming();
            } catch (Exception e) {
                Log.e(TAG, "❌ Critical error in processing thread: " + e.getMessage(), e);
            } finally {
                isProcessing.set(false);
                Log.d(TAG, "🏁 Sequential processing thread finished");
                notifyCallback(cb -> cb.onQueueStatusChanged(injectionQueue.size(), false));
            }
        });
    }

    private void processQueueWithTiming() {
        while (!isShutdown.get() && (!injectionQueue.isEmpty() || isCurrentlyInjecting.get())) {
            if (isPaused.get()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            TimedAudioChunk nextChunk = injectionQueue.poll();
            if (nextChunk != null) {
                boolean success = injectChunkWithTiming(nextChunk);

                if (!success && nextChunk.retryCount < MAX_RETRIES) {
                    TimedAudioChunk retryChunk = nextChunk.withRetry();
                    injectionQueue.offer(retryChunk);
                    totalRetries.incrementAndGet();

                    Log.w(TAG, "🔄 Queuing chunk #" + nextChunk.chunkNumber +
                            " for retry (" + retryChunk.retryCount + "/" + MAX_RETRIES + ")");

                    notifyCallback(cb -> cb.onInjectionError(nextChunk.chunkNumber,
                            "Injection failed, retrying", retryChunk.retryCount));
                }
            } else {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    // ============================================================================
    // INJECT CHUNK WITH PRECISE TIMING
    // ============================================================================
    private boolean injectChunkWithTiming(TimedAudioChunk chunk) {
        long overallStartTime = System.currentTimeMillis();
        isCurrentlyInjecting.set(true);

        try {
            Log.d(TAG, "🎯 Starting timed injection for chunk #" + chunk.chunkNumber);
            Log.d(TAG, "  📊 Size: " + chunk.data.length + " bytes");
            Log.d(TAG, "  ⏱️ Audio Duration: " + chunk.calculatedDurationMs + "ms");
            Log.d(TAG, "  ⏳ Required Delay: " + chunk.requiredDelayMs + "ms");

            // ====================================================================
            // STEP 1: APPLY CALCULATED DELAY
            // ====================================================================
            if (chunk.requiredDelayMs > 0) {
                long delayStartTime = System.currentTimeMillis();

                String delayReason = "PCM device release + timing sync";
                Log.d(TAG, "⏳ Applying delay: " + chunk.requiredDelayMs + "ms (" + delayReason + ")");

                notifyCallback(cb -> cb.onDelayStarted(chunk.chunkNumber, chunk.requiredDelayMs, delayReason));

                try {
                    Thread.sleep(chunk.requiredDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.w(TAG, "⚠️ Delay interrupted for chunk #" + chunk.chunkNumber);
                    return false;
                }

                long actualDelayMs = System.currentTimeMillis() - delayStartTime;
                totalDelayTime.addAndGet(actualDelayMs);

                Log.d(TAG, "✅ Delay completed: " + actualDelayMs + "ms (planned: " + chunk.requiredDelayMs + "ms)");
                notifyCallback(cb -> cb.onDelayCompleted(chunk.chunkNumber, actualDelayMs));
            }

            // ====================================================================
            // STEP 2: PERFORM INJECTION
            // ====================================================================
            lastInjectionStartTime.set(System.currentTimeMillis());

            notifyCallback(cb -> cb.onInjectionStarted(chunk.chunkNumber, chunk.data.length));

            boolean success = performTimedInjection(chunk);

            long injectionEndTime = System.currentTimeMillis();
            lastInjectionEndTime.set(injectionEndTime);

            long totalDuration = injectionEndTime - overallStartTime;
            long injectionDuration = injectionEndTime - lastInjectionStartTime.get();

            if (success) {
                totalChunksInjected.incrementAndGet();
                totalBytesInjected.addAndGet(chunk.data.length);

                Log.d(TAG, "✅ Chunk #" + chunk.chunkNumber + " completed successfully:");
                Log.d(TAG, "  ⏱️ Total time: " + totalDuration + "ms");
                Log.d(TAG, "  🎯 Injection time: " + injectionDuration + "ms");
                Log.d(TAG, "  📊 Audio duration: " + chunk.calculatedDurationMs + "ms");
            } else {
                Log.w(TAG, "❌ Chunk #" + chunk.chunkNumber + " injection failed (" + totalDuration + "ms)");
            }

            notifyCallback(cb -> cb.onInjectionCompleted(chunk.chunkNumber, success, totalDuration));

            return success;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - overallStartTime;
            Log.e(TAG, "💥 Exception injecting timed chunk #" + chunk.chunkNumber + ": " + e.getMessage());
            notifyCallback(cb -> cb.onInjectionError(chunk.chunkNumber, e.getMessage(), chunk.retryCount));
            notifyCallback(cb -> cb.onInjectionCompleted(chunk.chunkNumber, false, duration));
            return false;

        } finally {
            isCurrentlyInjecting.set(false);
        }
    }

    private boolean performTimedInjection(TimedAudioChunk chunk) throws InterruptedException {
        CountDownLatch injectionComplete = new CountDownLatch(1);
        AtomicBoolean injectionSuccess = new AtomicBoolean(false);
        AtomicBoolean callbackReceived = new AtomicBoolean(false);

        CallAudioInjector.InjectionCallback callback = new CallAudioInjector.InjectionCallback() {
            @Override
            public void onInjectionStarted() {
                Log.v(TAG, "▶️ Timed injection #" + chunk.chunkNumber + " started in AudioInjector");
            }

            @Override
            public void onInjectionCompleted(boolean success) {
                if (callbackReceived.getAndSet(true)) {
                    Log.w(TAG, "⚠️ Duplicate completion callback for chunk #" + chunk.chunkNumber);
                    return;
                }

                injectionSuccess.set(success);
                Log.v(TAG, "🏁 Timed injection #" + chunk.chunkNumber + " completed: " + success);
                injectionComplete.countDown();
            }

            @Override
            public void onInjectionError(String error) {
                if (callbackReceived.getAndSet(true)) {
                    Log.w(TAG, "⚠️ Duplicate error callback for chunk #" + chunk.chunkNumber);
                    return;
                }

                Log.e(TAG, "❌ Timed injection #" + chunk.chunkNumber + " error: " + error);
                injectionComplete.countDown();
            }


        };

        // Start injection
        audioInjector.injectAudio16kMono(chunk.data, callback);

        // Wait for completion with timeout
        boolean completed = injectionComplete.await(INJECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        if (!completed) {
            Log.w(TAG, "⏰ Timed injection #" + chunk.chunkNumber + " timeout after " +
                    INJECTION_TIMEOUT_MS + "ms");
            return false;
        }

        return injectionSuccess.get();
    }

    // ============================================================================
    // UTILITY METHODS
    // ============================================================================

    private int dropOldChunks() {
        int dropped = 0;
        while (injectionQueue.size() >= MAX_QUEUE_SIZE / 2 && !injectionQueue.isEmpty()) {
            TimedAudioChunk droppedChunk = injectionQueue.poll();
            if (droppedChunk != null) {
                dropped++;
                totalChunksDropped.incrementAndGet();
            }
        }

        if (dropped > 0) {
            Log.w(TAG, "🗑️ Queue overflow: dropped " + dropped + " old chunks");
        }

        return dropped;
    }

    // ============================================================================
    // MONITORING AND STATISTICS
    // ============================================================================

    private void startMonitoring() {
        monitoringExecutor.execute(() -> {
            Log.d(TAG, "📊 Starting enhanced monitoring thread");

            while (!isShutdown.get()) {
                try {
                    Thread.sleep(5000);

                    if (!isShutdown.get()) {
                        InjectionStatistics stats = new InjectionStatistics(this);
                        notifyCallback(cb -> cb.onStatisticsUpdate(stats));
                        logPeriodicStatus(stats);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error in monitoring thread: " + e.getMessage());
                }
            }

            Log.d(TAG, "📊 Monitoring thread stopped");
        });
    }

    private void logPeriodicStatus(InjectionStatistics stats) {
        if (stats.chunksQueued > 0) {
            Log.d(TAG, "📊 Enhanced Status Update:");
            Log.d(TAG, "  📦 Queued: " + stats.chunksQueued + ", Injected: " + stats.chunksInjected +
                    ", Dropped: " + stats.chunksDropped + ", Queue: " + stats.currentQueueSize);
            Log.d(TAG, "  ✅ Success Rate: " + String.format("%.1f%%", stats.successRate) +
                    ", Avg Time: " + String.format("%.0fms", stats.averageInjectionTime));
            Log.d(TAG, "  🎵 Audio: " + (stats.totalAudioDuration / 1000) + "s, " +
                    "Delays: " + (stats.totalDelayTime / 1000) + "s, " +
                    "Ratio: " + String.format("%.1f%%", stats.delayToAudioRatio * 100));
            Log.d(TAG, "  🔄 Processing: " + stats.isProcessing + ", Injecting: " + stats.isInjecting);
        }
    }

    // ============================================================================
    // PUBLIC CONTROL METHODS
    // ============================================================================

    public void pause() {
        isPaused.set(true);
        Log.d(TAG, "⏸️ Timed sequential injection paused");
    }

    public void resume() {
        boolean wasPaused = isPaused.getAndSet(false);
        if (wasPaused) {
            Log.d(TAG, "▶️ Timed sequential injection resumed");
            if (!injectionQueue.isEmpty() && !isProcessing.get()) {
                startProcessing();
            }
        }
    }

    public void clearQueue() {
        int cleared = 0;
        while (!injectionQueue.isEmpty()) {
            if (injectionQueue.poll() != null) {
                cleared++;
            }
        }

        if (cleared > 0) {
            totalChunksDropped.addAndGet(cleared);
            Log.d(TAG, "🗑️ Cleared " + cleared + " timed chunks from queue");
        }
    }

    public String getStatus() {
        InjectionStatistics stats = new InjectionStatistics(this);

        return String.format(
                "Enhanced Sequential Injector Status:\n" +
                        "  State: %s%s%s\n" +
                        "  Queue: %d/%d chunks\n" +
                        "  Progress: %d queued → %d injected → %d dropped\n" +
                        "  Success Rate: %.1f%% (Retries: %d)\n" +
                        "  Data: %.1fKB queued → %.1fKB injected\n" +
                        "  Timing: %.1fs audio, %.1fs delays (%.1f%% ratio)\n" +
                        "  Performance: %.0fms avg injection, %dms avg delay\n" +
                        "  Last injection: %s",

                stats.isProcessing ? "PROCESSING" : "IDLE",
                stats.isInjecting ? " + INJECTING" : "",
                isPaused.get() ? " (PAUSED)" : "",

                stats.currentQueueSize, MAX_QUEUE_SIZE,
                stats.chunksQueued, stats.chunksInjected, stats.chunksDropped,
                stats.successRate, stats.retryCount,
                stats.bytesQueued / 1024.0, stats.bytesInjected / 1024.0,
                stats.totalAudioDuration / 1000.0, stats.totalDelayTime / 1000.0,
                stats.delayToAudioRatio * 100,
                stats.averageInjectionTime, stats.averageDelayPerChunk,
                stats.timeSinceLastInjection >= 0 ? stats.timeSinceLastInjection + "ms ago" : "NEVER"
        );
    }

    public InjectionStatistics getStatistics() {
        return new InjectionStatistics(this);
    }

    public boolean isProcessing() {
        return isProcessing.get();
    }

    public boolean isInjecting() {
        return isCurrentlyInjecting.get();
    }

    public int getQueueSize() {
        return injectionQueue.size();
    }

    public boolean isPaused() {
        return isPaused.get();
    }

    private void notifyCallback(java.util.function.Consumer<SequentialInjectionCallback> action) {
        if (callback != null) {
            statusHandler.post(() -> {
                try {
                    action.accept(callback);
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error in callback: " + e.getMessage());
                }
            });
        }
    }

    public void cleanup() {
        if (isShutdown.getAndSet(true)) {
            Log.w(TAG, "⚠️ Already shut down");
            return;
        }

        Log.d(TAG, "🧹 Shutting down enhanced sequential injector...");

        isPaused.set(true);

        // Wait for current injection with timeout
        long shutdownStart = System.currentTimeMillis();
        while (isCurrentlyInjecting.get() && (System.currentTimeMillis() - shutdownStart) < 5000) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        int remainingChunks = injectionQueue.size();
        clearQueue();

        injectionExecutor.shutdown();
        monitoringExecutor.shutdown();

        try {
            if (!injectionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                injectionExecutor.shutdownNow();
            }
            if (!monitoringExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                monitoringExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            injectionExecutor.shutdownNow();
            monitoringExecutor.shutdownNow();
        }

        // Final statistics
        InjectionStatistics finalStats = new InjectionStatistics(this);

        Log.d(TAG, "📊 Final Enhanced Statistics:");
        Log.d(TAG, "  📦 Total chunks: " + finalStats.chunksQueued + " queued, " +
                finalStats.chunksInjected + " injected, " + finalStats.chunksDropped + " dropped");
        Log.d(TAG, "  💾 Total data: " + (finalStats.bytesQueued / 1024) + "KB queued, " +
                (finalStats.bytesInjected / 1024) + "KB injected");
        Log.d(TAG, "  🎵 Audio time: " + (finalStats.totalAudioDuration / 1000) + "s");
        Log.d(TAG, "  ⏳ Delay time: " + (finalStats.totalDelayTime / 1000) + "s");
        Log.d(TAG, "  ✅ Success rate: " + String.format("%.1f%%", finalStats.successRate));
        Log.d(TAG, "  ⱖ️ Total time: " + (finalStats.totalProcessingTime / 1000) + "s");
        Log.d(TAG, "  🗑️ Cleanup: " + remainingChunks + " remaining chunks cleared");

        Log.d(TAG, "✅ Enhanced sequential injector cleanup completed");
    }
}