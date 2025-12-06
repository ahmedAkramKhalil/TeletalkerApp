package com.teletalker.app.services.ai;

import android.util.Log;

// Enhanced audio processing with adaptive silence detection
public class EnhancedAudioProcessor {
    private static final String TAG = "EnhancedAudioProcessor";

    // Optimized audio configuration
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNELS = 1;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int CHUNK_SIZE_MS = 50; // 50ms chunks for better responsiveness
    private static final int BYTES_PER_CHUNK = (SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8) * CHUNK_SIZE_MS / 1000;

    // Silence detection parameters
    private static final int SILENCE_WINDOW_SIZE = 10; // Number of chunks to analyze
    private static final double SILENCE_THRESHOLD_MULTIPLIER = 2.5;
    private static final long MIN_SPEECH_DURATION_MS = 300;
    private static final long MIN_SILENCE_DURATION_MS = 800;
    private static final long MAX_SILENCE_DURATION_MS = 2000;

    private final AdaptiveSilenceDetector silenceDetector;
    private final CircularBuffer<AudioChunk> audioBuffer;
    private final VoiceActivityDetector voiceDetector;
    private final AudioQualityAnalyzer qualityAnalyzer;

    // State tracking
    private long speechStartTime = 0;
    private long lastSpeechTime = 0;
    private boolean inSpeechSegment = false;
    private int consecutiveSilentChunks = 0;
    private int consecutiveVoiceChunks = 0;

    public interface AudioProcessorCallback {
        void onSpeechStarted();
        void onSpeechEnded(long durationMs);
        void onSilenceDetected(long durationMs);
        void onAudioQualityChanged(AudioQuality quality);
        void onChunkProcessed(AudioChunk chunk, boolean hasVoice);
    }

    private AudioProcessorCallback callback;

    public EnhancedAudioProcessor() {
        this.silenceDetector = new AdaptiveSilenceDetector();
        this.audioBuffer = new CircularBuffer<>(SILENCE_WINDOW_SIZE);
        this.voiceDetector = new VoiceActivityDetector();
        this.qualityAnalyzer = new AudioQualityAnalyzer();
    }

    public void setCallback(AudioProcessorCallback callback) {
        this.callback = callback;
    }

    public ProcessingResult processAudioChunk(byte[] rawAudio) {
        AudioChunk chunk = new AudioChunk(rawAudio, System.currentTimeMillis());

        // Analyze chunk
        chunk.amplitude = calculateRMSAmplitude(rawAudio);
        chunk.maxAmplitude = calculateMaxAmplitude(rawAudio);
        chunk.spectralCentroid = calculateSpectralCentroid(rawAudio);
        chunk.zeroCrossingRate = calculateZeroCrossingRate(rawAudio);

        // Add to buffer
        audioBuffer.add(chunk);

        // Update silence detector
        silenceDetector.updateNoiseModel(chunk);

        // Voice activity detection
        boolean hasVoice = voiceDetector.detectVoice(chunk, silenceDetector.getThreshold());
        chunk.hasVoice = hasVoice;

        // Update speech state
        ProcessingResult result = updateSpeechState(chunk, hasVoice);

        // Quality analysis
        AudioQuality quality = qualityAnalyzer.analyzeQuality(chunk);

        // Notify callback
        if (callback != null) {
            callback.onChunkProcessed(chunk, hasVoice);

            if (quality.changed) {
                callback.onAudioQualityChanged(quality);
            }
        }

        return result;
    }

