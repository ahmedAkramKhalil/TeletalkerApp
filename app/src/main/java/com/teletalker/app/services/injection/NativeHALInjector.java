package com.teletalker.app.services.injection;

import android.content.Context;
import android.util.Log;

public class NativeHALInjector extends BaseAudioInjector {
    private MediaPlayerInjector fallbackInjector;

    public NativeHALInjector(Context context) {
        super(context);
        this.fallbackInjector = new MediaPlayerInjector(context);
    }

    @Override
    public boolean isAvailable() {
        if (!isRooted) return false;

        // Check for tinymix or alsa
        String tinymixPath = executeRootCommand("which tinymix");
        String alsaPath = executeRootCommand("which alsa_amixer");

        return (tinymixPath != null && tinymixPath.contains("tinymix")) ||
                (alsaPath != null && alsaPath.contains("alsa"));
    }

    @Override
    public int getPriority() {
        // Lower priority, use only if others fail
        return 60;
    }

    @Override
    public InjectionMethod getMethod() {
        return InjectionMethod.NATIVE_HAL;
    }

    @Override
    public boolean inject(String audioPath, InjectionCallback callback) {
        this.callback = callback;

        try {
            Log.d(TAG, "Starting Native HAL injection...");

            // Apply HAL-level routing
            boolean hasTinymix = executeRootCommand("which tinymix") != null;

            if (hasTinymix) {
                executeRootCommands(new String[]{
                        "tinymix 'Voice Tx Mute' 0",
                        "tinymix 'TX1 Digital Volume' 84",
                        "tinymix 'Voice Rx Device' 'HANDSET'",
                        "tinymix 'Voice Call' 1"
                });
            } else {
                executeRootCommands(new String[]{
                        "alsa_amixer -c 0 set 'Voice Tx' unmute",
                        "alsa_amixer -c 0 set 'Voice Tx Volume' 80%",
                        "alsa_amixer -c 0 set 'Voice Call' on"
                });
            }

            // Additional audio routing
            executeRootCommands(new String[]{
                    "setprop audio.voice.volume.boost 2",
                    "setprop persist.audio.vns.mode 2",
                    "service call audio 3 i32 3" // MODE_IN_CALL
            });

            // Use MediaPlayer with enhanced routing
            return fallbackInjector.inject(audioPath, callback);

        } catch (Exception e) {
            Log.e(TAG, "Native HAL injection failed", e);
            if (callback != null) {
                callback.onInjectionFailed(e.getMessage());
            }
            return false;
        }
    }

    @Override
    public void stop() {
        fallbackInjector.stop();

        // Restore HAL settings
        if (isRooted) {
            boolean hasTinymix = executeRootCommand("which tinymix") != null;

            if (hasTinymix) {
                executeRootCommands(new String[]{
                        "tinymix 'Voice Call' 0",
                        "tinymix 'Voice Tx Mute' 1"
                });
            }
        }
    }
}