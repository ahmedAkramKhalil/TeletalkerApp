package com.teletalker.app.services.callinjection;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;
import android.media.AudioDeviceInfo; // Make sure this is imported

/**
 * Enhanced Audio Injection Solutions for Android Call Recording
 * Multiple fallback approaches when TinyMix/ALSA are unavailable
 *
 * Modified to allow injection of pre-processed audio into the call.
 */
public class EnhancedCallInjectionManager {
    private static final String TAG = "EnhancedCallInjection";

    private Context context;
    private AudioManager audioManager;
    private boolean isRooted;

    // Multiple AudioTrack instances for different approaches
    private AudioTrack voiceCallTrack;      // STREAM_VOICE_CALL
    private AudioTrack musicTrack;          // STREAM_MUSIC
    private AudioTrack systemTrack;         // STREAM_SYSTEM
    private AudioTrack dtmfTrack;           // STREAM_DTMF
    private MediaPlayer mediaPlayer;        // MediaPlayer approach - Note: MediaPlayer is for files, not real-time PCM injection.

    // Common audio format for injection
    private static final int SAMPLE_RATE = 16000; // Hz
    private static final int CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private int injectionBufferSize;

    public EnhancedCallInjectionManager(Context context, boolean isRooted) {
        this.context = context;
        this.isRooted = isRooted;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.injectionBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT);

