package com.teletalker.app.services.callinjection;


import android.content.Context;
import android.os.Build;
import android.util.Log;

/**
 * Main coordinator for call audio injection
 * Manages different injection strategies based on device capabilities
 */
public class CallInjectionManager {
    private static final String TAG = "CallInjectionManager";

    private Context context;
    private boolean isRooted;
    private DeviceCapabilityDetector detector;
    private TinyMixInjector tinyMixInjector;
    private NonTinyMixInjector nonTinyMixInjector;
    private FallbackInjector fallbackInjector;

    public CallInjectionManager(Context context, boolean isRooted) {
        this.context = context;
        this.isRooted = isRooted;

        // Initialize components
        this.detector = new DeviceCapabilityDetector();
        this.tinyMixInjector = new TinyMixInjector();
        this.nonTinyMixInjector = new NonTinyMixInjector();
        this.fallbackInjector = new FallbackInjector(context);

        // Detect device capabilities
        detectDeviceCapabilities();
    }

    /**
     * Main method to inject audio into call stream
     */
    public boolean injectAudioIntoCall(byte[] audioData) {
        if (!isRooted) {
            Log.w(TAG, "⚠️ Root access required for call injection");
            return false;
        }

        if (audioData == null || audioData.length == 0) {
            Log.w(TAG, "⚠️ No audio data to inject");
            return false;
        }

        Log.d(TAG, "🎯 Starting call injection: " + audioData.length + " bytes");

        // Try injection methods in order of success probability
        return tryTinyMixInjection(audioData) ||
                tryNonTinyMixInjection(audioData) ||
                tryFallbackInjection(audioData);
    }

    /**
     * Test injection capabilities with a test tone
     */
    public boolean testInjectionCapability() {
        if (!isRooted) {
            Log.w(TAG, "⚠️ Root required for injection testing");
            return false;
        }

        // Generate 1-second test tone at 1kHz
        byte[] testTone = AudioTestUtils.generateTestTone(1000, 1000, 16000);

        Log.d(TAG, "🧪 Testing injection with " + testTone.length + " byte test tone");

        boolean success = injectAudioIntoCall(testTone);
        Log.d(TAG, "🧪 Injection test result: " + (success ? "SUCCESS" : "FAILED"));

        return success;
    }

    /**
     * Get device capabilities information
     */
    public String getDeviceCapabilities() {
        return detector.getDeviceInfo();
    }

    private void detectDeviceCapabilities() {
        Log.d(TAG, "📱 Detecting device capabilities...");
        String capabilities = detector.detectCapabilities();
        Log.d(TAG, "📋 Device Capabilities:\n" + capabilities);
    }

    private boolean tryTinyMixInjection(byte[] audioData) {
        if (!detector.hasTinyMixSupport()) {
            Log.d(TAG, "⚠️ TinyMix not available, skipping");
            return false;
        }

        Log.d(TAG, "🎛️ Trying TinyMix injection...");
        return tinyMixInjector.inject(audioData);
    }

    private boolean tryNonTinyMixInjection(byte[] audioData) {
        Log.d(TAG, "🔧 Trying non-TinyMix injection...");
        return nonTinyMixInjector.inject(audioData, detector.getDeviceType());
    }

    private boolean tryFallbackInjection(byte[] audioData) {
        Log.d(TAG, "🔄 Trying fallback injection methods...");
        return fallbackInjector.inject(audioData);
    }
}