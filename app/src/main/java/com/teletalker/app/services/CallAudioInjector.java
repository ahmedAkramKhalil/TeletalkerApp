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

/**
 * CallAudioInjector with COMPLETE FIXES and Enhancements
 *
 * FIXES APPLIED:
 * ✅ Enhanced call state detection
 * ✅ Better audio validation
 * ✅ Injection monitoring and progress callbacks
 * ✅ Concurrent injection prevention
 * ✅ Optimized WAV file creation
 * ✅ Enhanced script execution with timeout handling
 * ✅ Comprehensive error handling and logging
 */
public class CallAudioInjector {

    private static final String TAG = "CallAudioInjector";
    private static final String INJECT_SCRIPT = "/data/adb/modules/com.teletalker.app/inject_audio.sh";

    private final Context context;
    private ExecutorService executor;
    private final Handler mainHandler;

    // NEW: Injection monitoring
    private volatile boolean isCurrentlyInjecting = false;
    private volatile long lastInjectionTime = 0;
    private final Object injectionLock = new Object();

    public interface InjectionCallback {
        void onInjectionStarted();
        void onInjectionCompleted(boolean success);
        void onInjectionError(String error);

        // NEW: Enhanced progress callbacks
        default void onInjectionProgress(long elapsedMs, long expectedMs) {}
        default void onScriptOutput(String output) {}
        default void onAudioValidated(long durationMs, int sampleRate) {}
        default void onCallStateValidated(String audioMode) {}
    }

    public CallAudioInjector(Context context) {
        this.context = context;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    // === MAIN WORKING METHOD (ORIGINAL) ===
    public void injectAudio16kMono(byte[] pcmData, InjectionCallback callback) {
        if (pcmData == null || pcmData.length == 0) {
            if (callback != null) {
                mainHandler.post(() -> callback.onInjectionError("No audio data provided"));
            }
            return;
        }

        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            Log.d(TAG, "🔄 Recreating executor for injection");
            executor = Executors.newSingleThreadExecutor();
        }


        executor.execute(() -> {
            File tempWav = null;
            try {
                Log.d(TAG, "🎧 Starting audio injection: " + pcmData.length + " bytes");

                // Validate call state
                if (!isInCall()) {
                    throw new IllegalStateException("No active call detected");
                }

                // Create WAV file
                tempWav = File.createTempFile("ai_audio_", ".wav", context.getCacheDir());
                writeWavFile(pcmData, 1, 16000, 16, tempWav);

                Log.d(TAG, "📁 WAV file created: " + tempWav.length() + " bytes");

                if (callback != null) {
                    mainHandler.post(callback::onInjectionStarted);
                }

                // Execute injection
                boolean success = executeInjectionScript(tempWav.getAbsolutePath());

                if (callback != null) {
                    final boolean finalSuccess = success;
                    if (success) {
                        mainHandler.post(() -> callback.onInjectionCompleted(finalSuccess));
                    } else {
                        mainHandler.post(() -> callback.onInjectionError("Script execution failed"));
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "💥 Audio injection failed", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onInjectionError("Failed: " + e.getMessage()));
                }
            } finally {
                // Cleanup
                if (tempWav != null && tempWav.exists()) {
                    boolean deleted = tempWav.delete();
                    Log.d(TAG, "🗑️ Temp file cleanup: " + deleted);
                }
            }
        });
    }

