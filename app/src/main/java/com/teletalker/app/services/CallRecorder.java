package com.teletalker.app.services;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CallRecorder - Simplified BCR-style call recording implementation
 * Focuses purely on reliable call recording without AI features
 */
public class CallRecorder {
    private static final String TAG = "CallRecorder";

    // Audio configuration
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    // Audio sources prioritized by Android version
    private static final int[] AUDIO_SOURCES_ANDROID_12_PLUS = {
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
    };

    private static final int[] AUDIO_SOURCES_LEGACY = {
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_UPLINK,
            MediaRecorder.AudioSource.VOICE_DOWNLINK,
            MediaRecorder.AudioSource.MIC
    };

    // Recording modes
    public enum RecordingMode {
        VOICE_CALL_TWO_WAY("Two-way call recording (both sides)"),
        VOICE_COMMUNICATION("Call recording (optimized)"),
        VOICE_UPLINK("Outgoing voice only"),
        VOICE_DOWNLINK("Incoming voice only"),
        MICROPHONE_ONLY("Microphone only (your voice)"),
        UNKNOWN("Unknown mode"),
        FAILED("Recording failed");

        private final String description;
        RecordingMode(String description) {
            this.description = description;
        }
        public String getDescription() {
            return description;
        }
    }

    // Callback interface
    public interface RecordingCallback {
        void onRecordingStarted(RecordingMode mode);
        void onRecordingFailed(String reason);
        void onRecordingStopped(String filename, long duration);
    }

    // Instance variables
    private Context context;
    private MediaRecorder mediaRecorder;
    private ExecutorService executorService;
    private Handler mainHandler;
    private RecordingCallback callback;

    // State tracking
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private String currentRecordingFile;
    private long recordingStartTime;
    private RecordingMode currentRecordingMode = RecordingMode.UNKNOWN;
    private int currentAudioSource;

    // System capabilities
    private final AtomicBoolean isRooted = new AtomicBoolean(false);
    private final AtomicBoolean voiceCallAccessible = new AtomicBoolean(false);

    public CallRecorder(Context context) {
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());

