package com.teletalker.app.services.callinjection;


import android.os.Build;
import android.util.Log;
import java.util.Base64;

/**
 * Audio injection using TinyMix controls
 * Primarily for Qualcomm-based devices
 */
public class TinyMixInjector {
    private static final String TAG = "TinyMixInjector";

    /**
     * Inject audio using TinyMix controls
     */
    public boolean inject(byte[] audioData) {
        Log.d(TAG, "🎛️ Starting TinyMix injection...");

        // First, try to enable injection via mixer controls
        if (!enableInjectionControls()) {
            Log.w(TAG, "⚠️ Failed to enable injection controls");
        }

        // Try different TinyMix injection methods
        return tryStandardTinyMixInjection(audioData) ||
                tryQualcommSpecificInjection(audioData) ||
                tryDeviceSpecificTinyMix(audioData);
    }

    private boolean enableInjectionControls() {
        Log.d(TAG, "🔧 Enabling TinyMix injection controls...");

        // Discover available mixer controls first
        try {
            Process discoverProcess = Runtime.getRuntime().exec("su -c tinymix");
            discoverProcess.waitFor();
        } catch (Exception e) {
            Log.w(TAG, "Failed to discover mixer controls: " + e.getMessage());
        }

        String[] enableCommands = {
                // Common injection enable controls
                "su -c 'tinymix \"Voice Call Injection Enable\" 1'",
                "su -c 'tinymix \"Call_RX Audio Mixer Voice Stub Tx\" 1'",
                "su -c 'tinymix \"Voice_Tx Mixer Voice Stub_Tx\" 1'",
                "su -c 'tinymix \"SLIM_0_TX Voice Mixer Voice Stub\" 1'",

                // Alternative control names
                "su -c 'tinymix \"Call Injection Switch\" On'",
                "su -c 'tinymix \"Voice Call Path\" injection'",
                "su -c 'tinymix \"MSM_DL1 Audio Mixer Voice Stub Tx\" 1'",

                // Volume controls
                "su -c 'tinymix \"Call Mic Volume\" 100'",
                "su -c 'tinymix \"Voice Call Volume\" 80'"
        };

        return CommandExecutor.executeAnySuccessful(enableCommands);
    }

    private boolean tryStandardTinyMixInjection(byte[] audioData) {
        Log.d(TAG, "🔧 Trying standard TinyMix injection...");

        String tempFile = "/data/local/tmp/tinymix_inject.pcm";

        String[] commands = {
                // Write audio data to file
                "su -c 'echo -n \"" + Base64.getEncoder().encodeToString(audioData) +
                        "\" | base64 -d > " + tempFile + "'",

                // Enable call injection path
                "su -c 'tinymix \"Voice_Tx Mixer Voice_Stub_Tx\" 1'",
                "su -c 'tinymix \"Call_RX Audio Mixer Voice Stub Tx\" 1'",

                // Play through injection path
                "su -c 'cat " + tempFile + " > /dev/snd/pcmC0D0p'",
                "su -c 'aplay -D hw:0,0 -f S16_LE -r 16000 -c 1 " + tempFile + "'",

                // Alternative playback methods
                "su -c 'cat " + tempFile + " > /sys/kernel/debug/voice_call/inject_pcm'",

                // Cleanup
                "su -c 'rm " + tempFile + "'"
        };

        return CommandExecutor.executeSequential(commands);
    }

    private boolean tryQualcommSpecificInjection(byte[] audioData) {
        Log.d(TAG, "🔧 Trying Qualcomm-specific injection...");

        String[] commands = {
                // Qualcomm-specific mixer controls
                "su -c 'tinymix \"MSM_DL1 Audio Mixer Voice Stub Tx\" 1'",
                "su -c 'tinymix \"Voice_Tx Mixer Voice_Stub_Tx\" 1'",
                "su -c 'tinymix \"SLIMBUS_0_TX Voice Mixer Voice Stub\" 1'",

                // Alternative Qualcomm controls
                "su -c 'tinymix \"QUAT_MI2S_TX Voice Mixer Voice Stub\" 1'",
                "su -c 'tinymix \"SEC_MI2S_TX Voice Mixer Voice Stub\" 1'",

                // Write to Qualcomm injection point
                "su -c 'echo \"" + Base64.getEncoder().encodeToString(audioData) +
                        "\" | base64 -d > /data/vendor/audio/qcom_inject.pcm'"
        };

        boolean controlsEnabled = CommandExecutor.executeAnySuccessful(commands);

        if (controlsEnabled) {
            // Now try to play the audio through the configured path
            return playThroughInjectionPath(audioData);
        }

        return false;
    }

