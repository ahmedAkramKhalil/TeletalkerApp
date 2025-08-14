package com.teletalker.app.services.callinjection;


import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;
import java.util.Base64;

/**
 * Utility class for executing root commands
 */
class CommandExecutor {
    private static final String TAG = "CommandExecutor";

    /**
     * Execute commands until first success
     */
    public static boolean executeFirstSuccessful(String[] commands) {
        for (String command : commands) {
            try {
                Process process = Runtime.getRuntime().exec(command);
                int result = process.waitFor();

                if (result == 0) {
                    Log.d(TAG, "✅ Success: " + command);
                    return true;
                } else {
                    Log.v(TAG, "⚠️ Failed: " + command + " (exit: " + result + ")");
                }

            } catch (Exception e) {
                Log.v(TAG, "⚠️ Error: " + command + " -> " + e.getMessage());
            }
        }
        return false;
    }

    /**
     * Execute all commands sequentially, return true if any succeed
     */
    public static boolean executeSequential(String[] commands) {
        boolean anySuccess = false;

        for (String command : commands) {
            try {
                Process process = Runtime.getRuntime().exec(command);
                int result = process.waitFor();

                if (result == 0) {
                    Log.d(TAG, "✅ Success: " + command);
                    anySuccess = true;
                } else {
                    Log.v(TAG, "⚠️ Failed: " + command + " (exit: " + result + ")");
                }

            } catch (Exception e) {
                Log.v(TAG, "⚠️ Error: " + command + " -> " + e.getMessage());
            }
        }

        return anySuccess;
    }

    /**
     * Execute commands, return true if any succeed (alias for sequential)
     */
    public static boolean executeAnySuccessful(String[] commands) {
        return executeSequential(commands);
    }

    /**
     * Execute single command
     */
    public static boolean executeSingle(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            int result = process.waitFor();

            boolean success = result == 0;
            Log.d(TAG, (success ? "✅" : "❌") + " Command: " + command + " -> " + result);

            return success;

        } catch (Exception e) {
            Log.e(TAG, "❌ Command error: " + command + " -> " + e.getMessage());
            return false;
        }
    }
}

/**
 * Fallback injection methods using Android AudioTrack
 */
class FallbackInjector {
    private static final String TAG = "FallbackInjector";
    private Context context;

    public FallbackInjector(Context context) {
        this.context = context;
    }

    /**
     * Fallback injection using enhanced AudioTrack methods
     */
    public boolean inject(byte[] audioData) {
        Log.d(TAG, "🔄 Starting fallback injection methods...");

        return tryHiddenAPIInjection(audioData) ||
                tryReflectionInjection(audioData) ||
                tryEnhancedAudioTrack(audioData);
    }

