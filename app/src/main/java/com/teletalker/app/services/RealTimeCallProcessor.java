package com.teletalker.app.services;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
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

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RealTimeCallProcessor - Integrated version for use with CallDetector service
 *
 * This version removes duplicate call monitoring and relies on CallDetector
 * for call state management and lifecycle control.
 */
public class RealTimeCallProcessor {
    private static final String TAG = "RealTimeCallProcessor";

    // Audio configuration
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int AUDIO_CHUNK_DURATION_MS = 250;
    private static final int AUDIO_CHUNK_SIZE = (SAMPLE_RATE * AUDIO_CHUNK_DURATION_MS / 1000) * 2;

    // Recording modes and settings
    private static final boolean OFFLINE_MODE = false;
    private static final boolean FILE_RECORDING_ONLY = false;
    private static final boolean ENABLE_FILE_RECORDING = true;
    private static final boolean ENABLE_DEBUGGING = true;
    private static final boolean IS_ANDROID_12_PLUS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;

    // Audio sources for different Android versions
    private static final int[] AUDIO_SOURCES_ANDROID_12_PLUS = {
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
    };

    private static final int[] AUDIO_SOURCES_LEGACY = {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_UPLINK,
            MediaRecorder.AudioSource.VOICE_DOWNLINK,
            MediaRecorder.AudioSource.MIC
    };

    // Component instances
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private Context context;
    private MediaRecorder mediaRecorder;
    private ExecutorService executorService;
    private ElevenLabsWebSocketClient elevenLabsClient;
    private Handler mainHandler;

    // State management - controlled by CallDetector
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private final AtomicBoolean isPlayingResponse = new AtomicBoolean(false);
    private final AtomicBoolean isFileRecording = new AtomicBoolean(false);
    private final AtomicBoolean isReceivingAudioStream = new AtomicBoolean(false);
    private final AtomicBoolean isAppOpsFixed = new AtomicBoolean(false);
    private final AtomicBoolean voiceCallAccessible = new AtomicBoolean(false);

    // File and audio management
    private String currentRecordingFile;
    private String currentPlayingFile;
    private ByteArrayOutputStream responseAudioBuffer;
    private long recordingStartTime;

    // Recording quality tracking
    private RecordingMode currentRecordingMode = RecordingMode.UNKNOWN;

    public enum RecordingMode {
        VOICE_CALL_TWO_WAY("Two-way call recording"),
        VOICE_COMMUNICATION("Optimized call recording"),
        MICROPHONE_ONLY("Microphone only"),
        UNKNOWN("Unknown mode"),
        FAILED("Recording failed");

        private final String description;
        RecordingMode(String description) { this.description = description; }
        public String getDescription() { return description; }
    }

    // Callback interface for CallDetector integration
    public interface CallProcessorCallback {
        void onRecordingStarted(RecordingMode mode);
        void onRecordingFailed(String reason);
        void onRecordingStopped(String filename, long duration);
        void onRecordingModeChanged(RecordingMode mode);
    }

    private CallProcessorCallback callback;

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public RealTimeCallProcessor(Context context) {
        this.context = context;
        this.executorService = Executors.newFixedThreadPool(4);
        this.elevenLabsClient = new ElevenLabsWebSocketClient(context);
        this.responseAudioBuffer = new ByteArrayOutputStream();
        this.mainHandler = new Handler(Looper.getMainLooper());

        // Initialize based on Android version
        if (IS_ANDROID_12_PLUS) {
            initializeAndroid12Plus();
        } else {
            initializeLegacyAndroid();
        }
    }


