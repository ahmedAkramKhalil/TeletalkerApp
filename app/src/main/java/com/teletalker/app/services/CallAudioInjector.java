package com.teletalker.app.services;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CallAudioInjector with Streaming Mode Support
 *
 * Uses streaming mode to avoid gaps between audio chunks
 */
public class CallAudioInjector {

    private static final String TAG = "CallAudioInjector";

    // Script paths
    private static final String SCRIPTS_DIR = "/data/adb/modules/com.teletalker.app/scripts";
    private static final String INJECT_SCRIPT = SCRIPTS_DIR + "/inject_stream.sh";

    // Streaming pipe path (must match script)
    private static final String STREAM_PIPE = "/data/local/tmp/call_injector/audio_stream.pipe";

    // Audio configuration
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNELS = 1;
    private static final int BITS_PER_SAMPLE = 16;

    // Instance management
    private static final AtomicInteger instanceCounter = new AtomicInteger(0);
    private final int instanceId;
    private final String instanceTag;

    // Threading and state
    private final Context context;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final AtomicBoolean isInjecting = new AtomicBoolean(false);
    private final AtomicBoolean streamingModeActive = new AtomicBoolean(false);

    // Global synchronization
    private static final Object INJECTION_LOCK = new Object();
    private static volatile boolean globalInjectionRunning = false;
    private static Process streamingProcess = null;

    public interface InjectionCallback {
        void onInjectionStarted();
        void onInjectionCompleted(boolean success);
        void onInjectionError(String error);
        default void onInjectionProgress(String output) {}
        default void onStreamingStarted() {}
        default void onStreamingStopped() {}
    }

