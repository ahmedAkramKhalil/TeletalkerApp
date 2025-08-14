package com.teletalker.app.services.callinjection;


import android.util.Log;
import java.util.Base64;

/**
 * Audio injection for devices without TinyMix support
 * Handles MediaTek, Samsung Exynos, and other non-Qualcomm devices
 */
public class NonTinyMixInjector {
    private static final String TAG = "NonTinyMixInjector";

    /**
     * Main injection method for non-TinyMix devices
     */
    public boolean inject(byte[] audioData, DeviceCapabilityDetector.DeviceType deviceType) {
        Log.d(TAG, "🔧 Starting non-TinyMix injection for: " + deviceType);

        // Try device-specific methods first
        if (tryDeviceSpecificInjection(audioData, deviceType)) {
            return true;
        }

        // Fallback to universal methods
        return tryUniversalMethods(audioData);
    }

    private boolean tryDeviceSpecificInjection(byte[] audioData, DeviceCapabilityDetector.DeviceType deviceType) {
        switch (deviceType) {
            case MEDIATEK:
                return tryMediaTekInjection(audioData);
            case SAMSUNG_EXYNOS:
                return tryExynosInjection(audioData);
            case HUAWEI_KIRIN:
                return tryHuaweiInjection(audioData);
            case LEGACY_ALSA:
                return tryLegacyALSAInjection(audioData);
            default:
                Log.d(TAG, "No specific method for device type: " + deviceType);
                return false;
        }
    }

    private boolean tryUniversalMethods(byte[] audioData) {
        Log.d(TAG, "🔄 Trying universal injection methods...");

        return tryDirectPCMInjection(audioData) ||
                tryAudioFlingerInjection(audioData) ||
                tryKernelAudioInjection(audioData);
    }

    // MediaTek-specific injection
    private boolean tryMediaTekInjection(byte[] audioData) {
        Log.d(TAG, "📱 Trying MediaTek injection...");

        String[] commands = {
                // MediaTek-specific properties
                "su -c 'setprop mtk.audio.call.inject 1'",
                "su -c 'setprop vendor.mtk.audio.inject true'",

                // Write to MediaTek injection points
                "su -c 'echo \"" + Base64.getEncoder().encodeToString(audioData) +
                        "\" | base64 -d > /data/vendor/audiohal/inject.pcm'",

                "su -c 'echo \"" + Base64.getEncoder().encodeToString(audioData) +
                        "\" | base64 -d > /sys/kernel/debug/mtk_audio/inject'",

                // Alternative MediaTek paths
                "su -c 'echo \"" + Base64.getEncoder().encodeToString(audioData) +
                        "\" | base64 -d > /proc/mtk_audio/call_inject'",

                // Trigger injection
                "su -c 'echo 1 > /sys/class/mtk_audio/inject_enable'"
        };

        return CommandExecutor.executeFirstSuccessful(commands);
    }

    // Samsung Exynos injection
    private boolean tryExynosInjection(byte[] audioData) {
        Log.d(TAG, "📱 Trying Samsung Exynos injection...");

        String[] commands = {
                // Samsung-specific properties
                "su -c 'setprop vendor.samsung.audio.inject 1'",
                "su -c 'setprop ro.config.samsung_audio inject'",

                // Write to Samsung injection points
                "su -c 'echo \"" + Base64.getEncoder().encodeToString(audioData) +
                        "\" | base64 -d > /sys/kernel/debug/samsung_audio/inject'",

                "su -c 'echo \"" + Base64.getEncoder().encodeToString(audioData) +
                        "\" | base64 -d > /data/vendor/samsung/audio/inject.pcm'",

                // Alternative Samsung paths
                "su -c 'echo \"" + Base64.getEncoder().encodeToString(audioData) +
                        "\" | base64 -d > /proc/samsung_audio/call_inject'",

                // Trigger Samsung injection
                "su -c 'echo 1 > /sys/class/samsung_audio/inject_enable'"
        };

        return CommandExecutor.executeFirstSuccessful(commands);
    }

    // Huawei Kirin injection
    private boolean tryHuaweiInjection(byte[] audioData) {
        Log.d(TAG, "📱 Trying Huawei Kirin injection...");

        String[] commands = {
                // Huawei-specific properties
                "su -c 'setprop hw.audio.call.inject true'",
                "su -c 'setprop vendor.huawei.audio.inject 1'",

                // Write to Huawei injection points
                "su -c 'echo \"" + Base64.getEncoder().encodeToString(audioData) +
                        "\" | base64 -d > /data/vendor/huawei/audio/inject.pcm'",

                "su -c 'echo \"" + Base64.getEncoder().encodeToString(audioData) +
                        "\" | base64 -d > /sys/kernel/debug/hisi_audio/inject'"
        };

        return CommandExecutor.executeFirstSuccessful(commands);
    }