        initializeAllInjectionMethods();
    }

    /**
     * SOLUTION 1: Multiple AudioTrack Streams
     * Try different audio streams that might work on your device
     */
    private void initializeAllInjectionMethods() {
        Log.d(TAG, "Initializing all injection methods...");

        // Try STREAM_VOICE_CALL - most likely for call injection
        try {
            voiceCallTrack = new AudioTrack(
                    AudioManager.STREAM_VOICE_CALL, // Prioritize this for in-call audio
                    SAMPLE_RATE,
                    CHANNEL_CONFIG_OUT,
                    AUDIO_FORMAT,
                    injectionBufferSize,
                    AudioTrack.MODE_STREAM
            );
            if (voiceCallTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                voiceCallTrack.play();
                Log.d(TAG, "VoiceCall AudioTrack initialized and playing.");
            } else {
                Log.w(TAG, "VoiceCall AudioTrack failed to initialize.");
                voiceCallTrack.release(); // Release if not initialized
                voiceCallTrack = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing VoiceCall AudioTrack: " + e.getMessage());
            voiceCallTrack = null;
        }

        // Try STREAM_MUSIC - common fallback, but usually for general media, not calls
        try {
            musicTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG_OUT,
                    AUDIO_FORMAT,
                    injectionBufferSize,
                    AudioTrack.MODE_STREAM
            );
            if (musicTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                musicTrack.play();
                Log.d(TAG, "Music AudioTrack initialized and playing.");
            } else {
                Log.w(TAG, "Music AudioTrack failed to initialize.");
                musicTrack.release();
                musicTrack = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Music AudioTrack: " + e.getMessage());
            musicTrack = null;
        }

        // Try STREAM_SYSTEM - for system sounds, less likely for call injection
        try {
            systemTrack = new AudioTrack(
                    AudioManager.STREAM_SYSTEM,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG_OUT,
                    AUDIO_FORMAT,
                    injectionBufferSize,
                    AudioTrack.MODE_STREAM
            );
            if (systemTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                systemTrack.play();
                Log.d(TAG, "System AudioTrack initialized and playing.");
            } else {
                Log.w(TAG, "System AudioTrack failed to initialize.");
                systemTrack.release();
                systemTrack = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing System AudioTrack: " + e.getMessage());
            systemTrack = null;
        }

        // Try STREAM_DTMF - for DTMF tones, not suitable for continuous audio
        try {
            dtmfTrack = new AudioTrack(
                    AudioManager.STREAM_DTMF,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG_OUT,
                    AUDIO_FORMAT,
                    injectionBufferSize,
                    AudioTrack.MODE_STREAM
            );
            if (dtmfTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                dtmfTrack.play();
                Log.d(TAG, "DTMF AudioTrack initialized and playing.");
            } else {
                Log.w(TAG, "DTMF AudioTrack failed to initialize.");
                dtmfTrack.release();
                dtmfTrack = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing DTMF AudioTrack: " + e.getMessage());
            dtmfTrack = null;
        }

        // MediaPlayer is not for real-time PCM injection, so it's not used for injectProcessedAudio.
        // Its initialization is kept for completeness if other features rely on it.
        try {
            mediaPlayer = new MediaPlayer();
            Log.d(TAG, "MediaPlayer initialized.");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing MediaPlayer: " + e.getMessage());
            mediaPlayer = null;
        }
    }

    /**
     * Injects already processed PCM audio data into the call.
     * It attempts to write to the most suitable AudioTrack available.
     * This method assumes the audio has already been captured and modified
     * by another component (e.g., AICallRecorder).
     *
     * @param audioData The byte array containing the processed PCM audio data.
     * @return true if audio was written to at least one AudioTrack, false otherwise.
     */
    public boolean injectProcessedAudio(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            Log.w(TAG, "No audio data provided for injection.");
            return false;
        }

        // Ensure AudioManager is in a communication mode for better routing
        if (audioManager.getMode() != AudioManager.MODE_IN_CALL &&
                audioManager.getMode() != AudioManager.MODE_IN_COMMUNICATION) {
            Log.d(TAG, "Setting AudioManager mode to MODE_IN_COMMUNICATION for injection.");
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        }

        boolean injected = false;
        int writtenBytes = 0;

        // Prioritize STREAM_VOICE_CALL as it's most relevant for in-call audio
        if (voiceCallTrack != null && voiceCallTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            try {
                writtenBytes = voiceCallTrack.write(audioData, 0, audioData.length);
                if (writtenBytes > 0) {
                    Log.d(TAG, "Injected " + writtenBytes + " bytes to VoiceCall AudioTrack.");
                    injected = true;
                } else {
                    Log.w(TAG, "Failed to write to VoiceCall AudioTrack. Error: " + writtenBytes);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error writing to VoiceCall AudioTrack: " + e.getMessage());
            }
        }

        // Fallback to STREAM_MUSIC if VoiceCall fails or isn't available
        if (!injected && musicTrack != null && musicTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            try {
                writtenBytes = musicTrack.write(audioData, 0, audioData.length);
                if (writtenBytes > 0) {
                    Log.d(TAG, "Injected " + writtenBytes + " bytes to Music AudioTrack (fallback).");
                    injected = true;
                } else {
                    Log.w(TAG, "Failed to write to Music AudioTrack. Error: " + writtenBytes);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error writing to Music AudioTrack: " + e.getMessage());
            }
        }

        // Add more fallbacks if needed, in order of preference
        // e.g., systemTrack, dtmfTrack, but these are less likely for general speech injection.

        if (!injected) {
            Log.e(TAG, "Failed to inject audio using any available AudioTrack.");
            return false;
        }
        return true;
    }

    /**
     * Performs a quick check to determine if direct audio injection into a call
     * is likely possible on this device.
     * This checks for the initialization of a STREAM_VOICE_CALL AudioTrack
     * and the presence of a TYPE_TELEPHONY output device.
     *
     * @return true if direct injection appears feasible, false otherwise.
     */
    @TargetApi(Build.VERSION_CODES.M) // For AudioDeviceInfo
    public boolean testInjectionCapability() {
        Log.d(TAG, "🧪 Running quick injection capability test...");

        // Check if an AudioTrack configured for voice call stream can be initialized
        boolean voiceCallTrackInitialized = false;
        AudioTrack testVoiceTrack = null;
        try {
            testVoiceTrack = new AudioTrack(
                    AudioManager.STREAM_VOICE_CALL,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG_OUT,
                    AUDIO_FORMAT,
                    injectionBufferSize,
                    AudioTrack.MODE_STREAM
            );
            voiceCallTrackInitialized = (testVoiceTrack.getState() == AudioTrack.STATE_INITIALIZED);
            if (voiceCallTrackInitialized) {
                Log.d(TAG, "Test: AudioTrack for STREAM_VOICE_CALL initialized.");
            } else {
                Log.w(TAG, "Test: AudioTrack for STREAM_VOICE_CALL failed to initialize.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Test: Error initializing STREAM_VOICE_CALL AudioTrack: " + e.getMessage());
            voiceCallTrackInitialized = false;
        } finally {
            if (testVoiceTrack != null) {
                testVoiceTrack.release();
            }
        }

        // Check for TYPE_TELEPHONY output device
        boolean telephonyDeviceFound = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo device : devices) {
                if (device.getType() == AudioDeviceInfo.TYPE_TELEPHONY) {
                    telephonyDeviceFound = true;
                    Log.d(TAG, "Test: TYPE_TELEPHONY audio device found.");
                    break;
                }
            }
            if (!telephonyDeviceFound) {
                Log.w(TAG, "Test: TYPE_TELEPHONY audio device NOT found.");
            }
        } else {
            Log.d(TAG, "Test: TYPE_TELEPHONY check skipped (API < M).");
        }

        // Consider injection capable if either STREAM_VOICE_CALL track initialized OR TYPE_TELEPHONY found
        // The TYPE_TELEPHONY is a stronger indicator for direct injection.
        boolean likelyCapable = voiceCallTrackInitialized || telephonyDeviceFound;

        Log.d(TAG, "🧪 Quick injection capability test result: " + (likelyCapable ? "POSSIBLE" : "UNLIKELY"));
        return likelyCapable;
    }


    /**
     * Generates a detailed report of the device's audio injection capabilities.
     * This method does not perform real-time injection but tests the
     * initialization of various AudioTrack streams and system properties.
     *
     * @return A string containing the capability report.
     */
    public String getDetailedCapabilities() {
        StringBuilder report = new StringBuilder();
        report.append("--- Enhanced Call Injection Capability Report ---\n");
        report.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        report.append("Android: ").append(Build.VERSION.RELEASE).append("\n");
        report.append("Root: ").append(isRooted ? "Yes" : "No").append("\n\n");

        report.append("AudioTrack Availability:\n");
        report.append("├─ Voice Call: ").append(voiceCallTrack != null ? "✅" : "❌").append(" (State: ").append(voiceCallTrack != null ? voiceCallTrack.getState() : "N/A").append(")\n");
        report.append("├─ Music Stream: ").append(musicTrack != null ? "✅" : "❌").append(" (State: ").append(musicTrack != null ? musicTrack.getState() : "N/A").append(")\n");
        report.append("├─ System Stream: ").append(systemTrack != null ? "✅" : "❌").append(" (State: ").append(systemTrack != null ? systemTrack.getState() : "N/A").append(")\n");
        report.append("├─ DTMF Stream: ").append(dtmfTrack != null ? "✅" : "❌").append(" (State: ").append(dtmfTrack != null ? dtmfTrack.getState() : "N/A").append(")\n");
        report.append("└─ MediaPlayer: ").append(mediaPlayer != null ? "✅" : "❌").append(" (Note: For file playback, not real-time PCM)\n\n");

        report.append("Current Audio Mode: ").append(getAudioManagerModeName(audioManager.getMode())).append("\n");
        report.append("Speakerphone On: ").append(audioManager.isSpeakerphoneOn()).append("\n");

        // Add more checks for AudioDeviceInfo.TYPE_TELEPHONY if relevant for this class's role
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean telephonyDeviceFound = false;
            for (android.media.AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                if (device.getType() == android.media.AudioDeviceInfo.TYPE_TELEPHONY) {
                    telephonyDeviceFound = true;
                    break;
                }
            }
            report.append("Telephony Output Device (API 23+): ").append(telephonyDeviceFound ? "✅ Found" : "❌ Not Found").append("\n");
        }


        report.append("\n--- End Report ---");
        return report.toString();
    }

    private String getAudioManagerModeName(int mode) {
        switch (mode) {
            case AudioManager.MODE_NORMAL: return "NORMAL";
            case AudioManager.MODE_RINGTONE: return "RINGTONE";
            case AudioManager.MODE_IN_CALL: return "IN_CALL";
            case AudioManager.MODE_IN_COMMUNICATION: return "IN_COMMUNICATION";
            default: return "UNKNOWN (" + mode + ")";
        }
    }


    public void cleanup() {
        Log.d(TAG, "Cleaning up EnhancedCallInjectionManager resources.");
        if (voiceCallTrack != null) {
            if (voiceCallTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) voiceCallTrack.stop();
            voiceCallTrack.release();
            voiceCallTrack = null;
        }
        if (musicTrack != null) {
            if (musicTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) musicTrack.stop();
            musicTrack.release();
            musicTrack = null;
        }
        if (systemTrack != null) {
            if (systemTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) systemTrack.stop();
            systemTrack.release();
            systemTrack = null;
        }
        if (dtmfTrack != null) {
            if (dtmfTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) dtmfTrack.stop();
            dtmfTrack.release();
            dtmfTrack = null;
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        // It's good practice to reset AudioManager mode if your app was the one changing it
        // audioManager.setMode(AudioManager.MODE_NORMAL); // Only do this if you manage the mode exclusively
    }
}