    private ProcessingResult updateSpeechState(AudioChunk chunk, boolean hasVoice) {
        long currentTime = chunk.timestamp;

        if (hasVoice) {
            consecutiveVoiceChunks++;
            consecutiveSilentChunks = 0;

            if (!inSpeechSegment) {
                // Start of speech
                if (consecutiveVoiceChunks >= 2) { // Need 2 consecutive voice chunks
                    inSpeechSegment = true;
                    speechStartTime = currentTime;
                    lastSpeechTime = currentTime;

                    Log.d(TAG, "🎤 Speech started");
                    if (callback != null) {
                        callback.onSpeechStarted();
                    }

                    return ProcessingResult.SPEECH_STARTED;
                }
            } else {
                // Continuing speech
                lastSpeechTime = currentTime;
                return ProcessingResult.SPEECH_CONTINUING;
            }
        } else {
            consecutiveSilentChunks++;
            consecutiveVoiceChunks = 0;

            if (inSpeechSegment) {
                long silenceDuration = currentTime - lastSpeechTime;
                long speechDuration = lastSpeechTime - speechStartTime;

                // End speech if silence is long enough and speech was substantial
                if (silenceDuration >= MIN_SILENCE_DURATION_MS &&
                        speechDuration >= MIN_SPEECH_DURATION_MS &&
                        consecutiveSilentChunks >= 3) {

                    inSpeechSegment = false;

                    Log.d(TAG, "🔇 Speech ended - Duration: " + speechDuration + "ms, Silence: " + silenceDuration + "ms");
                    if (callback != null) {
                        callback.onSpeechEnded(speechDuration);
                    }

                    return ProcessingResult.SPEECH_ENDED;
                }

                // Force end if silence is too long
                if (silenceDuration >= MAX_SILENCE_DURATION_MS) {
                    inSpeechSegment = false;

                    Log.d(TAG, "⏰ Speech force-ended - Max silence exceeded");
                    if (callback != null) {
                        callback.onSpeechEnded(speechDuration);
                    }

                    return ProcessingResult.SPEECH_ENDED;
                }
            } else {
                // Pure silence
                if (callback != null && consecutiveSilentChunks % 20 == 0) { // Every second
                    callback.onSilenceDetected(consecutiveSilentChunks * CHUNK_SIZE_MS);
                }
            }
        }

        return ProcessingResult.CONTINUE;
    }

    // Audio analysis methods
    private int calculateRMSAmplitude(byte[] audioData) {
        long sum = 0;
        int samples = audioData.length / 2;

        for (int i = 0; i < audioData.length - 1; i += 2) {
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            sum += sample * sample;
        }

        return samples > 0 ? (int) Math.sqrt(sum / samples) : 0;
    }

    private int calculateMaxAmplitude(byte[] audioData) {
        int maxAmp = 0;

        for (int i = 0; i < audioData.length - 1; i += 2) {
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            int amplitude = Math.abs(sample);
            if (amplitude > maxAmp) {
                maxAmp = amplitude;
            }
        }

        return maxAmp;
    }

    private double calculateSpectralCentroid(byte[] audioData) {
        // Simplified spectral centroid calculation
        double weightedSum = 0;
        double magnitudeSum = 0;

        for (int i = 0; i < audioData.length - 1; i += 2) {
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            double magnitude = Math.abs(sample);
            weightedSum += magnitude * i;
            magnitudeSum += magnitude;
        }

        return magnitudeSum > 0 ? weightedSum / magnitudeSum : 0;
    }

    private double calculateZeroCrossingRate(byte[] audioData) {
        int zeroCrossings = 0;
        short prevSample = 0;

        for (int i = 0; i < audioData.length - 1; i += 2) {
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));

            if (i > 0 && ((prevSample >= 0 && sample < 0) || (prevSample < 0 && sample >= 0))) {
                zeroCrossings++;
            }

