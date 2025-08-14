package com.teletalker.app.services.injection;


public interface AudioInjector {

    enum InjectionMethod {
        MEDIA_PLAYER("MediaPlayer with STREAM_VOICE_CALL"),
        AUDIO_TRACK("AudioTrack with telephony routing"),
        NATIVE_HAL("Native HAL injection"),
        XPOSED_HOOK("Xposed framework hook"),
        ACCESSIBILITY("Accessibility service fallback");

        private final String description;

        InjectionMethod(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    interface InjectionCallback {
        void onInjectionStarted(InjectionMethod method);
        void onInjectionProgress(int percentage);
        void onInjectionCompleted();
        void onInjectionFailed(String reason);
    }

    /**
     * Test if this injection method is available on the device
     */
    boolean isAvailable();

    /**
     * Get the priority of this method (higher = better)
     */
    int getPriority();

    /**
     * Get the injection method type
     */
    InjectionMethod getMethod();

    /**
     * Inject audio into the call
     */
    boolean inject(String audioPath, InjectionCallback callback);

    /**
     * Stop the injection
     */
    void stop();

    /**
     * Clean up resources
     */
    void cleanup();
}