    // Legacy ALSA injection (older devices)
    private boolean tryLegacyALSAInjection(byte[] audioData) {
        Log.d(TAG, "📱 Trying legacy ALSA injection...");

        String tempFile = "/data/local/tmp/alsa_inject.pcm";

        String[] commands = {
                // Write audio data to temp file
                "su -c 'echo -n \"" + Base64.getEncoder().encodeToString(audioData) +
                        "\" | base64 -d > " + tempFile + "'",

                // Try amixer controls (alternative to tinymix)
                "su -c 'amixer -c 0 set \"Voice Call\" 100% unmute 2>/dev/null'",
                "su -c 'amixer -c 0 set \"Call Injection\" on 2>/dev/null'",

                // Direct ALSA playback
                "su -c 'aplay -D plughw:0,0 -f S16_LE -r 16000 -c 1 " + tempFile + " 2>/dev/null'",
                "su -c 'aplay -D plughw:0,1 -f S16_LE -r 16000 -c 1 " + tempFile + " 2>/dev/null'",

                // Cleanup
                "su -c 'rm " + tempFile + "'"
        };

        return CommandExecutor.executeSequential(commands);
    }

    // Direct PCM injection (universal method)
    private boolean tryDirectPCMInjection(byte[] audioData) {
        Log.d(TAG, "🔧 Trying direct PCM injection...");

        String tempFile = "/data/local/tmp/direct_inject.pcm";

        String[] commands = {
                // Write audio data
                "su -c 'echo -n \"" + Base64.getEncoder().encodeToString(audioData) +
                        "\" | base64 -d > " + tempFile + "'",

                // Try various PCM devices
                "su -c 'cat " + tempFile + " > /dev/snd/pcmC0D0p 2>/dev/null'",
                "su -c 'cat " + tempFile + " > /dev/snd/pcmC0D1p 2>/dev/null'",
                "su -c 'cat " + tempFile + " > /dev/snd/pcmC0D2p 2>/dev/null'",
                "su -c 'cat " + tempFile + " > /dev/snd/pcmC1D0p 2>/dev/null'",

                // Alternative aplay commands
                "su -c 'aplay -D hw:0,0 -f S16_LE -r 16000 -c 1 " + tempFile + " 2>/dev/null'",
                "su -c 'aplay -D hw:0,1 -f S16_LE -r 16000 -c 1 " + tempFile + " 2>/dev/null'",

                // Cleanup
                "su -c 'rm " + tempFile + "'"
        };

        return CommandExecutor.executeFirstSuccessful(commands);
    }

    // AudioFlinger injection (Android system service)
    private boolean tryAudioFlingerInjection(byte[] audioData) {
        Log.d(TAG, "🔧 Trying AudioFlinger injection...");

        String[] commands = {
                // Enable AudioFlinger injection
                "su -c 'setprop af.inject.enable 1'",
                "su -c 'setprop af.inject.format pcm_16'",
                "su -c 'setprop af.inject.rate 16000'",

                // Write to AudioFlinger injection point
                "su -c 'echo \"" + Base64.getEncoder().encodeToString(audioData) +
                        "\" | base64 -d > /data/misc/audio/inject.pcm'",

                // Trigger injection
                "su -c 'setprop af.inject.trigger 1'",

                // Wait and cleanup
                "su -c 'sleep 1'",
                "su -c 'setprop af.inject.enable 0'"
        };

        return CommandExecutor.executeSequential(commands);
    }

    // Kernel-level audio injection
    private boolean tryKernelAudioInjection(byte[] audioData) {
        Log.d(TAG, "🔧 Trying kernel audio injection...");

        String[] commands = {
                // Try common kernel injection points
                "su -c 'echo \"" + Base64.getEncoder().encodeToString(audioData) +
                        "\" | base64 -d > /sys/kernel/debug/audio/inject'",

                "su -c 'echo \"" + Base64.getEncoder().encodeToString(audioData) +
                        "\" | base64 -d > /proc/audio/call_inject'",

                "su -c 'echo \"" + Base64.getEncoder().encodeToString(audioData) +
                        "\" | base64 -d > /dev/audio_inject'",

                // Trigger kernel injection
                "su -c 'echo 1 > /sys/kernel/debug/audio/inject_enable'",
                "su -c 'echo trigger > /proc/audio/inject_start'"
        };

        return CommandExecutor.executeFirstSuccessful(commands);
    }
}