    private boolean tryDeviceSpecificTinyMix(byte[] audioData) {
        String manufacturer = Build.MANUFACTURER.toLowerCase();

        Log.d(TAG, "🔧 Trying device-specific TinyMix for: " + manufacturer);

        if (manufacturer.contains("samsung")) {
            return trySamsungTinyMix(audioData);
        } else if (manufacturer.contains("xiaomi")) {
            return tryXiaomiTinyMix(audioData);
        } else if (manufacturer.contains("oneplus")) {
            return tryOnePlusTinyMix(audioData);
        } else if (manufacturer.contains("oppo")) {
            return tryOppoTinyMix(audioData);
        }

        return false;
    }

    private boolean trySamsungTinyMix(byte[] audioData) {
        Log.d(TAG, "📱 Samsung TinyMix injection...");

        String[] commands = {
                "su -c 'tinymix \"Call Injection Switch\" On'",
                "su -c 'tinymix \"VOICE_CALL RX Voice Mixer Voice Stub Tx\" 1'",
                "su -c 'tinymix \"Samsung Call Path\" injection'",

                // Write to Samsung-specific path
                "su -c 'echo \"" + Base64.getEncoder().encodeToString(audioData) +
                        "\" | base64 -d > /sys/kernel/debug/voice_call/inject_pcm'"
        };

        return CommandExecutor.executeSequential(commands);
    }

    private boolean tryXiaomiTinyMix(byte[] audioData) {
        Log.d(TAG, "📱 Xiaomi TinyMix injection...");

        String[] commands = {
                "su -c 'tinymix \"MIUI Call Injection\" 1'",
                "su -c 'tinymix \"Voice_Tx Mixer Voice_Stub_Tx\" 1'",

                // Xiaomi-specific properties
                "su -c 'setprop vendor.audio.miui.call_inject 1'",

                // Write to Xiaomi path
                "su -c 'echo \"" + Base64.getEncoder().encodeToString(audioData) +
                        "\" | base64 -d > /data/vendor/audio/miui_call_inject.pcm'"
        };

        return CommandExecutor.executeSequential(commands);
    }

    private boolean tryOnePlusTinyMix(byte[] audioData) {
        Log.d(TAG, "📱 OnePlus TinyMix injection...");

        String[] commands = {
                "su -c 'tinymix \"Voice_Tx Mixer Voice_Stub_Tx\" 1'",
                "su -c 'tinymix \"OnePlus Call Mode\" injection'",
                "su -c 'setprop vendor.audio.oneplus.inject 1'"
        };

        return CommandExecutor.executeSequential(commands);
    }

    private boolean tryOppoTinyMix(byte[] audioData) {
        Log.d(TAG, "📱 Oppo TinyMix injection...");

        String[] commands = {
                "su -c 'tinymix \"OPPO Call Injection\" On'",
                "su -c 'tinymix \"Voice_Tx Mixer Voice_Stub_Tx\" 1'",
                "su -c 'setprop vendor.audio.oppo.inject true'"
        };

        return CommandExecutor.executeSequential(commands);
    }

    private boolean playThroughInjectionPath(byte[] audioData) {
        Log.d(TAG, "🔊 Playing through configured injection path...");

        String tempFile = "/data/local/tmp/injection_play.pcm";

        String[] commands = {
                // Write PCM data
                "su -c 'echo -n \"" + Base64.getEncoder().encodeToString(audioData) +
                        "\" | base64 -d > " + tempFile + "'",

                // Play through injection-configured path
                "su -c 'cat " + tempFile + " > /dev/snd/controlC0'",
                "su -c 'aplay -D plughw:0,0 -f S16_LE -r 16000 -c 1 " + tempFile + "'",

                // Alternative: Direct to audio node
                "su -c 'cat " + tempFile + " > /sys/kernel/debug/asoc/VOICE_CALL/dapm_pop_time'",

                // Cleanup
                "su -c 'rm " + tempFile + "'"
        };

        return CommandExecutor.executeFirstSuccessful(commands);
    }
}