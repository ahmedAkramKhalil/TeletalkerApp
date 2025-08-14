package com.teletalker.app.services.injection;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;

public class MediaPlayerInjector extends BaseAudioInjector {
    private MediaPlayer mediaPlayer;

    public MediaPlayerInjector(Context context) {
        super(context);
    }

    @Override
    public boolean isAvailable() {
        try {
            // Test if we can create a MediaPlayer with VOICE_CALL stream
            MediaPlayer testPlayer = new MediaPlayer();
            testPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setLegacyStreamType(AudioManager.STREAM_VOICE_CALL)
                            .build()
            );
            testPlayer.release();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int getPriority() {
        // Highest priority as it's most compatible
        return 100;
    }

    @Override
    public InjectionMethod getMethod() {
        return InjectionMethod.MEDIA_PLAYER;
    }

    @Override
    public boolean inject(String audioPath, InjectionCallback callback) {
        this.callback = callback;

        try {
            Log.d(TAG, "Starting MediaPlayer injection...");

            // Prepare audio system
            prepareAudioSystem();

            // Create MediaPlayer
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setLegacyStreamType(AudioManager.STREAM_VOICE_CALL)
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
            );

            // Set data source
            if (audioPath.startsWith("assets/")) {
                String assetPath = audioPath.substring(7);
                AssetFileDescriptor afd = context.getAssets().openFd(assetPath);
                mediaPlayer.setDataSource(afd.getFileDescriptor(),
                        afd.getStartOffset(), afd.getLength());
                afd.close();
            } else {
                String filePath = resolveAudioPath(audioPath);
                mediaPlayer.setDataSource(filePath);
            }

            // Prepare and start
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                isInjecting = true;
                if (callback != null) {
                    callback.onInjectionStarted(getMethod());
                }
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                if (callback != null) {
                    callback.onInjectionCompleted();
                }
                stop();
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                if (callback != null) {
                    callback.onInjectionFailed("MediaPlayer error: " + what);
                }
                stop();
                return true;
            });

            mediaPlayer.prepareAsync();
            return true;

        } catch (Exception e) {
            Log.e(TAG, "MediaPlayer injection failed", e);
            if (callback != null) {
                callback.onInjectionFailed(e.getMessage());
            }
            stop();
            return false;
        }
    }

    private void prepareAudioSystem() {
        // Set audio mode
        audioManager.setMode(AudioManager.MODE_IN_CALL);

        // Apply root enhancements if available
        if (isRooted) {
            executeRootCommands(new String[]{
                    "setprop audio.voicecall.inject 1",
                    "dumpsys audio set-voice-call-routing 1",
                    "settings put system volume_voice_call 8"
            });

            // Manufacturer specific
            String manufacturer = Build.MANUFACTURER.toLowerCase();
            if (manufacturer.contains("samsung")) {
                executeRootCommand("setprop audio.samsung.voice_path on");
            } else if (manufacturer.contains("xiaomi")) {
                executeRootCommand("setprop persist.audio.voice.call on");
            }
        }
    }

    @Override
    public void stop() {
        isInjecting = false;

        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping MediaPlayer", e);
            }
            mediaPlayer = null;
        }

        // Restore audio mode
        audioManager.setMode(AudioManager.MODE_NORMAL);
    }
}