        // Initialize system capabilities
        executorService.execute(this::initializeCapabilities);
    }

    public void setCallback(RecordingCallback callback) {
        this.callback = callback;
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

        try {
            recordingStartTime = System.currentTimeMillis();
            currentRecordingFile = createRecordingPath(filename);

            Log.d(TAG, "🚀 Starting call recording: " + filename);
            logDeviceInfo();

            // Apply system fixes if needed
            if (isRooted.get()) {
                applyRecordingFixes();
            }

            // Try recording with different audio sources
            boolean started = attemptRecordingWithBestSource();

            if (started) {
                isRecording.set(true);
                analyzeRecordingQuality();
                notifyCallback(cb -> cb.onRecordingStarted(currentRecordingMode));
                Log.d(TAG, "✅ Recording started with " + currentRecordingMode.getDescription());
                return true;
            } else {
                Log.e(TAG, "❌ Failed to start recording with any audio source");
                notifyCallback(cb -> cb.onRecordingFailed("No compatible audio source found"));
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "Exception starting recording: " + e.getMessage());
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
            Log.d(TAG, "🛑 Stopping call recording...");

            long recordingDuration = System.currentTimeMillis() - recordingStartTime;
            isRecording.set(false);

            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            }

            // Validate and finalize recording
            String finalFilename = validateRecording();

            notifyCallback(cb -> cb.onRecordingStopped(finalFilename, recordingDuration));
            Log.d(TAG, "✅ Recording stopped. Duration: " + (recordingDuration / 1000) + "s");

        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording: " + e.getMessage());
            cleanup();
        }
    }

    // ============================================================================
    // RECORDING IMPLEMENTATION
    // ============================================================================

    private boolean attemptRecordingWithBestSource() {
        int[] audioSources = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
                AUDIO_SOURCES_ANDROID_12_PLUS : AUDIO_SOURCES_LEGACY;

        for (int audioSource : audioSources) {
            if (attemptRecordingWithSource(audioSource)) {
                currentAudioSource = audioSource;
                currentRecordingMode = getRecordingModeFromSource(audioSource);
                return true;
            }
        }

        currentRecordingMode = RecordingMode.FAILED;
        return false;
    }

    private boolean attemptRecordingWithSource(int audioSource) {
        try {
            Log.d(TAG, "🧪 Trying audio source: " + getAudioSourceName(audioSource));

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(audioSource);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

            // Configure based on audio source
            configureRecordingSettings(audioSource);

            mediaRecorder.setOutputFile(currentRecordingFile);

            // Prepare and start
            mediaRecorder.prepare();
            Thread.sleep(200); // Brief delay for stability
            mediaRecorder.start();

            Log.d(TAG, "✅ Recording started with " + getAudioSourceName(audioSource));
            return true;

        } catch (Exception e) {
            Log.w(TAG, "❌ Failed with " + getAudioSourceName(audioSource) + ": " + e.getMessage());

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

    private void configureRecordingSettings(int audioSource) {
        // Configure recording settings based on audio source and Android version
        boolean isAndroid12Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;

        if (audioSource == MediaRecorder.AudioSource.VOICE_CALL && isAndroid12Plus) {
            // High quality stereo for two-way recording
            mediaRecorder.setAudioSamplingRate(48000);
            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setAudioChannels(2); // Stereo for both call sides
        } else if (audioSource == MediaRecorder.AudioSource.VOICE_COMMUNICATION) {
            // Optimized for call recording
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setAudioEncodingBitRate(96000);
            mediaRecorder.setAudioChannels(1); // Mono
        } else {
            // Default settings for other sources
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setAudioEncodingBitRate(64000);
            mediaRecorder.setAudioChannels(1); // Mono
        }
    }

    private RecordingMode getRecordingModeFromSource(int audioSource) {
        switch (audioSource) {
            case MediaRecorder.AudioSource.VOICE_CALL:
                return RecordingMode.VOICE_CALL_TWO_WAY;
            case MediaRecorder.AudioSource.VOICE_COMMUNICATION:
                return RecordingMode.VOICE_COMMUNICATION;
            case MediaRecorder.AudioSource.VOICE_UPLINK:
                return RecordingMode.VOICE_UPLINK;
            case MediaRecorder.AudioSource.VOICE_DOWNLINK:
                return RecordingMode.VOICE_DOWNLINK;
            case MediaRecorder.AudioSource.MIC:
                return RecordingMode.MICROPHONE_ONLY;
            default:
                return RecordingMode.UNKNOWN;
        }
    }

    // ============================================================================
    // SYSTEM FIXES AND INITIALIZATION
    // ============================================================================

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private void initializeCapabilities() {
        // Check root access
        isRooted.set(checkRootAccess());

        // Test VOICE_CALL accessibility
        voiceCallAccessible.set(testVoiceCallAccess());

        Log.d(TAG, "System capabilities - Root: " + isRooted.get() +
                ", VOICE_CALL: " + voiceCallAccessible.get());
    }

    private boolean checkRootAccess() {
        try {
            Process process = Runtime.getRuntime().exec("su -c echo test");
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private boolean testVoiceCallAccess() {
        if (!hasRecordAudioPermission()) {
            return false;
        }

        try {
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

            AudioRecord testRecord = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_CALL,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
            );

            boolean accessible = testRecord.getState() == AudioRecord.STATE_INITIALIZED;
            testRecord.release();

            return accessible;
        } catch (Exception e) {
            return false;
        }
    }

    private void applyRecordingFixes() {
        if (!isRooted.get()) {
            return;
        }

        executorService.execute(() -> {
            try {
                Log.d(TAG, "🔧 Applying recording fixes...");

                String packageName = context.getPackageName();

                // Essential AppOps fixes
                String[] commands = {
                        "appops set " + packageName + " RECORD_AUDIO allow",
                        "appops set " + packageName + " PHONE_CALL_MICROPHONE allow"
                };

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+ specific fixes
                    String[] android12Commands = {
                            "appops set " + packageName + " RECORD_AUDIO_OUTPUT allow",
                            "setprop persist.vendor.radio.enable_voicecall_recording true"
                    };
                    commands = concatenateArrays(commands, android12Commands);
                }

                executeRootCommands(commands);

                // Brief delay for changes to take effect
                Thread.sleep(1000);

                Log.d(TAG, "✅ Recording fixes applied");

            } catch (Exception e) {
                Log.e(TAG, "Failed to apply recording fixes: " + e.getMessage());
            }
        });
    }

    private void executeRootCommands(String[] commands) {
        for (String command : commands) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
                process.waitFor();

                if (process.exitValue() == 0) {
                    Log.d(TAG, "✅ Command succeeded: " + command);
                } else {
                    Log.w(TAG, "⚠️ Command failed: " + command);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error executing command: " + command);
            }
        }
    }

    // ============================================================================
    // FILE MANAGEMENT
    // ============================================================================

    private String createRecordingPath(String filename) {
        // Create TeleTalker directory in Music/Downloads
        File recordingDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MUSIC), "TeleTalker");

        if (!recordingDir.exists()) {
            boolean created = recordingDir.mkdirs();
            if (!created) {
                Log.w(TAG, "Failed to create recording directory, using app directory");
                recordingDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Recordings");
                recordingDir.mkdirs();
            }
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
            showToast("Recording failed - no file created");
            return null;
        }

        long fileSize = recordingFile.length();
        Log.d(TAG, "📁 Recording file size: " + fileSize + " bytes");

        if (fileSize < 1024) { // Less than 1KB
            Log.w(TAG, "⚠️ Recording file too small, likely empty");
            showToast("Recording may be empty or corrupted");
            // Don't delete, let user decide
        }

        // Test if file is playable
        if (!isFilePlayable(recordingFile)) {
            Log.w(TAG, "⚠️ Recording file may be corrupted");
            if (isRooted.get()) {
                // Attempt repair with ffmpeg if available
                repairRecordingFile(recordingFile);
            }
        }

        // Return filename only (not full path)
        String fileName = recordingFile.getName();
        String message = "✅ Call recorded: " + fileName + " (" + currentRecordingMode.getDescription() + ")";
        showToast(message);

        return currentRecordingFile; // Return full path for callback
    }

    private boolean isFilePlayable(File file) {
        MediaPlayer player = new MediaPlayer();
        try {
            player.setDataSource(file.getAbsolutePath());
            player.prepare();
            int duration = player.getDuration();
            player.release();
            return duration > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void repairRecordingFile(File file) {
        if (!isRooted.get()) {
            return;
        }

        try {
            String tempPath = file.getAbsolutePath() + ".fixed";
            Process process = Runtime.getRuntime().exec(new String[]{
                    "su", "-c", "ffmpeg -y -i " + file.getAbsolutePath() + " -c copy " + tempPath
            });
            process.waitFor();

            File fixedFile = new File(tempPath);
            if (fixedFile.exists() && fixedFile.length() > file.length()) {
                file.delete();
                fixedFile.renameTo(file);
                Log.d(TAG, "✅ Recording file repaired");
            }
        } catch (Exception e) {
            Log.e(TAG, "File repair failed: " + e.getMessage());
        }
    }

    // ============================================================================
    // QUALITY ANALYSIS
    // ============================================================================

    private void analyzeRecordingQuality() {
        // Delay analysis to let recording stabilize
        new Handler().postDelayed(() -> {
            executorService.execute(() -> {
                try {
                    // Brief quality analysis
                    String qualityInfo = analyzeCurrentRecording();
                    Log.d(TAG, "📊 Recording quality: " + qualityInfo);

                    // Show user-friendly message
                    String userMessage = getUserFriendlyQualityMessage();
                    showToast(userMessage);

                } catch (Exception e) {
                    Log.e(TAG, "Quality analysis failed: " + e.getMessage());
                }
            });
        }, 3000);
    }

    private String analyzeCurrentRecording() {
        // Simple analysis based on audio source and system capabilities
        StringBuilder analysis = new StringBuilder();

        analysis.append("Source: ").append(getAudioSourceName(currentAudioSource));
        analysis.append(", Mode: ").append(currentRecordingMode.getDescription());

        if (isRooted.get()) {
            analysis.append(", Root fixes applied");
        }

        return analysis.toString();
    }

    private String getUserFriendlyQualityMessage() {
        switch (currentRecordingMode) {
            case VOICE_CALL_TWO_WAY:
                return "🎙️ High quality - Both sides recording";
            case VOICE_COMMUNICATION:
                return "🎙️ Good quality - Call optimized recording";
            case MICROPHONE_ONLY:
                return "🎙️ Basic quality - Your voice only";
            case VOICE_UPLINK:
                return "🎙️ Outgoing voice recorded";
            case VOICE_DOWNLINK:
                return "🎙️ Incoming voice recorded";
            default:
                return "🎙️ Recording active";
        }
    }

    // ============================================================================
    // UTILITY METHODS
    // ============================================================================

    private void cleanup() {
        isRecording.set(false);

        if (mediaRecorder != null) {
            try {
                mediaRecorder.release();
            } catch (Exception ignored) {}
            mediaRecorder = null;
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

    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    private String getAudioSourceName(int audioSource) {
        switch (audioSource) {
            case MediaRecorder.AudioSource.VOICE_CALL: return "VOICE_CALL";
            case MediaRecorder.AudioSource.VOICE_COMMUNICATION: return "VOICE_COMMUNICATION";
            case MediaRecorder.AudioSource.VOICE_UPLINK: return "VOICE_UPLINK";
            case MediaRecorder.AudioSource.VOICE_DOWNLINK: return "VOICE_DOWNLINK";
            case MediaRecorder.AudioSource.MIC: return "MIC";
            case MediaRecorder.AudioSource.DEFAULT: return "DEFAULT";
            default: return "UNKNOWN(" + audioSource + ")";
        }
    }

    private String[] concatenateArrays(String[] array1, String[] array2) {
        String[] result = new String[array1.length + array2.length];
        System.arraycopy(array1, 0, result, 0, array1.length);
        System.arraycopy(array2, 0, result, array1.length, array2.length);
        return result;
    }

    private void logDeviceInfo() {
        Log.d(TAG, "=== DEVICE INFO ===");
        Log.d(TAG, "Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        Log.d(TAG, "Device: " + Build.MANUFACTURER + " " + Build.MODEL);
        Log.d(TAG, "Root: " + (isRooted.get() ? "YES" : "NO"));
        Log.d(TAG, "VOICE_CALL accessible: " + (voiceCallAccessible.get() ? "YES" : "NO"));
    }

    // ============================================================================
    // PUBLIC GETTERS
    // ============================================================================

    public boolean isRecording() {
        return isRecording.get();
    }

    public RecordingMode getCurrentRecordingMode() {
        return currentRecordingMode;
    }

    public String getCurrentRecordingFile() {
        return currentRecordingFile;
    }

    public boolean isRooted() {
        return isRooted.get();
    }

    public boolean hasVoiceCallAccess() {
        return voiceCallAccessible.get();
    }
}