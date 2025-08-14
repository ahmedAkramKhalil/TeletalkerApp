package com.teletalker.app.services.callinjection;


import android.os.Build;
import android.util.Log;

/**
 * Detects device audio capabilities and hardware type
 */
public class DeviceCapabilityDetector {
    private static final String TAG = "DeviceCapabilityDetector";

    public enum DeviceType {
        QUALCOMM_TINYMIX,
        MEDIATEK,
        SAMSUNG_EXYNOS,
        HUAWEI_KIRIN,
        LEGACY_ALSA,
        UNKNOWN
    }

    private DeviceType deviceType;
    private boolean hasTinyMix = false;
    private boolean hasALSA = false;
    private boolean hasPulseAudio = false;

    /**
     * Detect device capabilities
     */
    public String detectCapabilities() {
        StringBuilder info = new StringBuilder();

        try {
            // Basic device info
            String hardware = Build.HARDWARE.toLowerCase();
            String manufacturer = Build.MANUFACTURER.toLowerCase();

            info.append("=== DEVICE INFO ===\n");
            info.append("Hardware: ").append(hardware).append("\n");
            info.append("Manufacturer: ").append(manufacturer).append("\n");
            info.append("Model: ").append(Build.MODEL).append("\n");
            info.append("Android: ").append(Build.VERSION.RELEASE).append("\n");

            // Detect device type
            deviceType = detectDeviceType(hardware, manufacturer);
            info.append("Device Type: ").append(deviceType).append("\n");

            // Check audio capabilities
            info.append("\n=== AUDIO CAPABILITIES ===\n");

            // TinyMix check
            hasTinyMix = checkTinyMixSupport();
            info.append("TinyMix: ").append(hasTinyMix ? "✅ Available" : "❌ Not Available").append("\n");

            // ALSA check
            hasALSA = checkALSASupport();
            info.append("ALSA: ").append(hasALSA ? "✅ Available" : "❌ Not Available").append("\n");

            // PulseAudio check
            hasPulseAudio = checkPulseAudioSupport();
            info.append("PulseAudio: ").append(hasPulseAudio ? "✅ Available" : "❌ Not Available").append("\n");

            // Audio devices check
            boolean hasAudioDevices = checkAudioDevices();
            info.append("Audio Devices: ").append(hasAudioDevices ? "✅ Available" : "❌ Not Available").append("\n");

            Log.d(TAG, "Device capabilities detected:\n" + info.toString());

        } catch (Exception e) {
            Log.e(TAG, "Error detecting capabilities: " + e.getMessage());
            info.append("Detection Error: ").append(e.getMessage());
        }

        return info.toString();
    }

    private DeviceType detectDeviceType(String hardware, String manufacturer) {
        // MediaTek detection
        if (hardware.contains("mt") || hardware.contains("mediatek")) {
            return DeviceType.MEDIATEK;
        }

        // Samsung Exynos detection
        if (hardware.contains("exynos") || manufacturer.contains("samsung")) {
            return DeviceType.SAMSUNG_EXYNOS;
        }

        // Qualcomm detection
        if (hardware.contains("msm") || hardware.contains("sdm") ||
                hardware.contains("sm") || hardware.contains("qcom")) {
            return DeviceType.QUALCOMM_TINYMIX;
        }

        // Huawei Kirin detection
        if (hardware.contains("kirin") || manufacturer.contains("huawei") ||
                manufacturer.contains("honor")) {
            return DeviceType.HUAWEI_KIRIN;
        }

        // Legacy ALSA devices (older Android)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return DeviceType.LEGACY_ALSA;
        }

        return DeviceType.UNKNOWN;
    }

    private boolean checkTinyMixSupport() {
        try {
            Process process = Runtime.getRuntime().exec("su -c 'which tinymix 2>/dev/null'");
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkALSASupport() {
        try {
            Process process = Runtime.getRuntime().exec("su -c 'ls /proc/asound 2>/dev/null'");
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkPulseAudioSupport() {
        try {
            Process process = Runtime.getRuntime().exec("su -c 'which pulseaudio 2>/dev/null'");
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkAudioDevices() {
        try {
            Process process = Runtime.getRuntime().exec("su -c 'ls /dev/snd/ 2>/dev/null'");
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // Getters
    public DeviceType getDeviceType() {
        return deviceType != null ? deviceType : DeviceType.UNKNOWN;
    }

    public boolean hasTinyMixSupport() {
        return hasTinyMix;
    }

    public boolean hasALSASupport() {
        return hasALSA;
    }

    public boolean hasPulseAudioSupport() {
        return hasPulseAudio;
    }

    public String getDeviceInfo() {
        return String.format("Type: %s, TinyMix: %s, ALSA: %s",
                getDeviceType(),
                hasTinyMix ? "Yes" : "No",
                hasALSA ? "Yes" : "No");
    }
}