    // === ENHANCED METHOD FOR PRECISE TIMING ===
    public void injectAudioWithPreciseTiming(byte[] pcmData, long expectedDurationMs, long timeoutMs, InjectionCallback callback) {
        if (pcmData == null || pcmData.length == 0) {
            if (callback != null) {
                mainHandler.post(() -> callback.onInjectionError("No audio data provided"));
            }
            return;
        }

        // Prevent concurrent injections
        synchronized (injectionLock) {
            if (isCurrentlyInjecting) {
                Log.w(TAG, "⚠️ Injection already in progress, rejecting new request");
                if (callback != null) {
                    mainHandler.post(() -> callback.onInjectionError("Injection already in progress"));
                }
                return;
            }
            isCurrentlyInjecting = true;
        }

        executor.execute(() -> {
            File tempWav = null;
            long startTime = System.currentTimeMillis();

            try {
                Log.d(TAG, "🎯 PRECISE INJECTION START:");
                Log.d(TAG, "  📊 Audio: " + pcmData.length + " bytes");
                Log.d(TAG, "  ⏱️ Expected: " + expectedDurationMs + "ms");
                Log.d(TAG, "  ⏰ Timeout: " + timeoutMs + "ms");

                // Enhanced call state validation
                if (!isInCallEnhanced()) {
                    throw new IllegalStateException("No active call detected for injection");
                }

                // Validate audio data quality
                validateAudioData(pcmData, callback);

                // Create optimized WAV file
                tempWav = createOptimizedWavFile(pcmData);
                Log.d(TAG, "📁 Optimized WAV created: " + tempWav.length() + " bytes");

                if (callback != null) {
                    mainHandler.post(callback::onInjectionStarted);
                }

                // Execute with enhanced monitoring
                boolean success = executeInjectionWithMonitoring(tempWav.getAbsolutePath(),
                        expectedDurationMs, timeoutMs, callback);

                // Calculate final timing
                long actualDuration = System.currentTimeMillis() - startTime;
                double accuracy = expectedDurationMs > 0 ? (actualDuration * 100.0 / expectedDurationMs) : 0;

                Log.d(TAG, "📊 INJECTION COMPLETED:");
                Log.d(TAG, "  ✅ Success: " + success);
                Log.d(TAG, "  ⏱️ Expected: " + expectedDurationMs + "ms");
                Log.d(TAG, "  ⏱️ Actual: " + actualDuration + "ms");
                Log.d(TAG, "  📈 Accuracy: " + String.format("%.1f", accuracy) + "%");

                lastInjectionTime = System.currentTimeMillis();

                if (callback != null) {
                    final boolean finalSuccess = success;
                    mainHandler.post(() -> callback.onInjectionCompleted(finalSuccess));
                }

            } catch (Exception e) {
                long actualDuration = System.currentTimeMillis() - startTime;
                Log.e(TAG, "💥 Injection failed after " + actualDuration + "ms", e);

                if (callback != null) {
                    mainHandler.post(() -> callback.onInjectionError("Failed after " + actualDuration + "ms: " + e.getMessage()));
                }
            } finally {
                // Always cleanup
                synchronized (injectionLock) {
                    isCurrentlyInjecting = false;
                }

                if (tempWav != null && tempWav.exists()) {
                    boolean deleted = tempWav.delete();
                    Log.d(TAG, "🗑️ Temp file cleanup: " + deleted);
                }
            }
        });
    }

    // === ENHANCED CALL STATE DETECTION ===
    private boolean isInCall() {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) {
                Log.w(TAG, "⚠️ AudioManager not available");
                return false;
            }

            int mode = am.getMode();
            boolean inCall = (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION);