    /**
     * Checks if the app is ready to start recording (all permissions granted)
     */
    public boolean isSystemReadyForRecording() {
        try {
            // Check 1: Basic permissions
            if (!hasRecordAudioPermission()) {
                Log.e(TAG, "❌ RECORD_AUDIO permission missing");
                return false;
            }

            // Check 2: Root access (helpful but not required)
            if (!isDeviceRooted()) {
                Log.w(TAG, "⚠️ No root access - limited functionality");
            }

            // Check 3: Audio system
            try {
                int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
                if (bufferSize <= 0) {
                    Log.e(TAG, "❌ Audio system not ready");
                    return false;
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Audio system check failed");
                return false;
            }

            // Check 4: Try to create a test AudioRecord
            try {
                AudioRecord testRecord = new AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                );

                if (testRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    testRecord.release();
                    Log.d(TAG, "✅ System appears ready for recording");
                    return true;
                } else {
                    testRecord.release();
                    Log.e(TAG, "❌ AudioRecord test failed - AppOps issue likely");
                    return false;
                }

            } catch (SecurityException e) {
                Log.e(TAG, "❌ AudioRecord security exception - permission issue");
                return false;
            } catch (Exception e) {
                Log.e(TAG, "❌ AudioRecord test exception: " + e.getMessage());
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "System readiness check failed: " + e.getMessage());
            return false;
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public void applyAllEnhancedFixesWithBackground() {
        Log.d(TAG, "🔧 Applying all enhanced fixes including background recording...");
        showToast("🔧 Applying comprehensive audio fixes...");

        executorService.execute(() -> {
            try {
                if (IS_ANDROID_12_PLUS) {
                    applyAndroid12Fixes();
                    Thread.sleep(1000);
                }

                boolean backgroundSuccess = fixBackgroundRecordingPermissions();
                Thread.sleep(2000);

                boolean voiceCallWorks = testVoiceCallAccess();
                boolean backgroundWorks = testBackgroundRecording();

                String resultMessage;
                if (voiceCallWorks && backgroundWorks) {
                    resultMessage = "🎉 All fixes successful - Full recording capability!";
                } else if (backgroundWorks) {
                    resultMessage = "✅ Background recording fixed - VOICE_COMMUNICATION available";
                } else {
                    resultMessage = "⚠️ Basic fixes applied - May be foreground-only";
                }

                Log.d(TAG, resultMessage);
                showToast(resultMessage);

            } catch (Exception e) {
                Log.e(TAG, "Comprehensive fixes failed: " + e.getMessage());
                showToast("❌ Comprehensive fixes failed");
            }
        });
    }

    private boolean fixBackgroundRecordingPermissions() {
        if (!isDeviceRooted()) {
            Log.e(TAG, "❌ Root access required for background recording fixes");
            return false;
        }

        try {
            Log.d(TAG, "🔧 Fixing background recording permissions...");

            String packageName = context.getPackageName();
            int uid = context.getApplicationInfo().uid;

            // Fix 1: Set RECORD_AUDIO to allow in all modes (not just foreground)
            String[] backgroundRecordingCommands = {
                    "appops set --uid " + uid + " RECORD_AUDIO allow",
                    "appops set " + packageName + " RECORD_AUDIO allow",
                    "cmd appops set " + packageName + " RECORD_AUDIO allow",
                    "cmd appops set --uid " + uid + " RECORD_AUDIO allow",
                    "appops set " + packageName + " RECORD_AUDIO_OUTPUT allow",
                    "appops set --uid " + uid + " RECORD_AUDIO_OUTPUT allow",
                    "appops set " + packageName + " PHONE_CALL_MICROPHONE allow",
                    "appops set --uid " + uid + " PHONE_CALL_MICROPHONE allow",
                    "appops set " + packageName + " RECORD_AUDIO_HOTWORD allow",
                    "appops set --uid " + uid + " RECORD_AUDIO_HOTWORD allow"
            };

            executeCommands(backgroundRecordingCommands, "Background Recording");

            // Fix 2: Disable battery optimization restrictions
            String[] batteryOptimizationCommands = {
                    "dumpsys deviceidle whitelist +" + packageName,
                    "cmd appops set " + packageName + " START_FOREGROUND allow",
                    "cmd appops set " + packageName + " RUN_IN_BACKGROUND allow"
            };

            executeCommands(batteryOptimizationCommands, "Battery Optimization");

            Thread.sleep(2000);
            return verifyBackgroundRecordingPermissions();

        } catch (Exception e) {
            Log.e(TAG, "Background recording fix failed", e);
            return false;
        }
    }

    private boolean verifyBackgroundRecordingPermissions() {
        try {
            String packageName = context.getPackageName();

            Process p = Runtime.getRuntime().exec("su -c \"appops get " + packageName + " RECORD_AUDIO\"");
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            boolean foundAllow = false;
            boolean stillForegroundOnly = false;

            while ((line = reader.readLine()) != null) {
                Log.d(TAG, "AppOps result: " + line);

                if (line.contains("RECORD_AUDIO: allow")) {
                    foundAllow = true;
                }

                if (line.contains("foreground") && !line.contains("background")) {
                    stillForegroundOnly = true;
                }
            }

            if (foundAllow && !stillForegroundOnly) {
                Log.d(TAG, "✅ Background recording permissions verified");
                return true;
            } else {
                Log.w(TAG, "⚠️ RECORD_AUDIO allowed but may still have restrictions");
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "Background recording verification failed", e);
            return false;
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private boolean testBackgroundRecording() {
        try {
            Log.d(TAG, "🧪 Testing background recording capability...");

            int bufferSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

            AudioRecord testRecord = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
            );

            if (testRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "❌ AudioRecord failed to initialize in background test");
                testRecord.release();
                return false;
            }

            testRecord.startRecording();

            if (testRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "❌ AudioRecord failed to start recording in background test");
                testRecord.release();
                return false;
            }

            byte[] buffer = new byte[1024];
            int bytesRead = testRecord.read(buffer, 0, buffer.length);

            testRecord.stop();
            testRecord.release();

            if (bytesRead > 0) {
                Log.d(TAG, "✅ Background recording test successful - read " + bytesRead + " bytes");
                return true;
            } else {
                Log.w(TAG, "⚠️ Background recording started but no data read");
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "Background recording test failed: " + e.getMessage());
            return false;
        }
    }



    public void setCallback(CallProcessorCallback callback) {
        this.callback = callback;
    }

    // ============================================================================
    // INITIALIZATION METHODS
    // ============================================================================

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private void initializeAndroid12Plus() {
        Log.d(TAG, "🔧 Initializing for Android 12+ (API " + Build.VERSION.SDK_INT + ")");

        executorService.execute(() -> {
            boolean moduleDetected = detectFixCallRecordingModule();
            if (moduleDetected) {
                Log.d(TAG, "✅ FixCallRecording module detected");
                applyAndroid12Fixes();
            } else {
                Log.w(TAG, "⚠️ FixCallRecording module not detected");
                showToast("⚠️ Install FixCallRecording for two-way recording");
            }
        });
    }

    private void initializeLegacyAndroid() {
        Log.d(TAG, "🔧 Initializing for Android < 12 (API " + Build.VERSION.SDK_INT + ")");

        executorService.execute(() -> {
            if (isDeviceRooted()) {
                applyLegacyRootFixes();
            }
        });
    }

    private boolean detectFixCallRecordingModule() {
        try {
            String[] commands = {
                    "find /data/adb/modules -name '*call*record*' -type d",
                    "find /data/adb/modules -name '*FixCallRecord*' -type d"
            };

            for (String command : commands) {
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line = reader.readLine();

                if (line != null && !line.trim().isEmpty()) {
                    Log.d(TAG, "✅ Found call recording module: " + line);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Module detection failed", e);
            return false;
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private void applyAndroid12Fixes() {
        try {
            String packageName = context.getPackageName();

            String[] criticalCommands = {
                    "appops set " + packageName + " RECORD_AUDIO allow",
                    "appops set " + packageName + " PHONE_CALL_MICROPHONE allow",
                    "appops set " + packageName + " RECORD_AUDIO_OUTPUT allow",
                    "setprop persist.vendor.radio.enable_voicecall_recording true",
                    "setprop vendor.audio.feature.call_recording.enable true",
                    "setprop vendor.audio.record.multiple.enabled true"
            };

            executeCommands(criticalCommands, "Android 12+ Fixes");

            Thread.sleep(1000);
            testVoiceCallAccess();
            isAppOpsFixed.set(true);

        } catch (Exception e) {
            Log.e(TAG, "Android 12+ fixes failed", e);
        }
    }

    private void applyLegacyRootFixes() {
        try {
            String packageName = context.getPackageName();

            String[] legacyCommands = {
                    "appops set " + packageName + " RECORD_AUDIO allow",
                    "settings put global op_voice_recording_supported_by_mcc 1",
                    "settings put system call_recording 1"
            };

            executeCommands(legacyCommands, "Legacy Root Fixes");
            isAppOpsFixed.set(true);

        } catch (Exception e) {
            Log.e(TAG, "Legacy fixes failed", e);
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private boolean testVoiceCallAccess() {
        try {
            int bufferSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

            AudioRecord testRecord = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_CALL,
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
            );

            if (testRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                Log.d(TAG, "✅ VOICE_CALL access successful");
                voiceCallAccessible.set(true);
            } else {
                Log.e(TAG, "❌ VOICE_CALL access failed");
                voiceCallAccessible.set(false);
            }

            testRecord.release();

            return true;
        } catch (Exception e) {
            Log.e(TAG, "VOICE_CALL test failed: " + e.getMessage());
            voiceCallAccessible.set(false);
            return false;

        }
    }

    // ============================================================================
    // MAIN RECORDING METHODS - CALLED BY CALLDETECTOR
    // ============================================================================

    public void checkCurrentAppOpsStatus() {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "📋 Current AppOps Status:");

                String packageName = context.getPackageName();

                Process p1 = Runtime.getRuntime().exec("su -c \"appops get " + packageName + " RECORD_AUDIO\"");
                BufferedReader reader1 = new BufferedReader(new InputStreamReader(p1.getInputStream()));
                String line;
                while ((line = reader1.readLine()) != null) {
                    Log.d(TAG, "RECORD_AUDIO: " + line);
                }

            } catch (Exception e) {
                Log.e(TAG, "AppOps status check failed", e);
            }
        });
    }

    /**
     * Called by CallDetector when call becomes active (OFFHOOK)
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public boolean startCallRecording() {
        try {
            if (!hasRecordAudioPermission()) {
                Log.e(TAG, "❌ RECORD_AUDIO permission not granted");
                notifyCallback(cb -> cb.onRecordingFailed("RECORD_AUDIO permission not granted"));
                return false;
            }

            logDeviceInfo();
            recordingStartTime = System.currentTimeMillis();

            if (ENABLE_FILE_RECORDING) {
                boolean recordingStarted = IS_ANDROID_12_PLUS ?
                        startAndroid12Recording() : startLegacyRecording();

                if (!recordingStarted) {
                    Log.e(TAG, "❌ Failed to start call recording");
                    notifyCallback(cb -> cb.onRecordingFailed("Failed to initialize recording"));
                    return false;
                }
            }

            // Setup real-time processing if not in file-only mode
            if (!FILE_RECORDING_ONLY) {
                setupRealTimeProcessing();
            }

            isRecording.set(true);

            // Analyze and report recording mode
            analyzeRecordingMode();

            Log.d(TAG, "✅ Call recording started successfully");
            notifyCallback(cb -> cb.onRecordingStarted(currentRecordingMode));

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to start call recording: " + e.getMessage());
            notifyCallback(cb -> cb.onRecordingFailed("Exception: " + e.getMessage()));
            handleRecordingFailure();
            return false;
        }
    }

    /**
     * Called by CallDetector when call ends (IDLE)
     */
    public void stopCallRecording() {
        Log.d(TAG, "🛑 Stopping call recording...");

        long recordingDuration = System.currentTimeMillis() - recordingStartTime;
        isRecording.set(false);

        // Stop file recording
        String finalFilename = stopFileRecording();

        // Stop real-time processing
        if (isPlayingResponse.get() || isReceivingAudioStream.get()) {
            interruptAudioResponse();
        }

        if (elevenLabsClient != null) {
            elevenLabsClient.disconnect();
        }

        cleanupAudioResources();

        Log.d(TAG, "✅ Call recording stopped. Duration: " + (recordingDuration / 1000) + "s");
        notifyCallback(cb -> cb.onRecordingStopped(finalFilename, recordingDuration));
    }

    private boolean startAndroid12Recording() {
        Log.d(TAG, "🚀 Starting Android 12+ recording...");

        // Try VOICE_CALL first if accessible
        if (voiceCallAccessible.get() || attemptVoiceCallRecording()) {
            currentRecordingMode = RecordingMode.VOICE_CALL_TWO_WAY;
            return true;
        }

        // Fallback to VOICE_COMMUNICATION
        if (attemptVoiceCommunicationRecording()) {
            currentRecordingMode = RecordingMode.VOICE_COMMUNICATION;
            return true;
        }

        // Last resort - microphone only
        if (attemptMicrophoneRecording()) {
            currentRecordingMode = RecordingMode.MICROPHONE_ONLY;
            return true;
        }

        currentRecordingMode = RecordingMode.FAILED;
        return false;
    }

    private boolean startLegacyRecording() {
        Log.d(TAG, "🚀 Starting legacy Android recording...");

        int[] sources = AUDIO_SOURCES_LEGACY;

        for (int source : sources) {
            if (attemptRecordingWithSource(source)) {
                determineRecordingModeFromSource(source);
                Log.d(TAG, "✅ Recording started with source: " + getAudioSourceName(source));
                return true;
            }
        }

        currentRecordingMode = RecordingMode.FAILED;
        return false;
    }

    private boolean attemptVoiceCallRecording() {
        return attemptRecordingWithSource(MediaRecorder.AudioSource.VOICE_CALL);
    }

    private boolean attemptVoiceCommunicationRecording() {
        return attemptRecordingWithSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION);
    }

    private boolean attemptMicrophoneRecording() {
        return attemptRecordingWithSource(MediaRecorder.AudioSource.MIC);
    }

    private boolean attemptRecordingWithSource(int audioSource) {
        try {
            createRecordingFile();

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(audioSource);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

            // Configure based on source and Android version
            if (IS_ANDROID_12_PLUS && audioSource == MediaRecorder.AudioSource.VOICE_CALL) {
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mediaRecorder.setAudioSamplingRate(48000);
                mediaRecorder.setAudioEncodingBitRate(128000);
                mediaRecorder.setAudioChannels(2); // Stereo for both call sides
            } else {
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mediaRecorder.setAudioSamplingRate(44100);
                mediaRecorder.setAudioEncodingBitRate(96000);
                mediaRecorder.setAudioChannels(1); // Mono for other sources
            }

            mediaRecorder.setOutputFile(currentRecordingFile);

            // Android version specific timing
            Thread.sleep(IS_ANDROID_12_PLUS ? 300 : 200);
            mediaRecorder.prepare();
            Thread.sleep(IS_ANDROID_12_PLUS ? 200 : 100);
            mediaRecorder.start();

            isFileRecording.set(true);

            Log.d(TAG, "✅ Recording started with " + getAudioSourceName(audioSource));
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Recording failed with " + getAudioSourceName(audioSource) + ": " + e.getMessage());

            if (mediaRecorder != null) {
                try {
                    mediaRecorder.reset();
                    mediaRecorder.release();
                } catch (Exception ignored) {}
                mediaRecorder = null;
            }

            return false;
        }
    }

    private void createRecordingFile() {

        File recordingDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TeleTalker");

//        File recordingDir = new File(Environment.getExternalStoragePublicDirectory(
//                Environment.DIRECTORY_MUSIC), "CallRecordings");

        if (!recordingDir.exists()) {
            recordingDir.mkdirs();
        }

        String filename = "call_" + System.currentTimeMillis();
        if (IS_ANDROID_12_PLUS) {
            filename += "_a12";
        }
        filename += ".m4a";

        currentRecordingFile = recordingDir.getAbsolutePath() + "/" + filename;
    }

    private String stopFileRecording() {
        if (mediaRecorder == null || !isFileRecording.get()) {
            return null;
        }

        try {
            mediaRecorder.stop();
            Thread.sleep(IS_ANDROID_12_PLUS ? 500 : 300);

            mediaRecorder.release();
            mediaRecorder = null;
            isFileRecording.set(false);

            if (currentRecordingFile != null) {
                return validateAndFinalizeRecording();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording", e);
            handleRecordingFailure();
        }

        return null;
    }

    private String validateAndFinalizeRecording() {
        File recordingFile = new File(currentRecordingFile);

        if (recordingFile.exists()) {
            long fileSize = recordingFile.length();
            Log.d(TAG, "Recording finalized. Size: " + fileSize + " bytes, Mode: " + currentRecordingMode);

            if (fileSize > 1024 && isFilePlayable(recordingFile)) {
                String fileName = recordingFile.getName();
                String message = "✅ Call recorded: " + fileName + " (" + currentRecordingMode.getDescription() + ")";
                showToast(message);
                return fileName;
            } else {
                Log.w(TAG, "Recording file corrupted or too small");
                if (!repairRecordingFile(recordingFile)) {
                    showToast("❌ Recording corrupted");
                    recordingFile.delete();
                    return null;
                }
                return recordingFile.getName();
            }
        } else {
            Log.e(TAG, "Recording file not created");
            showToast("❌ Recording failed - no file created");
            return null;
        }
    }

    // ============================================================================
    // REAL-TIME AUDIO PROCESSING
    // ============================================================================

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private void setupRealTimeProcessing() {
        if (!OFFLINE_MODE) {
            setupAudioRecord();
            setupAudioTrack();

            elevenLabsClient.connect(new ElevenLabsWebSocketClient.ConversationCallback() {
                @Override
                public void onConnected() {
                    Log.d(TAG, "✅ ElevenLabs connected");
                    showToast("✅ AI service connected");
                }

                @Override
                public void onDisconnected() {
                    Log.w(TAG, "⚠️ ElevenLabs disconnected");
                }

                @Override
                public void onError(Exception error) {
                    Log.e(TAG, "❌ ElevenLabs error: " + error.getMessage());
                }

                @Override
                public void onConversationStarted(String conversationId, String audioFormat) {

                }

                @Override
                public void onAudioReceived(byte[] audioData, int eventId) {
                    processAndInjectAudio(audioData);

                }

                @Override
                public void onAgentResponse(String response) {

                }

                @Override
                public void onUserTranscript(String transcript) {

                }

                @Override
                public void onAgentResponseCorrection(String correctedResponse) {

                }

                @Override
                public void onInterruption() {

                }


            });

            executorService.execute(this::processAudioStream);
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private void setupAudioRecord() {
        if (!hasRecordAudioPermission()) {
            throw new SecurityException("RECORD_AUDIO permission not granted");
        }

        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        int[] audioSources = IS_ANDROID_12_PLUS ? AUDIO_SOURCES_ANDROID_12_PLUS : AUDIO_SOURCES_LEGACY;

        for (int audioSource : audioSources) {
            try {
                audioRecord = new AudioRecord(
                        audioSource, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize * 4);

                if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    Log.d(TAG, "✅ AudioRecord initialized with source: " + getAudioSourceName(audioSource));
                    return;
                } else {
                    audioRecord.release();
                    audioRecord = null;
                }
            } catch (Exception e) {
                Log.e(TAG, "AudioRecord failed with " + getAudioSourceName(audioSource));
                if (audioRecord != null) {
                    audioRecord.release();
                    audioRecord = null;
                }
            }
        }

        throw new RuntimeException("Failed to initialize AudioRecord");
    }

    private void setupAudioTrack() {
        int bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AUDIO_FORMAT);

        audioTrack = new AudioTrack(
                AudioManager.STREAM_VOICE_CALL, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                AUDIO_FORMAT, bufferSize * 4, AudioTrack.MODE_STREAM);

        Log.d(TAG, "AudioTrack configured for call injection");
    }

    private void processAudioStream() {
        if (!FILE_RECORDING_ONLY && audioRecord != null) {
            audioRecord.startRecording();
        }

        byte[] buffer = new byte[AUDIO_CHUNK_SIZE];
        int totalChunksSent = 0;

        while (isRecording.get()) {
            if (FILE_RECORDING_ONLY) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }

            if (audioRecord == null) {
                break;
            }

            int bytesRead = audioRecord.read(buffer, 0, buffer.length);

            if (bytesRead > 0) {
                totalChunksSent++;

                if (ENABLE_DEBUGGING && totalChunksSent % 20 == 0) {
                    boolean hasAudio = checkAudioPresence(buffer, bytesRead);
                    Log.d(TAG, "Chunk #" + totalChunksSent + " - Has audio: " + hasAudio);
                }

                sendAudioChunk(buffer, bytesRead);
            }

            try {
                Thread.sleep(AUDIO_CHUNK_DURATION_MS);
            } catch (InterruptedException e) {
                break;
            }
        }

        Log.d(TAG, "Audio processing stopped. Total chunks: " + totalChunksSent);
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private void analyzeRecordingMode() {
        executorService.execute(() -> {
            try {
                Thread.sleep(2000);

                String message = "🎙️ " + currentRecordingMode.getDescription();

                if (currentRecordingMode == RecordingMode.MICROPHONE_ONLY) {
                    message += " - Only your voice will be recorded";
                } else if (currentRecordingMode == RecordingMode.VOICE_CALL_TWO_WAY) {
                    message += " - Both sides of call will be recorded";
                }

                Log.d(TAG, message);
                showToast(message);

                notifyCallback(cb -> cb.onRecordingModeChanged(currentRecordingMode));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void executeCommands(String[] commands, String phase) {
        for (String cmd : commands) {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
                p.waitFor();
                if (p.exitValue() == 0) {
                    Log.d(TAG, "✅ " + phase + " command succeeded: " + cmd);
                } else {
                    Log.w(TAG, "⚠️ " + phase + " command failed: " + cmd);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error executing " + phase + " command: " + cmd);
            }
        }
    }

    private void determineRecordingModeFromSource(int audioSource) {
        switch (audioSource) {
            case MediaRecorder.AudioSource.VOICE_CALL:
                currentRecordingMode = RecordingMode.VOICE_CALL_TWO_WAY;
                break;
            case MediaRecorder.AudioSource.VOICE_COMMUNICATION:
                currentRecordingMode = RecordingMode.VOICE_COMMUNICATION;
                break;
            case MediaRecorder.AudioSource.MIC:
                currentRecordingMode = RecordingMode.MICROPHONE_ONLY;
                break;
            default:
                currentRecordingMode = RecordingMode.UNKNOWN;
                break;
        }
    }

    private void notifyCallback(CallbackAction action) {
        if (callback != null) {
            mainHandler.post(() -> action.execute(callback));
        }
    }

    @FunctionalInterface
    private interface CallbackAction {
        void execute(CallProcessorCallback callback);
    }

    private void cleanupAudioResources() {
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
                audioRecord = null;
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning AudioRecord");
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
                Log.e(TAG, "Error cleaning AudioTrack");
            }
        }
    }

    private void handleRecordingFailure() {
        currentRecordingMode = RecordingMode.FAILED;
        isRecording.set(false);

        if (currentRecordingFile != null) {
            File file = new File(currentRecordingFile);
            if (file.exists() && file.length() > 1024) {
                showToast("⚠️ Partial recording saved");
            } else {
                file.delete();
            }
        }

        cleanupAudioResources();
    }

    // ============================================================================
    // UTILITY METHODS
    // ============================================================================

    private String getAudioSourceName(int audioSource) {
        switch (audioSource) {
            case MediaRecorder.AudioSource.VOICE_COMMUNICATION: return "VOICE_COMMUNICATION";
            case MediaRecorder.AudioSource.MIC: return "MIC";
            case MediaRecorder.AudioSource.VOICE_CALL: return "VOICE_CALL";
            case MediaRecorder.AudioSource.DEFAULT: return "DEFAULT";
            case MediaRecorder.AudioSource.VOICE_UPLINK: return "VOICE_UPLINK";
            case MediaRecorder.AudioSource.VOICE_DOWNLINK: return "VOICE_DOWNLINK";
            default: return "UNKNOWN(" + audioSource + ")";
        }
    }

    private boolean hasRecordAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private boolean isDeviceRooted() {
        try {
            Process process = Runtime.getRuntime().exec("su -c echo test");
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void showToast(String message) {
        mainHandler.post(() -> {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        });
    }

    private boolean checkAudioPresence(byte[] buffer, int length) {
        long sum = 0;
        for (int i = 0; i < length - 1; i += 2) {
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            sum += Math.abs(sample);
        }
        double average = (double) sum / (length / 2);
        return average > 100;
    }

    private void sendAudioChunk(byte[] buffer, int bytesRead) {
        if (OFFLINE_MODE || !elevenLabsClient.isConnected()) {
            return;
        }

        byte[] audioChunk = new byte[bytesRead];
        System.arraycopy(buffer, 0, audioChunk, 0, bytesRead);
        byte[] wavData = convertToWav(audioChunk);
        elevenLabsClient.sendAudio(wavData);
    }

    private byte[] convertToWav(byte[] audioData) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            writeWavHeader(out, audioData.length);
            out.write(audioData);
        } catch (IOException e) {
            Log.e(TAG, "Failed to convert to WAV");
        }
        return out.toByteArray();
    }

    private void writeWavHeader(ByteArrayOutputStream out, int dataLength) throws IOException {
        int totalLength = dataLength + 36;

        out.write("RIFF".getBytes());
        out.write(intToByteArray(totalLength), 0, 4);
        out.write("WAVE".getBytes());

        out.write("fmt ".getBytes());
        out.write(intToByteArray(16), 0, 4);
        out.write(shortToByteArray((short) 1), 0, 2);
        out.write(shortToByteArray((short) 1), 0, 2);
        out.write(intToByteArray(SAMPLE_RATE), 0, 4);
        out.write(intToByteArray(SAMPLE_RATE * 2), 0, 4);
        out.write(shortToByteArray((short) 2), 0, 2);
        out.write(shortToByteArray((short) 16), 0, 2);

        out.write("data".getBytes());
        out.write(intToByteArray(dataLength), 0, 4);
    }

    private byte[] intToByteArray(int value) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
    }

    private byte[] shortToByteArray(short value) {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array();
    }

    private boolean isFilePlayable(File file) {
        MediaPlayer player = new MediaPlayer();
        try {
            player.setDataSource(file.getAbsolutePath());
            player.prepare();
            player.release();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean repairRecordingFile(File file) {
        try {
            String tempPath = file.getAbsolutePath() + ".fixed";
            Process p = Runtime.getRuntime().exec(new String[]{
                    "su", "-c", "ffmpeg -y -i " + file.getAbsolutePath() + " -c copy " + tempPath
            });
            p.waitFor();

            if (new File(tempPath).exists()) {
                file.delete();
                new File(tempPath).renameTo(file);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "File repair failed");
        }
        return false;
    }

    private void processAndInjectAudio(byte[] audioData) {
        // Implementation for AI audio injection
    }

    private void interruptAudioResponse() {
        // Implementation for interrupting AI responses
    }

    public void logDeviceInfo() {
        Log.d(TAG, "=== DEVICE INFO ===");
        Log.d(TAG, "Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        Log.d(TAG, "Device: " + Build.MANUFACTURER + " " + Build.MODEL);
        Log.d(TAG, "Root: " + (isDeviceRooted() ? "YES" : "NO"));
        Log.d(TAG, "VOICE_CALL accessible: " + (voiceCallAccessible.get() ? "YES" : "NO"));
        Log.d(TAG, "Recording mode: " + currentRecordingMode);
    }

    // ============================================================================
    // PUBLIC GETTERS - FOR CALLDETECTOR INTEGRATION
    // ============================================================================

    public boolean isRecording() { return isRecording.get(); }
    public boolean isFileRecording() { return isFileRecording.get(); }
    public String getCurrentRecordingFile() { return currentRecordingFile; }
    public String getCurrentPlayingFile() { return currentPlayingFile; }
    public RecordingMode getCurrentRecordingMode() { return currentRecordingMode; }

    public String getRecordingStatus() {
        if (!isRecording.get()) {
            return "Not recording";
        }

        long duration = (System.currentTimeMillis() - recordingStartTime) / 1000;
        return String.format("Recording: %ds, Mode: %s", duration, currentRecordingMode.getDescription());
    }
}