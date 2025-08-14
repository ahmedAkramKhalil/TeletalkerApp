package com.teletalker.app.services.injection;

import static com.teletalker.app.services.injection.BaseAudioInjector.TELEPHONY_SAMPLE_RATE;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.teletalker.app.services.CallRecorder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioInjectionManager {
    private static final String TAG = "AudioInjectionManager";

    private final Context context;
    private final List<AudioInjector> availableInjectors;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    private AudioInjector currentInjector;
    private AudioInjector.InjectionCallback userCallback;

    private List<TestResult> injectionResults; // To store results of all injection attempts

    public AudioInjectionManager(Context context) {
        this.context = context;
        this.availableInjectors = new ArrayList<>();
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.injectionResults = new ArrayList<>();

        // Initialize injectors
        initializeInjectors();
    }

    private void initializeInjectors() {
        // Add all available injectors
        List<AudioInjector> allInjectors = new ArrayList<>();
        allInjectors.add(new MediaPlayerInjector(context));
        allInjectors.add(new AudioTrackInjector(context));
        allInjectors.add(new NativeHALInjector(context));
        // Add more injectors as needed

        // Test and add only available ones
        for (AudioInjector injector : allInjectors) {
            if (injector.isAvailable()) {
                availableInjectors.add(injector);
                Log.d(TAG, "Available: " + injector.getMethod().getDescription());
            }
        }

        // Sort by priority
        Collections.sort(availableInjectors,
                (a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
    }

    /**
     * Test all injection methods and return results
     */
    public void testInjectionMethods(TestCallback callback) {
        executorService.execute(() -> {
            List<TestResult> results = new ArrayList<>();

            for (AudioInjector injector : availableInjectors) {
                TestResult result = new TestResult();
                result.method = injector.getMethod();
                result.available = injector.isAvailable();
                result.priority = injector.getPriority();

                // Quick test with small audio
                try {
                    // You can create a small test audio in assets
                    boolean success = testInjector(injector);
                    result.tested = true;
                    result.working = success;
                } catch (Exception e) {
                    result.tested = true;
                    result.working = false;
                    result.error = e.getMessage();
                }

                results.add(result);
            }

            mainHandler.post(() -> callback.onTestComplete(results));
        });
    }

    private boolean testInjector(AudioInjector injector) {
        // Implement quick test logic
        // For now, just return availability
        return injector.isAvailable();
    }

    /**
     * Inject audio using the best available method
     */
    public boolean injectAudio(String audioPath, AudioInjector.InjectionCallback callback) {
        if (availableInjectors.isEmpty()) {
            Log.e(TAG, "No injection methods available");
            if (callback != null) {
                callback.onInjectionFailed("No injection methods available");
            }
            return false;
        }

        this.userCallback = callback;

        // Try injectors in order of priority
        return tryNextInjector(audioPath, 0);
    }

    public void stopInjection() {
        if (injecting) {
            injecting = false;

            if (injectionThread != null) {
                injectionThread.interrupt();
                try {
                    injectionThread.join(500);
                } catch (InterruptedException ignored) {}
                injectionThread = null;
            }

            if (injectionTrack != null) {
                try {
                    injectionTrack.stop();
                    injectionTrack.release();
                } catch (Exception ignored) {}
                injectionTrack = null;
            }

            Log.d(TAG, "🛑 Audio injection stopped");
        }
    }


    private Thread injectionThread;
    private volatile boolean injecting = false;
    AudioTrack injectionTrack;
    public boolean injectWithStreamingAudioTrack(String audioFilePath, CallRecorder.InjectionCallback injectionCallback) {
        try {
            Log.d(TAG, "🎵 Streaming AudioTrack injection...");

            File audioFile = new File(audioFilePath);
            FileInputStream fis = new FileInputStream(audioFile);
            byte[] buffer = new byte[2048];

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

            injectionTrack.play();
            injecting = true;

            injectionThread = new Thread(() -> {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                try {
                    int read;
                    while (injecting && (read = fis.read(buffer)) > 0) {
                        injectionTrack.write(buffer, 0, read);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error streaming audio: ", e);
                } finally {
                    try {
                        fis.close();
                    } catch (IOException ignored) {}

                    injectionTrack.stop();
                    injectionTrack.release();
                    injectionTrack = null;

                    injecting = false;
                    injectionCallback.onInjectionCompleted();
                    Log.d(TAG, "✅ Streaming AudioTrack injection finished");
                }
            });
            injectionThread.start();

            injectionCallback.onInjectionStarted(AudioInjector.InjectionMethod.AUDIO_TRACK);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Streaming AudioTrack injection failed", e);
            injectionCallback.onInjectionFailed(e.getMessage());
            return false;
        }
    }

    public void testAllInjectionMethods(String audioPath, AllMethodsCallback callback) {
        executorService.execute(() -> {
            injectionResults.clear();

            for (AudioInjector injector : availableInjectors) {
                TestResult result = new TestResult();
                result.method = injector.getMethod();
                result.available = injector.isAvailable();
                result.priority = injector.getPriority();

                try {
                    // Create a synchronous callback for testing
                    final boolean[] completed = {false};
                    final boolean[] success = {false};
                    final String[] errorMessage = {null};

                    AudioInjector.InjectionCallback testCallback = new AudioInjector.InjectionCallback() {
                        @Override
                        public void onInjectionStarted(AudioInjector.InjectionMethod method) {}

                        @Override
                        public void onInjectionProgress(int percentage) {}

                        @Override
                        public void onInjectionCompleted() {
                            success[0] = true;
                            completed[0] = true;
                        }

                        @Override
                        public void onInjectionFailed(String reason) {
                            success[0] = false;
                            errorMessage[0] = reason;
                            completed[0] = true;
                        }
                    };

                    // Perform the injection
                    boolean started = injector.inject(audioPath, testCallback);
                    if (!started) {
                        result.tested = true;
                        result.working = false;
                        result.error = "Injection failed to start";
                    } else {
                        // Wait for completion (with timeout)
                        long startTime = System.currentTimeMillis();
                        while (!completed[0] && (System.currentTimeMillis() - startTime) < 5000) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }

                        result.tested = true;
                        result.working = success[0];
                        result.error = success[0] ? null :
                                (errorMessage[0] != null ? errorMessage[0] : "Timeout occurred");
                    }
                } catch (Exception e) {
                    result.tested = true;
                    result.working = false;
                    result.error = e.getMessage();
                }

                injectionResults.add(result);

                // Ensure injector is stopped before trying next one
                injector.stop();
            }

            mainHandler.post(() -> callback.onAllMethodsTested(injectionResults));
        });
    }

    public interface AllMethodsCallback {
        void onAllMethodsTested(List<TestResult> results);
    }



    private boolean tryNextInjector(String audioPath, int index) {
        if (index >= availableInjectors.size()) {
            Log.e(TAG, "All injection methods failed");
            if (userCallback != null) {
                userCallback.onInjectionFailed("All injection methods failed");
            }
            return false;
        }

        currentInjector = availableInjectors.get(index);
        Log.d(TAG, "Trying: " + currentInjector.getMethod().getDescription());

        // Create wrapper callback to handle failures
        AudioInjector.InjectionCallback wrapperCallback = new AudioInjector.InjectionCallback() {
            @Override
            public void onInjectionStarted(AudioInjector.InjectionMethod method) {
                if (userCallback != null) {
                    userCallback.onInjectionStarted(method);
                }
            }

            @Override
            public void onInjectionProgress(int percentage) {
                if (userCallback != null) {
                    userCallback.onInjectionProgress(percentage);
                }
            }

            @Override
            public void onInjectionCompleted() {
                if (userCallback != null) {
                    userCallback.onInjectionCompleted();
                }
            }

            @Override
            public void onInjectionFailed(String reason) {
                Log.w(TAG, currentInjector.getMethod() + " failed: " + reason);
                // Try next injector
                tryNextInjector(audioPath, index + 1);
            }
        };

        return currentInjector.inject(audioPath, wrapperCallback);
    }

    /**
     * Stop current injection
     */

    /**
     * Clean up resources
     */
    public void cleanup() {
        stopInjection();

        for (AudioInjector injector : availableInjectors) {
            injector.cleanup();
        }

        executorService.shutdown();
    }

    /**
     * Get list of available injection methods
     */
    public List<AudioInjector.InjectionMethod> getAvailableMethods() {
        List<AudioInjector.InjectionMethod> methods = new ArrayList<>();
        for (AudioInjector injector : availableInjectors) {
            methods.add(injector.getMethod());
        }
        return methods;
    }

    // Test result class
    public static class TestResult {
        public AudioInjector.InjectionMethod method;
        public boolean available;
        public boolean tested;
        public boolean working;
        public int priority;
        public String error;
    }

    // Test callback interface
    public interface TestCallback {
        void onTestComplete(List<TestResult> results);
    }
}