            Log.d(TAG, "📞 Call state: " + getAudioModeString(mode) + " (In call: " + inCall + ")");
            return inCall;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error checking call state", e);
            return false;
        }
    }

    // NEW: Enhanced call state detection with detailed info
    public boolean isInCallEnhanced() {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) {
                Log.w(TAG, "⚠️ AudioManager not available");
                return false;
            }

            int mode = am.getMode();
            boolean inCall = (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION);

            // Additional checks for better validation
            boolean isSpeakerphoneOn = am.isSpeakerphoneOn();
            boolean isBluetoothA2dpOn = am.isBluetoothA2dpOn();
            boolean isMicrophoneMute = am.isMicrophoneMute();

            Log.d(TAG, "📞 Enhanced call state:");
            Log.d(TAG, "  🎯 Mode: " + getAudioModeString(mode) + " (In call: " + inCall + ")");
            Log.d(TAG, "  🔊 Speakerphone: " + isSpeakerphoneOn);
            Log.d(TAG, "  🎧 Bluetooth A2DP: " + isBluetoothA2dpOn);
            Log.d(TAG, "  🎤 Mic muted: " + isMicrophoneMute);

            return inCall;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error checking enhanced call state", e);
            return false;
        }
    }

    // NEW: Validate audio data quality with detailed analysis
    private void validateAudioData(byte[] pcmData, InjectionCallback callback) throws IllegalArgumentException {
        if (pcmData.length < 1600) { // Less than 0.1s at 16kHz mono
            throw new IllegalArgumentException("Audio data too short: " + pcmData.length + " bytes");
        }

        // Check for completely silent audio
        boolean hasAudio = false;
        int samplesAboveThreshold = 0;
        int totalSamples = pcmData.length / 2;
        int maxAmplitude = 0;
        long rmsSum = 0;

        for (int i = 0; i < pcmData.length - 1; i += 2) {
            short sample = (short) ((pcmData[i + 1] << 8) | (pcmData[i] & 0xFF));
            int amplitude = Math.abs(sample);

            rmsSum += sample * sample;

            if (amplitude > 100) {
                hasAudio = true;
                samplesAboveThreshold++;
            }

            if (amplitude > maxAmplitude) {
                maxAmplitude = amplitude;
            }
        }

        if (!hasAudio) {
            throw new IllegalArgumentException("Audio data appears to be silent");
        }

        // Calculate audio quality metrics
        double audioPercentage = totalSamples > 0 ? (double) samplesAboveThreshold / totalSamples : 0;
        double rms = Math.sqrt((double) rmsSum / totalSamples);
        long durationMs = (totalSamples * 1000L) / 16000; // 16kHz sample rate

        Log.d(TAG, "✅ Audio validation passed:");
        Log.d(TAG, "  📊 Duration: " + durationMs + "ms");
        Log.d(TAG, "  🎵 Audio samples: " + samplesAboveThreshold + "/" + totalSamples + " (" + String.format("%.1f", audioPercentage * 100) + "%)");
        Log.d(TAG, "  📈 Max amplitude: " + maxAmplitude);
        Log.d(TAG, "  📊 RMS: " + String.format("%.1f", rms));

        // Notify callback with validation results
        if (callback != null) {
            mainHandler.post(() -> callback.onAudioValidated(durationMs, 16000));
        }
    }

    // IMPROVED: Optimized WAV file creation
    private File createOptimizedWavFile(byte[] pcmData) throws IOException {
        File wavFile = File.createTempFile("precise_audio_", ".wav", context.getCacheDir());

        // Use optimized parameters for call injection
        int sampleRate = 16000; // Optimal for call audio
        int numChannels = 1;    // Mono
        int bitsPerSample = 16; // 16-bit

        writeWavFileOptimized(pcmData, numChannels, sampleRate, bitsPerSample, wavFile);
        return wavFile;
    }

    // === WAV FILE CREATION (ORIGINAL) ===
    private void writeWavFile(byte[] pcmData, int numChannels, int sampleRate, int bitsPerSample, File wavFile) throws IOException {
        int byteRate = sampleRate * numChannels * bitsPerSample / 8;
        int blockAlign = numChannels * bitsPerSample / 8;
        int dataLength = pcmData.length;
        int chunkSize = 36 + dataLength;

        try (FileOutputStream out = new FileOutputStream(wavFile)) {
            // Write WAV header (44 bytes)
            out.write(new byte[] {
                    'R','I','F','F',
                    (byte) (chunkSize      ), (byte)(chunkSize >>  8), (byte)(chunkSize >> 16), (byte)(chunkSize >> 24),
                    'W','A','V','E',
                    'f','m','t',' ',
                    16,0,0,0,                 // Subchunk1Size (16 for PCM)
                    1,0,                      // AudioFormat (1 for PCM)
                    (byte) numChannels, 0,    // NumChannels
                    (byte) (sampleRate      ), (byte) (sampleRate >> 8), (byte) (sampleRate >> 16), (byte) (sampleRate >> 24),
                    (byte) (byteRate      ), (byte) (byteRate >>  8), (byte) (byteRate >> 16), (byte) (byteRate >> 24),
                    (byte) blockAlign, 0,     // BlockAlign
                    (byte) bitsPerSample, 0,  // BitsPerSample
                    'd','a','t','a',
                    (byte) (dataLength      ), (byte) (dataLength >>  8), (byte) (dataLength >> 16), (byte) (dataLength >> 24)
            });
            out.write(pcmData);
            out.flush();
        }
    }

    // IMPROVED: Optimized WAV writing with better header structure
    private void writeWavFileOptimized(byte[] pcmData, int numChannels, int sampleRate, int bitsPerSample, File wavFile) throws IOException {
        int byteRate = sampleRate * numChannels * bitsPerSample / 8;
        int blockAlign = numChannels * bitsPerSample / 8;
        int dataLength = pcmData.length;
        int chunkSize = 36 + dataLength;

        try (FileOutputStream out = new FileOutputStream(wavFile)) {
            // Write optimized WAV header with proper endianness
            writeInt(out, 0x46464952);      // "RIFF"
            writeInt(out, chunkSize);       // File size - 8
            writeInt(out, 0x45564157);      // "WAVE"
            writeInt(out, 0x20746d66);      // "fmt "
            writeInt(out, 16);              // Subchunk1Size
            writeShort(out, (short) 1);     // AudioFormat (PCM)
            writeShort(out, (short) numChannels);
            writeInt(out, sampleRate);
            writeInt(out, byteRate);
            writeShort(out, (short) blockAlign);
            writeShort(out, (short) bitsPerSample);
            writeInt(out, 0x61746164);      // "data"
            writeInt(out, dataLength);

            // Write audio data
            out.write(pcmData);
            out.flush();
        }
    }

    public boolean isReady() {
        return executor != null && !executor.isShutdown() && !executor.isTerminated();

    }


        // Helper methods for optimized WAV writing
    private void writeInt(FileOutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    private void writeShort(FileOutputStream out, short value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    // === SCRIPT EXECUTION (ORIGINAL) ===
    private boolean executeInjectionScript(String audioFilePath) {
        return executeInjectionScript(audioFilePath, 30); // Default 30 second timeout
    }

    private boolean executeInjectionScript(String audioFilePath, long timeoutSeconds) {
        Process process = null;
        try {
            Log.d(TAG, "🚀 Executing injection script with " + timeoutSeconds + "s timeout");

            // Escape the file path properly
            String escapedPath = audioFilePath.replace("'", "'\"'\"'");

            // Execute with proper shell escaping
            String[] command = {"su", "-c", INJECT_SCRIPT + " '" + escapedPath + "' file"};

            process = Runtime.getRuntime().exec(command);

            // Monitor output
            StringBuilder output = new StringBuilder();
            Process finalProcess = process;
            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(finalProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        Log.d(TAG, "SCRIPT: " + line);
                    }
                } catch (IOException e) {
                    Log.w(TAG, "Error reading output: " + e.getMessage());
                }
            });

            outputReader.start();

            // Wait for completion with timeout
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            outputReader.join(2000);

            if (!finished) {
                Log.e(TAG, "❌ Script timed out after " + timeoutSeconds + "s");
                process.destroyForcibly();
                return false;
            }

            int exitCode = process.exitValue();
            Log.d(TAG, "📊 Script completed with exit code: " + exitCode);

            return exitCode == 0;

        } catch (Exception e) {
            Log.e(TAG, "💥 Script execution error", e);
            return false;
        } finally {
            if (process != null) {
                try {
                    process.destroyForcibly();
                } catch (Exception ignored) {}
            }
        }
    }

    // IMPROVED: Enhanced script execution with comprehensive monitoring
    private boolean executeInjectionWithMonitoring(String audioFilePath, long expectedDurationMs,
                                                   long timeoutMs, InjectionCallback callback) {
        Process process = null;
        try {
            Log.d(TAG, "🚀 Executing injection with comprehensive monitoring");
            Log.d(TAG, "  📁 File: " + audioFilePath);
            Log.d(TAG, "  ⏱️ Expected: " + expectedDurationMs + "ms");
            Log.d(TAG, "  ⏰ Timeout: " + timeoutMs + "ms");

            // Prepare enhanced command with better error handling
            String escapedPath = audioFilePath.replace("'", "'\"'\"'");
            String[] command = {"su", "-c", INJECT_SCRIPT + " '" + escapedPath + "' file enhanced"};

            long scriptStartTime = System.currentTimeMillis();
            process = Runtime.getRuntime().exec(command);

            // Enhanced progress monitoring with real-time reporting
            final Process monitoredProcess = process;
            Thread progressMonitor = new Thread(() -> {
                try {
                    long lastReportTime = System.currentTimeMillis();

                    while (!monitoredProcess.waitFor(500, TimeUnit.MILLISECONDS)) {
                        long elapsed = System.currentTimeMillis() - scriptStartTime;
                        long currentTime = System.currentTimeMillis();

                        // Report progress every 1 second
                        if (currentTime - lastReportTime >= 1000) {
                            Log.d(TAG, "⏱️ Injection progress: " + elapsed + "ms / " + expectedDurationMs + "ms (" +
                                    String.format("%.1f", (elapsed * 100.0 / expectedDurationMs)) + "%)");

                            if (callback != null) {
                                mainHandler.post(() -> callback.onInjectionProgress(elapsed, expectedDurationMs));
                            }

                            lastReportTime = currentTime;
                        }

                        // Check if we've exceeded reasonable time
                        if (elapsed > timeoutMs) {
                            Log.w(TAG, "⚠️ Injection timeout exceeded in monitor");
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Log.d(TAG, "Progress monitor interrupted");
                }
            });

            // Enhanced output monitoring with callback notifications
            StringBuilder output = new StringBuilder();
            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(monitoredProcess.getInputStream()));
                     BufferedReader errorReader = new BufferedReader(new InputStreamReader(monitoredProcess.getErrorStream()))) {

                    String line;

                    // Read stdout
                    while ((line = reader.readLine()) != null) {
                        output.append("OUT: ").append(line).append("\n");
                        Log.d(TAG, "SCRIPT OUT: " + line);

                        if (callback != null) {
                            final String outputLine = line;
                            mainHandler.post(() -> callback.onScriptOutput("OUT: " + outputLine));
                        }
                    }

                    // Read stderr
                    while ((line = errorReader.readLine()) != null) {
                        output.append("ERR: ").append(line).append("\n");
                        Log.w(TAG, "SCRIPT ERR: " + line);

                        if (callback != null) {
                            final String errorLine = line;
                            mainHandler.post(() -> callback.onScriptOutput("ERR: " + errorLine));
                        }
                    }

                } catch (IOException e) {
                    Log.w(TAG, "Error reading script output: " + e.getMessage());
                }
            });

            progressMonitor.start();
            outputReader.start();

            // Wait for completion with enhanced timeout handling
            long timeoutSeconds = Math.max(30, timeoutMs / 1000);
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            // Cleanup monitoring threads with timeout
            progressMonitor.interrupt();
            outputReader.join(3000); // Wait up to 3 seconds for output reader

            if (!finished) {
                Log.e(TAG, "❌ Script timed out after " + timeoutSeconds + "s");
                process.destroyForcibly();
                return false;
            }

            int exitCode = process.exitValue();
            long actualDuration = System.currentTimeMillis() - scriptStartTime;

            Log.d(TAG, "📊 Enhanced script execution completed:");
            Log.d(TAG, "  🎯 Exit code: " + exitCode);
            Log.d(TAG, "  ⏱️ Duration: " + actualDuration + "ms");
            Log.d(TAG, "  📝 Output lines: " + output.toString().split("\n").length);

            // Log a summary of output if verbose logging is enabled
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "📝 Complete script output:\n" + output.toString());
            }

            return exitCode == 0;

        } catch (Exception e) {
            Log.e(TAG, "💥 Enhanced script execution error", e);
            return false;
        } finally {
            if (process != null) {
                try {
                    process.destroyForcibly();
                } catch (Exception ignored) {}
            }
        }
    }

    // === UTILITY METHODS ===
    private String getAudioModeString(int mode) {
        switch (mode) {
            case AudioManager.MODE_NORMAL: return "NORMAL";
            case AudioManager.MODE_RINGTONE: return "RINGTONE";
            case AudioManager.MODE_IN_CALL: return "IN_CALL";
            case AudioManager.MODE_IN_COMMUNICATION: return "IN_COMMUNICATION";
            default: return "UNKNOWN(" + mode + ")";
        }
    }

    // === STATUS AND MONITORING METHODS ===

    // NEW: Check if injection is currently active
    public boolean isCurrentlyInjecting() {
        synchronized (injectionLock) {
            return isCurrentlyInjecting;
        }
    }

    // NEW: Get time since last injection
    public long getTimeSinceLastInjection() {
        return lastInjectionTime > 0 ? System.currentTimeMillis() - lastInjectionTime : -1;
    }

    // NEW: Get detailed injection status
    public String getInjectionStatus() {
        synchronized (injectionLock) {
            if (isCurrentlyInjecting) {
                return "ACTIVE";
            } else if (lastInjectionTime > 0) {
                long timeSince = getTimeSinceLastInjection();
                return "IDLE (last: " + timeSince + "ms ago)";
            } else {
                return "NEVER_USED";
            }
        }
    }

    // NEW: Validate injection environment
    public boolean validateInjectionEnvironment() {
        Log.d(TAG, "🔍 VALIDATING INJECTION ENVIRONMENT:");

        boolean valid = true;

        // Check script file existence
        File scriptFile = new File(INJECT_SCRIPT);
        if (!scriptFile.exists()) {
            Log.e(TAG, "❌ Injection script not found: " + INJECT_SCRIPT);
            valid = false;
        } else {
            Log.d(TAG, "✅ Injection script found: " + scriptFile.length() + " bytes");
        }

        // Check root access
        try {
            Process testRoot = Runtime.getRuntime().exec("su -c echo test");
            testRoot.waitFor();
            if (testRoot.exitValue() == 0) {
                Log.d(TAG, "✅ Root access available");
            } else {
                Log.e(TAG, "❌ Root access denied");
                valid = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Root access test failed: " + e.getMessage());
            valid = false;
        }

        // Check call state
        boolean inCall = isInCallEnhanced();
        if (inCall) {
            Log.d(TAG, "✅ Currently in call");
        } else {
            Log.w(TAG, "⚠️ Not currently in call");
            // Don't mark as invalid since this might be tested outside of calls
        }

        // Check temp directory access
        try {
            File tempTest = File.createTempFile("injection_test_", ".tmp", context.getCacheDir());
            tempTest.delete();
            Log.d(TAG, "✅ Temp directory accessible");
        } catch (Exception e) {
            Log.e(TAG, "❌ Temp directory not accessible: " + e.getMessage());
            valid = false;
        }

        Log.d(TAG, "🏁 Environment validation: " + (valid ? "PASSED" : "FAILED"));
        return valid;
    }

    // === TESTING AND DEBUGGING ===

    // NEW: Test injection with a simple tone
    public void testInjection(InjectionCallback callback) {
        Log.d(TAG, "🧪 STARTING INJECTION TEST");

        // Create a 1-second test tone at 800Hz
        byte[] testTone = createTestTone(800, 1000); // 800Hz, 1 second

        injectAudioWithPreciseTiming(testTone, 1000, 5000, new InjectionCallback() {
            @Override
            public void onInjectionStarted() {
                Log.d(TAG, "🧪 Test injection started");
                if (callback != null) callback.onInjectionStarted();
            }

            @Override
            public void onInjectionCompleted(boolean success) {
                Log.d(TAG, "🧪 Test injection completed: " + (success ? "SUCCESS" : "FAILED"));
                if (callback != null) callback.onInjectionCompleted(success);
            }

            @Override
            public void onInjectionError(String error) {
                Log.e(TAG, "🧪 Test injection error: " + error);
                if (callback != null) callback.onInjectionError(error);
            }

            @Override
            public void onInjectionProgress(long elapsedMs, long expectedMs) {
                Log.d(TAG, "🧪 Test progress: " + elapsedMs + "/" + expectedMs + "ms");
                if (callback != null) callback.onInjectionProgress(elapsedMs, expectedMs);
            }

            @Override
            public void onAudioValidated(long durationMs, int sampleRate) {
                Log.d(TAG, "🧪 Test audio validated: " + durationMs + "ms @ " + sampleRate + "Hz");
                if (callback != null) callback.onAudioValidated(durationMs, sampleRate);
            }

            @Override
            public void onScriptOutput(String output) {
                Log.d(TAG, "🧪 Test script: " + output);
                if (callback != null) callback.onScriptOutput(output);
            }
        });
    }

    // NEW: Create test tone for testing
    private byte[] createTestTone(double frequency, long durationMs) {
        int sampleRate = 16000;
        int totalSamples = (int) ((sampleRate * durationMs) / 1000);
        byte[] audioData = new byte[totalSamples * 2]; // 16-bit = 2 bytes per sample

        for (int i = 0; i < totalSamples; i++) {
            double time = i / (double) sampleRate;
            short sample = (short) (Math.sin(2 * Math.PI * frequency * time) * 16000);

            // Convert to little-endian bytes
            audioData[i * 2] = (byte) (sample & 0xFF);
            audioData[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        Log.d(TAG, "🎵 Created test tone: " + frequency + "Hz, " + durationMs + "ms, " + audioData.length + " bytes");
        return audioData;
    }

    // NEW: Log comprehensive status
    public void logStatus() {
        Log.d(TAG, "=== CALL AUDIO INJECTOR STATUS ===");
        Log.d(TAG, "Injection Status: " + getInjectionStatus());
        Log.d(TAG, "Call State: " + (isInCallEnhanced() ? "IN_CALL" : "NOT_IN_CALL"));
        Log.d(TAG, "Environment Valid: " + validateInjectionEnvironment());
        Log.d(TAG, "Last Injection: " + (lastInjectionTime > 0 ?
                getTimeSinceLastInjection() + "ms ago" : "NEVER"));

        // Audio manager details
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                Log.d(TAG, "Audio Mode: " + getAudioModeString(am.getMode()));
                Log.d(TAG, "Volume (Voice Call): " + am.getStreamVolume(AudioManager.STREAM_VOICE_CALL));
                Log.d(TAG, "Volume (Music): " + am.getStreamVolume(AudioManager.STREAM_MUSIC));
            }
        } catch (Exception e) {
            Log.w(TAG, "Error getting audio manager info: " + e.getMessage());
        }

        Log.d(TAG, "=====================================");
    }

    // === CLEANUP ===
    public void cleanup() {
        Log.d(TAG, "🧹 Cleaning up CallAudioInjector");

        synchronized (injectionLock) {
            if (isCurrentlyInjecting) {
                Log.w(TAG, "⚠️ Cleanup called while injection active - waiting...");
                // Wait a bit for current injection to complete
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.w(TAG, "Interrupted while waiting for injection to complete");
                }
            }
        }

        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    Log.w(TAG, "Executor did not terminate gracefully, forcing shutdown");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while waiting for executor termination");
                executor.shutdownNow();
            }
        }

        Log.d(TAG, "✅ CallAudioInjector cleanup completed");
    }
}