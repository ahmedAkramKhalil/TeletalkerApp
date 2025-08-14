package com.teletalker.app.services;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
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
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;

import com.teletalker.app.services.injection.AudioInjectionManager;
import com.teletalker.app.services.injection.AudioInjector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
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
    // Audio configuration
    private static final int SAMPLE_RATE = 44100;
    private static final int TELEPHONY_SAMPLE_RATE = 8000; // For injection
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

    // Audio injection modes
    public enum InjectionMode {
        MEDIA_PLAYER("MediaPlayer with STREAM_VOICE_CALL"),
        AUDIO_TRACK("AudioTrack with telephony routing"),
        NATIVE_HAL("Native HAL injection"),
        FAILED("Injection failed");

        private final String description;
        InjectionMode(String description) {
            this.description = description;
        }
        public String getDescription() {
            return description;
        }
    }



    public interface RecordingCallback {
        void onRecordingStarted(RecordingMode mode);
        void onRecordingFailed(String reason);
        void onRecordingStopped(String filename, long duration);
    }

    public interface InjectionCallback {
        void onInjectionStarted(AudioInjector.InjectionMethod mode);
        void onInjectionFailed(String reason);
        void onInjectionCompleted();
    }

    // Instance variables
    private Context context;
    private MediaRecorder mediaRecorder;
    private MediaPlayer injectionPlayer;
    private AudioTrack injectionTrack;
    private AudioManager audioManager;
    private ExecutorService executorService;
    private Handler mainHandler;
    private RecordingCallback recordingCallback;
    private InjectionCallback injectionCallback;

    // State tracking
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private final AtomicBoolean isInjecting = new AtomicBoolean(false);
    private String currentRecordingFile;
    private long recordingStartTime;
    private RecordingMode currentRecordingMode = RecordingMode.UNKNOWN;
    private InjectionMode currentInjectionMode = InjectionMode.FAILED;
    private int currentAudioSource;

    // System capabilities
    private final AtomicBoolean isRooted = new AtomicBoolean(false);
    private final AtomicBoolean voiceCallAccessible = new AtomicBoolean(false);

    private AudioInjectionManager injectionManager;


    // Audio state backup
    private int originalAudioMode;
    private boolean originalSpeakerphoneOn;

    public CallRecorder(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.injectionManager = new AudioInjectionManager(context);

        // Initialize system capabilities
        executorService.execute(this::initializeCapabilities);
    }

    public void setRecordingCallback(RecordingCallback callback) {
        this.recordingCallback = callback;
    }

    public void setInjectionCallback(InjectionCallback callback) {
        this.injectionCallback = callback;
    }

    private CallRecorder.InjectionMode convertToRecorderInjectionMode(AudioInjector.InjectionMethod method) {
        switch (method) {
            case MEDIA_PLAYER:
                return CallRecorder.InjectionMode.MEDIA_PLAYER;
            case AUDIO_TRACK:
                return CallRecorder.InjectionMode.AUDIO_TRACK;
            case NATIVE_HAL:
                return CallRecorder.InjectionMode.NATIVE_HAL;
            case XPOSED_HOOK:
            case ACCESSIBILITY:
            default:
                return CallRecorder.InjectionMode.FAILED;
        }
    }

    private AudioInjector.InjectionMethod currentInjectionMethod;

    private CallAudioInjector audioInjector;

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public boolean injectAudioToCall(String audioPath) {
        audioInjector = new CallAudioInjector(context);

//        audioInjector.injectAudio(audioPath, new CallAudioInjector.InjectionCallback() {
//            @Override
//            public void onInjectionStarted() {
//                Log.d("CallInjector","onInjectionStarted");
//            }
//
//            @Override
//            public void onInjectionCompleted(boolean success) {
//                Log.d("CallInjector","onInjectionCompleted");
//
//            }
//
//            @Override
//            public void onInjectionError(String error) {
//                Log.d("CallInjector","error" + error);
//
//            }
//        });


//        if (!hasRecordAudioPermission()) {
//            notifyInjectionCallback(cb -> cb.onInjectionFailed("RECORD_AUDIO permission not granted"));
//            return false;
//        }
//
//        return  injectionManager.injectWithStreamingAudioTrack(audioPath, new InjectionCallback() {
//            @Override
//            public void onInjectionStarted(AudioInjector.InjectionMethod mode) {
//                Log.d("InjectionTest","onInjectionStarted");
//
//            }
//
//            @Override
//            public void onInjectionFailed(String reason) {
//                Log.d("InjectionTest","onInjectionFailed");
//
//            }
//
//            @Override
//            public void onInjectionCompleted() {
//                Log.d("InjectionTest","onInjectionCompleted");
//
//            }
//        });
        return true;
    }




    public void stopInjection() {
//        injectionManager.stopInjection();
//        isInjecting.set(false);
    }

    // Test injection methods
    public void testInjectionMethods(AudioInjectionManager.TestCallback callback) {
        injectionManager.testInjectionMethods(callback);
    }

    public interface IAudioInjector extends android.os.IInterface {
        void startInjection(int audioSessionId) throws RemoteException;
        void stopInjection() throws RemoteException;
        void injectAudioData(byte[] data) throws RemoteException;
    }

    private AudioTrack audioTrack;
    private Thread injectionThread;



    private void startAudioInjection(int audioSessionId) {
        if (isInjecting.get()) {
            Log.w(TAG, "Injection already running");
            return;
        }

        try {
            int bufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );

            audioTrack = new AudioTrack(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    bufferSize,
                    AudioTrack.MODE_STREAM,
                    audioSessionId
            );

            audioTrack.play();
            isInjecting.set(true);

            // Start a thread to maintain the injection
            injectionThread = new Thread(() -> {
                while (isInjecting.get()) {
                    try {
                        // Keep the stream alive
                        byte[] silence = new byte[bufferSize];
                        audioTrack.write(silence, 0, silence.length);
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Injection thread interrupted", e);
                    }
                }
            });
            injectionThread.start();

            Log.d(TAG, "Audio injection started with session ID: " + audioSessionId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start audio injection", e);
            stopAudioInjection();
        }
    }

    private void stopAudioInjection() {
        if (!isInjecting.get()) return;

        isInjecting.set(false);

        try {
            if (injectionThread != null) {
                injectionThread.interrupt();
                injectionThread.join(500);
            }

            if (audioTrack != null) {
                audioTrack.stop();
                audioTrack.release();
                audioTrack = null;
            }

            Log.d(TAG, "Audio injection stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping audio injection", e);
        }
    }






    /**
     * Stop ongoing audio injection
     */

    // ============================================================================
    // INJECTION IMPLEMENTATIONS
    // ============================================================================

    private boolean injectWithMediaPlayer(String audioFilePath) {
        try {
            Log.d(TAG, "🎵 Attempting MediaPlayer injection...");

            // Prepare audio routing
            audioManager.setMode(AudioManager.MODE_IN_CALL);

            // Root commands for enhanced routing
            if (isRooted.get()) {
                executeRootCommands(new String[]{
                        "setprop audio.voicecall.inject 1",
                        "dumpsys audio set-voice-call-routing 1",
                        "settings put system volume_voice_call 8"
                });
            }

            // Create and configure MediaPlayer
            injectionPlayer = new MediaPlayer();
            injectionPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setLegacyStreamType(AudioManager.STREAM_VOICE_CALL)
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
            );

            injectionPlayer.setDataSource(audioFilePath);
            injectionPlayer.prepare();

            // Set completion listener
            injectionPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG, "Audio injection completed");
                stopInjection();
            });

            injectionPlayer.start();
            Log.d(TAG, "✅ MediaPlayer injection started");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "MediaPlayer injection failed: " + e.getMessage());
            if (injectionPlayer != null) {
                injectionPlayer.release();
                injectionPlayer = null;
            }
            return false;
        }
    }

    private boolean injectWithAudioTrack(String audioFilePath) {
        try {
            Log.d(TAG, "🎵 Attempting AudioTrack injection...");

            // Read audio file
            File audioFile = new File(audioFilePath);
            FileInputStream fis = new FileInputStream(audioFile);
            byte[] audioData = new byte[(int) audioFile.length()];
            fis.read(audioData);
            fis.close();

            // Configure audio routing
            audioManager.setMode(AudioManager.MODE_IN_CALL);

            // Enhanced routing for different manufacturers
            if (isRooted.get()) {
                String manufacturer = Build.MANUFACTURER.toLowerCase();
                if (manufacturer.contains("samsung")) {
                    executeRootCommand("setprop audio.samsung.voice_path on");
                } else if (manufacturer.contains("xiaomi")) {
                    executeRootCommand("setprop persist.audio.voice.call on");
                } else if (manufacturer.contains("huawei")) {
                    executeRootCommand("setprop persist.audio.voicecall on");
                }
            }

            // Create AudioTrack for telephony
            int bufferSize = AudioTrack.getMinBufferSize(
                    TELEPHONY_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );

            injectionTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(TELEPHONY_SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();

            // Write audio data
            injectionTrack.write(audioData, 0, audioData.length);

            // Set notification for completion
            injectionTrack.setNotificationMarkerPosition(audioData.length / 2); // 16-bit = 2 bytes per sample
            injectionTrack.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                @Override
                public void onMarkerReached(AudioTrack track) {
                    Log.d(TAG, "AudioTrack injection completed");
                    stopInjection();
                }

                @Override
                public void onPeriodicNotification(AudioTrack track) {}
            });

            injectionTrack.play();
            Log.d(TAG, "✅ AudioTrack injection started");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "AudioTrack injection failed: " + e.getMessage());
            if (injectionTrack != null) {
                injectionTrack.release();
                injectionTrack = null;
            }
            return false;
        }
    }

    private boolean injectWithNativeHAL(String audioFilePath) {
        try {
            Log.d(TAG, "🎵 Attempting Native HAL injection...");

            // Use tinymix or alsa commands based on availability
            String checkTinymix = executeRootCommand("which tinymix");
            boolean hasTinymix = checkTinymix != null && checkTinymix.contains("tinymix");

            if (hasTinymix) {
                // Use tinymix commands
                executeRootCommands(new String[]{
                        "tinymix 'Voice Tx Mute' 0",
                        "tinymix 'TX1 Digital Volume' 84",
                        "tinymix 'Voice Rx Device' 'HANDSET'"
                });
            } else {
                // Try alsa_amixer
                executeRootCommands(new String[]{
                        "alsa_amixer -c 0 set 'Voice Tx' unmute",
                        "alsa_amixer -c 0 set 'Voice Tx Volume' 80%"
                });
            }

            // Still use MediaPlayer but with enhanced routing
            return injectWithMediaPlayer(audioFilePath);

        } catch (Exception e) {
            Log.e(TAG, "Native HAL injection failed: " + e.getMessage());
            return false;
        }
    }

    // ============================================================================
    // AUDIO STATE MANAGEMENT
    // ============================================================================

    private void backupAudioState() {
        originalAudioMode = audioManager.getMode();
        originalSpeakerphoneOn = audioManager.isSpeakerphoneOn();

        Log.d(TAG, "Audio state backed up - Mode: " + originalAudioMode +
                ", Speaker: " + originalSpeakerphoneOn);
    }

    private void restoreAudioState() {
        try {
            audioManager.setMode(originalAudioMode);
            audioManager.setSpeakerphoneOn(originalSpeakerphoneOn);

            if (isRooted.get()) {
                executeRootCommand("setprop audio.voicecall.inject 0");
            }

            Log.d(TAG, "Audio state restored");
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore audio state: " + e.getMessage());
        }
    }

    private void applyInjectionFixes() {
        if (!isRooted.get()) {
            return;
        }

        try {
            Log.d(TAG, "🔧 Applying injection fixes...");

            String packageName = context.getPackageName();

            // Essential permissions and properties
            String[] commands = {
                    // AppOps permissions
                    "appops set " + packageName + " RECORD_AUDIO allow",
                    "appops set " + packageName + " PHONE_CALL_MICROPHONE allow",

                    // Audio properties for injection
                    "setprop persist.audio.voicecall true",
                    "setprop audio.telephony.inject 1",
                    "setprop persist.vendor.audio.voicecall.speaker.stereo true",

                    // Service calls for audio routing
                    "service call audio 31 i32 0",  // Set communication device
                    "service call audio 32 i32 1"   // Enable voice call routing
            };

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ specific
                String[] android12Commands = {
                        "appops set " + packageName + " RECORD_AUDIO_OUTPUT allow",
                        "setprop persist.vendor.radio.enable_voicecall_recording true",
                        "setprop persist.vendor.radio.enable_voicecall_injection true"
                };
                commands = concatenateArrays(commands, android12Commands);
            }

            executeRootCommands(commands);
            Thread.sleep(500); // Let changes take effect

            Log.d(TAG, "✅ Injection fixes applied");

        } catch (Exception e) {
            Log.e(TAG, "Failed to apply injection fixes: " + e.getMessage());
        }
    }



    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public boolean startRecording(String filename) {
        // [Keep all existing recording code as is]
        if (isRecording.get()) {
            Log.w(TAG, "Recording already in progress");
            return false;
        }

        if (!hasRecordAudioPermission()) {
            notifyRecordingCallback(cb -> cb.onRecordingFailed("RECORD_AUDIO permission not granted"));
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
                notifyRecordingCallback(cb -> cb.onRecordingStarted(currentRecordingMode));
                Log.d(TAG, "✅ Recording started with " + currentRecordingMode.getDescription());
                return true;
            } else {
                Log.e(TAG, "❌ Failed to start recording with any audio source");
                notifyRecordingCallback(cb -> cb.onRecordingFailed("No compatible audio source found"));
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "Exception starting recording: " + e.getMessage());
            notifyRecordingCallback(cb -> cb.onRecordingFailed("Exception: " + e.getMessage()));
            cleanup();
            return false;
        }
    }

    // [Keep all other existing methods as they are]

    // ============================================================================
    // UTILITY METHODS (ENHANCED)
    // ============================================================================

    private void notifyRecordingCallback(CallbackAction<RecordingCallback> action) {
        if (recordingCallback != null) {
            mainHandler.post(() -> action.execute(recordingCallback));
        }
    }

    private void notifyInjectionCallback(CallbackAction<InjectionCallback> action) {
        if (injectionCallback != null) {
            mainHandler.post(() -> action.execute(injectionCallback));
        }
    }

    @FunctionalInterface
    private interface CallbackAction<T> {
        void execute(T callback);
    }

    private String executeRootCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            process.waitFor();
            return output.toString().trim();
        } catch (Exception e) {
            Log.e(TAG, "Failed to execute: " + command, e);
            return null;
        }
    }

    // ============================================================================
    // PUBLIC GETTERS (ENHANCED)
    // ============================================================================

    public boolean isInjecting() {
        return isInjecting.get();
    }

    public InjectionMode getCurrentInjectionMode() {
        return currentInjectionMode;
    }



    // ============================================================================
    // MAIN RECORDING METHODS
    // ============================================================================


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

            // Use the correct callback method name
            notifyRecordingCallback(cb -> cb.onRecordingStopped(finalFilename, recordingDuration));
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