            prevSample = sample;
        }

        return (double) zeroCrossings / (audioData.length / 2);
    }

    // Supporting classes
    public static class AudioChunk {
        public final byte[] data;
        public final long timestamp;
        public int amplitude;
        public int maxAmplitude;
        public double spectralCentroid;
        public double zeroCrossingRate;
        public boolean hasVoice;

        public AudioChunk(byte[] data, long timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }
    }

    public static class AdaptiveSilenceDetector {
        private static final int NOISE_HISTORY_SIZE = 50;
        private final CircularBuffer<Integer> noiseHistory = new CircularBuffer<>(NOISE_HISTORY_SIZE);
        private int backgroundNoise = 200;
        private final double adaptationRate = 0.03;

        public void updateNoiseModel(AudioChunk chunk) {
            if (!chunk.hasVoice) {
                noiseHistory.add(chunk.amplitude);

                // Update background noise level
                double avgNoise = noiseHistory.getAverage();
                backgroundNoise = (int) (backgroundNoise * (1 - adaptationRate) + avgNoise * adaptationRate);
            }
        }

        public int getThreshold() {
            return (int) (backgroundNoise * SILENCE_THRESHOLD_MULTIPLIER);
        }

        public int getBackgroundNoise() {
            return backgroundNoise;
        }
    }

    public static class VoiceActivityDetector {
        public boolean detectVoice(AudioChunk chunk, int threshold) {
            // Multi-factor voice detection
            boolean amplitudeCheck = chunk.amplitude > threshold;
            boolean maxAmplitudeCheck = chunk.maxAmplitude > threshold * 1.5;
            boolean spectralCheck = chunk.spectralCentroid > 100; // Basic spectral activity
            boolean zcrCheck = chunk.zeroCrossingRate > 0.01 && chunk.zeroCrossingRate < 0.5;

            // Voice if multiple indicators are positive
            int positiveIndicators = 0;
            if (amplitudeCheck) positiveIndicators++;
            if (maxAmplitudeCheck) positiveIndicators++;
            if (spectralCheck) positiveIndicators++;
            if (zcrCheck) positiveIndicators++;

            return positiveIndicators >= 2;
        }
    }

    public static class AudioQuality {
        public final QualityLevel level;
        public final String reason;
        public final boolean changed;

        public AudioQuality(QualityLevel level, String reason, boolean changed) {
            this.level = level;
            this.reason = reason;
            this.changed = changed;
        }

        public enum QualityLevel {
            EXCELLENT, GOOD, FAIR, POOR, VERY_POOR
        }
    }

    public static class AudioQualityAnalyzer {
        private AudioQuality.QualityLevel lastQuality = AudioQuality.QualityLevel.GOOD;

        public AudioQuality analyzeQuality(AudioChunk chunk) {
            AudioQuality.QualityLevel quality;
            String reason;

            if (chunk.maxAmplitude < 50) {
                quality = AudioQuality.QualityLevel.VERY_POOR;
                reason = "Very low audio level";
            } else if (chunk.maxAmplitude > 30000) {
                quality = AudioQuality.QualityLevel.POOR;
                reason = "Audio clipping detected";
            } else if (chunk.amplitude < 100) {
                quality = AudioQuality.QualityLevel.POOR;
                reason = "Low audio energy";
            } else if (chunk.zeroCrossingRate > 0.8) {
                quality = AudioQuality.QualityLevel.FAIR;
                reason = "High noise level";
            } else if (chunk.amplitude > 1000 && chunk.zeroCrossingRate < 0.3) {
                quality = AudioQuality.QualityLevel.EXCELLENT;
                reason = "Clear audio signal";
            } else {
                quality = AudioQuality.QualityLevel.GOOD;
                reason = "Normal audio quality";
            }

            boolean changed = quality != lastQuality;
            lastQuality = quality;

            return new AudioQuality(quality, reason, changed);
        }
    }

    public enum ProcessingResult {
        SPEECH_STARTED,
        SPEECH_CONTINUING,
        SPEECH_ENDED,
        SILENCE,
        CONTINUE
    }

    // Utility classes
    public static class CircularBuffer<T> {
        private final T[] buffer;
        private final int capacity;
        private int index = 0;
        private int size = 0;

        @SuppressWarnings("unchecked")
        public CircularBuffer(int capacity) {
            this.capacity = capacity;
            this.buffer = (T[]) new Object[capacity];
        }

        public void add(T item) {
            buffer[index] = item;
            index = (index + 1) % capacity;
            if (size < capacity) size++;
        }

        public double getAverage() {
            if (size == 0) return 0;

            double sum = 0;
            for (int i = 0; i < size; i++) {
                if (buffer[i] instanceof Integer) {
                    sum += (Integer) buffer[i];
                }
            }
            return sum / size;
        }

        public int getSize() { return size; }
    }

    // Status and debugging
    public String getProcessingStatus() {
        return String.format("Speech: %s, Threshold: %d, BG Noise: %d, Voice Chunks: %d, Silent Chunks: %d",
                inSpeechSegment ? "ACTIVE" : "IDLE",
                silenceDetector.getThreshold(),
                silenceDetector.getBackgroundNoise(),
                consecutiveVoiceChunks,
                consecutiveSilentChunks);
    }

    public void reset() {
        inSpeechSegment = false;
        speechStartTime = 0;
        lastSpeechTime = 0;
        consecutiveSilentChunks = 0;
        consecutiveVoiceChunks = 0;
//        audioBuffer.clear();
    }

    public boolean isInSpeech() { return inSpeechSegment; }
    public int getCurrentThreshold() { return silenceDetector.getThreshold(); }
    public int getBackgroundNoise() { return silenceDetector.getBackgroundNoise(); }
}