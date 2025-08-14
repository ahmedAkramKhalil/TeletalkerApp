package com.teletalker.app.services.injection;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class AudioTrackInjector extends BaseAudioInjector {
    private AudioTrack audioTrack;
    private Thread playbackThread;

    public AudioTrackInjector(Context context) {
        super(context);
    }

    @Override
    public boolean isAvailable() {
        try {
            int bufferSize = AudioTrack.getMinBufferSize(
                    TELEPHONY_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );

            AudioTrack test = new AudioTrack(
                    AudioManager.STREAM_VOICE_CALL,
                    TELEPHONY_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
            );

            boolean available = test.getState() == AudioTrack.STATE_INITIALIZED;
            test.release();

            return available;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int getPriority() {
        // Second priority
        return 80;
    }

    @Override
    public InjectionMethod getMethod() {
        return InjectionMethod.AUDIO_TRACK;
    }

    @Override
    public boolean inject(String audioPath, InjectionCallback callback) {
        this.callback = callback;

        try {
            Log.d(TAG, "Starting AudioTrack injection...");

            // Prepare audio system
            audioManager.setMode(AudioManager.MODE_IN_CALL);
            applyManufacturerFixes();

            // Read audio data
            byte[] audioData = loadAudioData(audioPath);

            // Create AudioTrack
            int bufferSize = Math.max(
                    audioData.length,
                    AudioTrack.getMinBufferSize(
                            TELEPHONY_SAMPLE_RATE,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                    )
            );

            audioTrack = new AudioTrack.Builder()
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

            // Write and play
            audioTrack.write(audioData, 0, audioData.length);

            audioTrack.setNotificationMarkerPosition(audioData.length / 2);
            audioTrack.setPlaybackPositionUpdateListener(
                    new AudioTrack.OnPlaybackPositionUpdateListener() {
                        @Override
                        public void onMarkerReached(AudioTrack track) {
                            if (callback != null) {
                                callback.onInjectionCompleted();
                            }
                            stop();
                        }

                        @Override
                        public void onPeriodicNotification(AudioTrack track) {
                            // Update progress if needed
                        }
                    }
            );

            audioTrack.play();
            isInjecting = true;

            if (callback != null) {
                callback.onInjectionStarted(getMethod());
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "AudioTrack injection failed", e);
            if (callback != null) {
                callback.onInjectionFailed(e.getMessage());
            }
            stop();
            return false;
        }
    }

    private byte[] loadAudioData(String audioPath) throws Exception {
        if (audioPath.startsWith("assets/")) {
            // Load from assets
            String assetPath = audioPath.substring(7);
            try (InputStream is = context.getAssets().open(assetPath);
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

                byte[] data = new byte[16384];
                int nRead;
                while ((nRead = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                return buffer.toByteArray();
            }
        } else {
            // Load from file
            String filePath = resolveAudioPath(audioPath);
            return readAudioFile(filePath);
        }
    }

    private void applyManufacturerFixes() {
        if (!isRooted) return;

        String manufacturer = android.os.Build.MANUFACTURER.toLowerCase();
        if (manufacturer.contains("samsung")) {
            executeRootCommand("setprop audio.samsung.voice_path on");
        } else if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi")) {
            executeRootCommand("setprop persist.audio.voice.call on");
        } else if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            executeRootCommand("setprop persist.audio.voicecall on");
        } else if (manufacturer.contains("oneplus") || manufacturer.contains("oppo")) {
            executeRootCommand("setprop persist.audio.voice.change.support true");
        }
    }

    @Override
    public void stop() {
        isInjecting = false;

        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping AudioTrack", e);
            }
            audioTrack = null;
        }

        audioManager.setMode(AudioManager.MODE_NORMAL);
    }
}