    private boolean tryHiddenAPIInjection(byte[] audioData) {
        try {
            Log.d(TAG, "🔧 Trying hidden API injection...");

            // Access AudioSystem hidden methods
            Class<?> audioSystemClass = Class.forName("android.media.AudioSystem");

            // Enable call injection via hidden parameters
            java.lang.reflect.Method setParametersMethod = audioSystemClass.getMethod("setParameters", String.class);
            setParametersMethod.invoke(null, "call_injection=true;inject_pcm=enable");

            // Get active call session ID
            java.lang.reflect.Method getSessionMethod = audioSystemClass.getMethod("getAudioSessionId", int.class);
            int callSessionId = (Integer) getSessionMethod.invoke(null, AudioManager.STREAM_VOICE_CALL);

            Log.d(TAG, "📞 Call session ID: " + callSessionId);

            // Create injection AudioTrack with call session
            AudioTrack injectionTrack = createCallInjectionTrack(callSessionId);

            if (injectionTrack != null) {
                injectionTrack.play();
                int written = injectionTrack.write(audioData, 0, audioData.length);
                injectionTrack.stop();
                injectionTrack.release();

                Log.d(TAG, "📡 Injected " + written + " bytes via hidden API");
                return written > 0;
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Hidden API injection failed: " + e.getMessage());
        }

        return false;
    }

    private boolean tryReflectionInjection(byte[] audioData) {
        try {
            Log.d(TAG, "🔧 Trying reflection injection...");

            // This is a placeholder for reflection-based methods
            // Actual implementation would depend on specific Android version
            // and device manufacturer customizations

            return false;

        } catch (Exception e) {
            Log.e(TAG, "❌ Reflection injection failed: " + e.getMessage());
            return false;
        }
    }

    private boolean tryEnhancedAudioTrack(byte[] audioData) {
        try {
            Log.d(TAG, "🔧 Trying enhanced AudioTrack injection...");

            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

            // Save original mode
            int originalMode = audioManager.getMode();

            // Set communication mode
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

            // Create AudioTrack with communication attributes
            AudioTrack track = createEnhancedAudioTrack();

            if (track != null) {
                track.play();
                int written = track.write(audioData, 0, audioData.length);

                // Wait for playback
                Thread.sleep(audioData.length / 32); // Rough duration calculation

                track.stop();
                track.release();

                // Restore original mode
                audioManager.setMode(originalMode);

                Log.d(TAG, "📡 Enhanced AudioTrack wrote " + written + " bytes");
                return written > 0;
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Enhanced AudioTrack injection failed: " + e.getMessage());
        }

        return false;
    }

    private AudioTrack createCallInjectionTrack(int sessionId) {
        try {
            int bufferSize = AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                android.media.AudioAttributes attributes = new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setFlags(android.media.AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build();

                return new AudioTrack(
                        attributes,
                        new android.media.AudioFormat.Builder()
                                .setSampleRate(16000)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .build(),
                        bufferSize,
                        AudioTrack.MODE_STREAM,
                        sessionId
                );
            } else {
                return new AudioTrack(
                        AudioManager.STREAM_VOICE_CALL,
                        16000,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STREAM,
                        sessionId
                );
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to create call injection track: " + e.getMessage());
            return null;
        }
    }

    private AudioTrack createEnhancedAudioTrack() {
        try {
            int bufferSize = AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                android.media.AudioAttributes attributes = new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setFlags(android.media.AudioAttributes.FLAG_LOW_LATENCY)
                        .build();

                return new AudioTrack(
                        attributes,
                        new android.media.AudioFormat.Builder()
                                .setSampleRate(16000)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .build(),
                        bufferSize,
                        AudioTrack.MODE_STREAM,
                        AudioManager.AUDIO_SESSION_ID_GENERATE
                );
            } else {
                return new AudioTrack(
                        AudioManager.STREAM_VOICE_CALL,
                        16000,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STREAM
                );
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to create enhanced AudioTrack: " + e.getMessage());
            return null;
        }
    }
}

/**
 * Utility class for generating test audio
 */
class AudioTestUtils {
    private static final String TAG = "AudioTestUtils";

    /**
     * Generate a test tone for injection testing
     * @param frequency Frequency in Hz
     * @param durationMs Duration in milliseconds
     * @param sampleRate Sample rate in Hz
     * @return PCM audio data as byte array
     */
    public static byte[] generateTestTone(int frequency, int durationMs, int sampleRate) {
        int numSamples = (sampleRate * durationMs) / 1000;
        byte[] samples = new byte[numSamples * 2]; // 16-bit samples = 2 bytes per sample

        Log.d(TAG, "🎵 Generating test tone: " + frequency + "Hz, " + durationMs + "ms, " +
                numSamples + " samples");

        for (int i = 0; i < numSamples; i++) {
            // Generate sine wave
            double sample = Math.sin(2 * Math.PI * i * frequency / sampleRate);

            // Apply envelope to avoid clicks (fade in/out)
            double envelope = 1.0;
            int fadeLength = sampleRate / 20; // 50ms fade

            if (i < fadeLength) {
                envelope = (double) i / fadeLength;
            } else if (i > numSamples - fadeLength) {
                envelope = (double) (numSamples - i) / fadeLength;
            }

            // Convert to 16-bit signed integer with 30% volume
            short shortSample = (short) (sample * envelope * Short.MAX_VALUE * 0.3);

            // Convert to little-endian bytes
            samples[i * 2] = (byte) (shortSample & 0xFF);
            samples[i * 2 + 1] = (byte) ((shortSample >> 8) & 0xFF);
        }

        Log.d(TAG, "✅ Generated " + samples.length + " bytes of test audio");
        return samples;
    }

    /**
     * Generate a multi-tone test signal (more complex for testing)
     */
    public static byte[] generateMultiTone(int[] frequencies, int durationMs, int sampleRate) {
        int numSamples = (sampleRate * durationMs) / 1000;
        byte[] samples = new byte[numSamples * 2];

        Log.d(TAG, "🎵 Generating multi-tone: " + frequencies.length + " frequencies");

        for (int i = 0; i < numSamples; i++) {
            double sample = 0.0;

            // Sum multiple frequencies
            for (int freq : frequencies) {
                sample += Math.sin(2 * Math.PI * i * freq / sampleRate) / frequencies.length;
            }

            // Apply envelope
            double envelope = 1.0;
            int fadeLength = sampleRate / 20;

            if (i < fadeLength) {
                envelope = (double) i / fadeLength;
            } else if (i > numSamples - fadeLength) {
                envelope = (double) (numSamples - i) / fadeLength;
            }

            // Convert to 16-bit with reduced volume
            short shortSample = (short) (sample * envelope * Short.MAX_VALUE * 0.2);

            samples[i * 2] = (byte) (shortSample & 0xFF);
            samples[i * 2 + 1] = (byte) ((shortSample >> 8) & 0xFF);
        }

        return samples;
    }

    /**
     * Generate voice-like test audio (more realistic for testing)
     */
    public static byte[] generateVoiceTest(int durationMs, int sampleRate) {
        // Generate a voice-like signal with multiple harmonics
        int[] voiceFrequencies = {200, 400, 600, 800, 1000}; // Typical voice harmonics
        return generateMultiTone(voiceFrequencies, durationMs, sampleRate);
    }
}