    public CallAudioInjector(Context context) {
        this.context = context;
        this.instanceId = instanceCounter.incrementAndGet();
        this.instanceTag = TAG + "-" + instanceId;

        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AudioInjector-" + instanceId);
            t.setDaemon(true);
            return t;
        });

        this.mainHandler = new Handler(Looper.getMainLooper());

        Log.i(instanceTag, "CallAudioInjector with streaming mode created");
    }

    /**
     * Start streaming mode - creates persistent pipe for continuous injection
     */
    public void startStreamingMode(InjectionCallback callback) {
        Log.d(instanceTag, "Starting streaming mode");

        executor.execute(() -> {
            synchronized (INJECTION_LOCK) {
                if (streamingModeActive.get() || streamingProcess != null) {
                    Log.w(instanceTag, "Streaming mode already active");
                    if (callback != null) {
                        mainHandler.post(() -> callback.onInjectionError("Streaming mode already active"));
                    }
                    return;
                }

                try {
                    // Start streaming script
                    String[] command = {"su", "-c", INJECT_SCRIPT + " stream"};

                    Log.d(instanceTag, "Starting streaming process: " + String.join(" ", command));

                    streamingProcess = Runtime.getRuntime().exec(command);
                    streamingModeActive.set(true);
                    globalInjectionRunning = true;

                    // Monitor streaming process
                    Thread streamMonitor = new Thread(() -> {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(streamingProcess.getInputStream()));
                             BufferedReader errorReader = new BufferedReader(new InputStreamReader(streamingProcess.getErrorStream()))) {

                            String line;

                            // Read stdout
                            while ((line = reader.readLine()) != null && streamingModeActive.get()) {
                                final String outputLine = line;
                                Log.d(instanceTag, "Stream output: " + outputLine);

                                if (callback != null) {
                                    mainHandler.post(() -> callback.onInjectionProgress("OUT: " + outputLine));
                                }
                            }

                            // Read stderr
                            while ((line = errorReader.readLine()) != null && streamingModeActive.get()) {
                                final String errorLine = line;
                                Log.w(instanceTag, "Stream error: " + errorLine);

                                if (callback != null) {
                                    mainHandler.post(() -> callback.onInjectionProgress("ERR: " + errorLine));
                                }
                            }

                        } catch (IOException e) {
                            Log.e(instanceTag, "Error reading streaming process output: " + e.getMessage());
                        }
                    }, "StreamMonitor-" + instanceId);

                    streamMonitor.setDaemon(true);
                    streamMonitor.start();

                    // Wait a bit for pipe to be created
                    Thread.sleep(1000);

                    // Check if pipe was created
                    if (new File(STREAM_PIPE).exists()) {
                        Log.d(instanceTag, "Streaming mode started successfully");
                        if (callback != null) {
                            mainHandler.post(() -> {
                                callback.onInjectionStarted();
                                callback.onStreamingStarted();
                            });
                        }
                    } else {
                        throw new IOException("Stream pipe was not created");
                    }

                } catch (Exception e) {
                    Log.e(instanceTag, "Failed to start streaming mode: " + e.getMessage());

                    streamingModeActive.set(false);
                    globalInjectionRunning = false;

                    if (streamingProcess != null) {
                        streamingProcess.destroyForcibly();
                        streamingProcess = null;
                    }

                    if (callback != null) {
                        mainHandler.post(() -> callback.onInjectionError("Failed to start streaming: " + e.getMessage()));
                    }
                }
            }
        });
    }

    /**
     * Stop streaming mode
     */
    public void stopStreamingMode(InjectionCallback callback) {
        Log.d(instanceTag, "Stopping streaming mode");

        executor.execute(() -> {
            synchronized (INJECTION_LOCK) {
                try {
                    streamingModeActive.set(false);

                    // Remove pipe to signal script to stop
                    if (new File(STREAM_PIPE).exists()) {
                        new File(STREAM_PIPE).delete();
                    }

                    // Stop streaming process
                    if (streamingProcess != null) {
                        streamingProcess.destroyForcibly();
                        streamingProcess.waitFor(5, TimeUnit.SECONDS);
                        streamingProcess = null;
                    }

                    globalInjectionRunning = false;

                    Log.d(instanceTag, "Streaming mode stopped");

                    if (callback != null) {
                        mainHandler.post(() -> {
                            callback.onStreamingStopped();
                            callback.onInjectionCompleted(true);
                        });
                    }

                } catch (Exception e) {
                    Log.e(instanceTag, "Error stopping streaming mode: " + e.getMessage());

                    if (callback != null) {
                        mainHandler.post(() -> callback.onInjectionError("Error stopping streaming: " + e.getMessage()));
                    }
                }
            }
        });
    }

    /**
     * Inject audio data using streaming mode
     */
    public void injectAudio16kMono(byte[] pcmData, InjectionCallback callback) {
        if (pcmData == null || pcmData.length == 0) {
            Log.w(instanceTag, "No audio data provided");
            if (callback != null) {
                mainHandler.post(() -> callback.onInjectionError("No audio data provided"));
            }
            return;
        }

        if (!streamingModeActive.get()) {
            Log.w(instanceTag, "Streaming mode not active, cannot inject audio");
            if (callback != null) {
                mainHandler.post(() -> callback.onInjectionError("Streaming mode not active"));
            }
            return;
        }

        Log.d(instanceTag, "Injecting audio to stream: " + pcmData.length + " bytes");

        executor.execute(() -> {
            try {
                // Write PCM data directly to stream pipe
                boolean success = writeToStreamPipe(pcmData);

                if (success) {
                    Log.d(instanceTag, "Audio written to stream successfully");
                    if (callback != null) {
                        mainHandler.post(() -> callback.onInjectionCompleted(true));
                    }
                } else {
                    Log.e(instanceTag, "Failed to write audio to stream");
                    if (callback != null) {
                        mainHandler.post(() -> callback.onInjectionError("Failed to write to stream"));
                    }
                }

            } catch (Exception e) {
                Log.e(instanceTag, "Error injecting audio: " + e.getMessage());
                if (callback != null) {
                    mainHandler.post(() -> callback.onInjectionError("Injection error: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * Write PCM data directly to the streaming pipe
     */
    private boolean writeToStreamPipe(byte[] pcmData) {
        try {
            File pipeFile = new File(STREAM_PIPE);

            if (!pipeFile.exists()) {
                Log.e(instanceTag, "Stream pipe does not exist: " + STREAM_PIPE);
                return false;
            }

            // Write raw PCM data directly to pipe
            try (FileOutputStream pipeOutput = new FileOutputStream(pipeFile)) {
                pipeOutput.write(pcmData);
                pipeOutput.flush();

                Log.v(instanceTag, "Wrote " + pcmData.length + " bytes to stream pipe");
                return true;

            } catch (IOException e) {
                Log.e(instanceTag, "Error writing to pipe: " + e.getMessage());
                return false;
            }

        } catch (Exception e) {
            Log.e(instanceTag, "Error accessing stream pipe: " + e.getMessage());
            return false;
        }
    }

    /**
     * Alternative: inject using temporary file (fallback method)
     */
    public void injectAudio16kMonoFile(byte[] pcmData, InjectionCallback callback) {
        if (pcmData == null || pcmData.length == 0) {
            Log.w(instanceTag, "No audio data provided");
            if (callback != null) {
                mainHandler.post(() -> callback.onInjectionError("No audio data provided"));
            }
            return;
        }

        Log.d(instanceTag, "Using file-based injection: " + pcmData.length + " bytes");

        executor.execute(() -> executeFileInjection(pcmData, callback));
    }

    private void executeFileInjection(byte[] pcmData, InjectionCallback callback) {
        File tempWav = null;

        try {
            // Validate call state
            if (!isInCall()) {
                throw new IllegalStateException("No active call detected");
            }

            if (callback != null) {
                mainHandler.post(callback::onInjectionStarted);
            }

            // Create temporary WAV file
            tempWav = createTempWavFile(pcmData);
            Log.d(instanceTag, "Created temp WAV: " + tempWav.getAbsolutePath() + " (" + tempWav.length() + " bytes)");

            // Execute injection using script
            boolean success = executeInjectionScript(tempWav.getAbsolutePath(), callback);

            Log.d(instanceTag, "File injection completed: " + (success ? "SUCCESS" : "FAILED"));

            if (callback != null) {
                final boolean finalSuccess = success;
                mainHandler.post(() -> callback.onInjectionCompleted(finalSuccess));
            }

        } catch (Exception e) {
            Log.e(instanceTag, "File injection failed", e);
            if (callback != null) {
                mainHandler.post(() -> callback.onInjectionError("Injection failed: " + e.getMessage()));
            }
        } finally {
            // Cleanup
            if (tempWav != null && tempWav.exists()) {
                boolean deleted = tempWav.delete();
                Log.d(instanceTag, "Temp file cleanup: " + deleted);
            }
        }
    }

    private boolean executeInjectionScript(String audioFilePath, InjectionCallback callback) {
        try {
            String[] command = {
                    "su", "-c",
                    INJECT_SCRIPT + " file '" + audioFilePath.replace("'", "'\"'\"'") + "'"
            };

            Log.d(instanceTag, "Executing: " + String.join(" ", command));

            Process process = Runtime.getRuntime().exec(command);

            // Monitor output
            StringBuilder output = new StringBuilder();
            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                     BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                    String line;

                    while ((line = reader.readLine()) != null) {
                        output.append("OUT: ").append(line).append("\n");
                        Log.d(instanceTag, "Script output: " + line);

                        if (callback != null) {
                            final String outputLine = line;
                            mainHandler.post(() -> callback.onInjectionProgress("OUT: " + outputLine));
                        }
                    }

                    while ((line = errorReader.readLine()) != null) {
                        output.append("ERR: ").append(line).append("\n");
                        Log.w(instanceTag, "Script error: " + line);

                        if (callback != null) {
                            final String errorLine = line;
                            mainHandler.post(() -> callback.onInjectionProgress("ERR: " + errorLine));
                        }
                    }

                } catch (IOException e) {
                    Log.e(instanceTag, "Error reading script output", e);
                }
            }, "ScriptReader-" + instanceId);

            outputReader.start();

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);

            if (!finished) {
                Log.w(instanceTag, "Script timeout, force killing");
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                return false;
            }

            outputReader.join(3000);

            int exitCode = process.exitValue();
            Log.d(instanceTag, "Script exit code: " + exitCode);

            return exitCode == 0;

        } catch (Exception e) {
            Log.e(instanceTag, "Script execution error", e);
            return false;
        }
    }

    private File createTempWavFile(byte[] pcmData) throws IOException {
        String fileName = "inject_" + instanceId + "_" + System.currentTimeMillis() + ".wav";
        File tempFile = new File(context.getCacheDir(), fileName);

        writeWavFile(pcmData, CHANNELS, SAMPLE_RATE, BITS_PER_SAMPLE, tempFile);

        return tempFile;
    }

    private void writeWavFile(byte[] pcmData, int numChannels, int sampleRate, int bitsPerSample, File wavFile) throws IOException {
        int byteRate = sampleRate * numChannels * bitsPerSample / 8;
        int blockAlign = numChannels * bitsPerSample / 8;
        int dataLength = pcmData.length;
        int chunkSize = 36 + dataLength;

        try (FileOutputStream out = new FileOutputStream(wavFile)) {
            out.write(new byte[] {
                    'R','I','F','F',
                    (byte) chunkSize, (byte)(chunkSize >> 8), (byte)(chunkSize >> 16), (byte)(chunkSize >> 24),
                    'W','A','V','E',
                    'f','m','t',' ',
                    16,0,0,0,
                    1,0,
                    (byte) numChannels, 0,
                    (byte) sampleRate, (byte)(sampleRate >> 8), (byte)(sampleRate >> 16), (byte)(sampleRate >> 24),
                    (byte) byteRate, (byte)(byteRate >> 8), (byte)(byteRate >> 16), (byte)(byteRate >> 24),
                    (byte) blockAlign, 0,
                    (byte) bitsPerSample, 0,
                    'd','a','t','a',
                    (byte) dataLength, (byte)(dataLength >> 8), (byte)(dataLength >> 16), (byte)(dataLength >> 24)
            });

            out.write(pcmData);
            out.flush();
        }
    }

    private boolean isInCall() {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return false;

            int mode = am.getMode();
            return (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION);
        } catch (Exception e) {
            Log.e(instanceTag, "Error checking call state", e);
            return false;
        }
    }

    // Test methods
    public void testInjectionSystem(InjectionCallback callback) {
        Log.i(instanceTag, "Testing injection system");

        executor.execute(() -> {
            try {
                if (callback != null) {
                    mainHandler.post(callback::onInjectionStarted);
                }

                String[] command = {"su", "-c", SCRIPTS_DIR + "/test_injection.sh"};
                Process testProcess = Runtime.getRuntime().exec(command);

                int result = testProcess.waitFor();
                boolean success = result == 0;

                Log.i(instanceTag, "Test completed: " + (success ? "SUCCESS" : "FAILED"));

                if (callback != null) {
                    mainHandler.post(() -> callback.onInjectionCompleted(success));
                }

            } catch (Exception e) {
                Log.e(instanceTag, "Test failed", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onInjectionError("Test failed: " + e.getMessage()));
                }
            }
        });
    }

    public void testWithTone(int frequency, int durationMs, InjectionCallback callback) {
        byte[] toneData = generateTestTone(frequency, durationMs);

        if (streamingModeActive.get()) {
            injectAudio16kMono(toneData, callback);
        } else {
            injectAudio16kMonoFile(toneData, callback);
        }
    }

    private byte[] generateTestTone(double frequency, long durationMs) {
        int totalSamples = (int) ((SAMPLE_RATE * durationMs) / 1000);
        byte[] audioData = new byte[totalSamples * 2];

        for (int i = 0; i < totalSamples; i++) {
            double time = i / (double) SAMPLE_RATE;
            short sample = (short) (Math.sin(2 * Math.PI * frequency * time) * 8000);
            audioData[i * 2] = (byte) (sample & 0xFF);
            audioData[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        return audioData;
    }

    public boolean isStreamingModeActive() {
        return streamingModeActive.get();
    }

    public boolean isCurrentlyInjecting() {
        return isInjecting.get() || globalInjectionRunning;
    }

    public String getStatus() {
        if (streamingModeActive.get()) {
            return String.format("Instance %d: STREAMING_ACTIVE", instanceId);
        } else {
            return String.format("Instance %d: %s",
                    instanceId,
                    isInjecting.get() ? "INJECTING" : "IDLE");
        }
    }

    public void cleanup() {
        Log.i(instanceTag, "Cleaning up CallAudioInjector");

        // Stop streaming mode if active
        if (streamingModeActive.get()) {
            stopStreamingMode(null);
        }

        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    Log.w(instanceTag, "Executor did not terminate, forcing shutdown");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        Log.i(instanceTag, "Cleanup completed");
    }
}