package com.teletalker.app.services.ai;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.WebSocket;

/**
 * Handles streaming audio chunks to AI WebSocket
 * Separate from core recording to avoid conflicts
 */
public class AIChunkStreamer {
    private static final String TAG = "AIChunkStreamer";

    // AI-specific audio config (different from core recording)
    private static final int AI_SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    public interface StreamingCallback {
        void onStreamingStarted(String audioSource);
        void onStreamingFailed(String reason);
        void onStreamingStopped();
        void onChunkStreamed(int chunkSize, boolean hasRealAudio);
    }

    private final ExecutorService executorService;
    private StreamingCallback callback;

    // Audio streaming components
    private AudioRecord aiAudioRecord;
    private WebSocket webSocket;
    private int aiBufferSize;

    // State tracking
    private final AtomicBoolean isStreaming = new AtomicBoolean(false);
    private final AtomicBoolean isWebSocketConnected = new AtomicBoolean(false);

    // Statistics
    private int totalChunksSent = 0;
    private int chunksWithRealAudio = 0;
    private long lastLogTime = 0;
    private boolean firstAudioMessageLogged = false;

    public AIChunkStreamer() {
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public void setCallback(StreamingCallback callback) {
        this.callback = callback;
    }

    public void setWebSocket(WebSocket webSocket) {
        this.webSocket = webSocket;
        this.isWebSocketConnected.set(webSocket != null);
    }


    private void testStreamingAfterDelay() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.d(TAG, "🔍 STREAMING TEST AFTER 3 SECONDS:");
            Log.d(TAG, "  📊 Streaming active: " + isStreaming.get());
            Log.d(TAG, "  📡 Chunks sent: " + totalChunksSent);
            Log.d(TAG, "  🎵 Real audio chunks: " + chunksWithRealAudio);
            Log.d(TAG, "  🔗 WebSocket connected: " + isWebSocketConnected.get());

            if (totalChunksSent == 0) {
                Log.e(TAG, "❌ NO CHUNKS SENT AFTER 3 SECONDS - STREAMING NOT WORKING!");
            } else if (chunksWithRealAudio == 0) {
                Log.w(TAG, "⚠️ ONLY SILENT CHUNKS SENT - AUDIO CAPTURE NOT WORKING!");
            } else {
                Log.d(TAG, "✅ Audio streaming appears to be working");
            }
        }, 3000);
    }


    private void streamChunkToWebSocket(byte[] audioChunk) {
        if (webSocket == null || !isWebSocketConnected.get()) {
            Log.w(TAG, "⚠️ Cannot stream chunk - WebSocket not available");
            Log.d(TAG, "  WebSocket: " + (webSocket != null ? "available" : "null"));
            Log.d(TAG, "  Connected: " + isWebSocketConnected.get());
            return;
        }

        try {
            String base64Audio = Base64.getEncoder().encodeToString(audioChunk);

            // ElevenLabs WebSocket audio message format
            JSONObject audioMessage = new JSONObject();
            audioMessage.put("type", "user_audio_chunk");
            audioMessage.put("audio_chunk", base64Audio);

            // ENHANCED: Log every message details
            Log.v(TAG, "📤 SENDING TO ELEVENLABS:");
            Log.v(TAG, "  📦 Chunk size: " + audioChunk.length + " bytes");
            Log.v(TAG, "  📝 Base64 length: " + base64Audio.length() + " chars");
            Log.v(TAG, "  🔗 WebSocket state: " + (webSocket != null ? "connected" : "null"));

            // Log first few bytes for debugging
            if (audioChunk.length >= 10) {
                StringBuilder hexData = new StringBuilder();
                for (int i = 0; i < Math.min(10, audioChunk.length); i++) {
                    hexData.append(String.format("%02X ", audioChunk[i]));
                }
                Log.v(TAG, "  🔢 First 10 bytes: " + hexData.toString());
            }

            boolean sent = webSocket.send(audioMessage.toString());

            Log.v(TAG, "📡 Message sent: " + sent);

            if (!sent) {
                Log.e(TAG, "❌ Failed to send audio chunk to WebSocket!");
                Log.e(TAG, "  WebSocket might be closed or buffer full");
                isWebSocketConnected.set(false);
            } else {
                Log.v(TAG, "✅ Audio chunk sent successfully to ElevenLabs");
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error streaming to WebSocket: " + e.getMessage(), e);
            isWebSocketConnected.set(false);
        }
    }



    /**
     * Start AI audio streaming using the same source as successful core recording
     */
    public boolean startStreaming(int mainRecordingAudioSource) {
        if (isStreaming.get()) {
            Log.w(TAG, "AI streaming already in progress");
            return false;
        }

        Log.d(TAG, "🎤 Starting AI audio streaming...");
        Log.d(TAG, "📡 Main recording audio source: " + getAudioSourceName(mainRecordingAudioSource));
        Log.d(TAG, "🔗 WebSocket connected: " + (webSocket != null));

        // ENHANCED: Check WebSocket state before starting
        if (webSocket == null) {
            Log.e(TAG, "❌ Cannot start streaming - WebSocket is null");
            notifyCallback(cb -> cb.onStreamingFailed("WebSocket not connected"));
            return false;
        }

        // Try different audio sources for AI (avoid conflict with main recording)
        int[] aiAudioSources = {
                MediaRecorder.AudioSource.MIC,                  // Try microphone first
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,  // Communication optimized
                mainRecordingAudioSource,                       // Same as main (might work)
                MediaRecorder.AudioSource.DEFAULT               // Default fallback
        };

        for (int aiSource : aiAudioSources) {
            Log.d(TAG, "🧪 Testing AI audio source: " + getAudioSourceName(aiSource));

            if (initializeAIAudioWithSource(aiSource)) {
                Log.d(TAG, "✅ AI audio started with: " + getAudioSourceName(aiSource));
                startStreamingThread();
                notifyCallback(cb -> cb.onStreamingStarted(getAudioSourceName(aiSource)));

                // ENHANCED: Test if streaming actually works
                testStreamingAfterDelay();
                return true;
            } else {
                Log.w(TAG, "❌ AI audio source failed: " + getAudioSourceName(aiSource));
            }
        }

        Log.e(TAG, "💥 Could not start AI audio capture with any source");
        notifyCallback(cb -> cb.onStreamingFailed("No suitable audio source available"));
        return false;
    }


    /**
     * Stop AI audio streaming
     */
    public void stopStreaming() {
        if (!isStreaming.get()) {
            Log.d(TAG, "AI streaming not active");
            return;
        }

        Log.d(TAG, "🛑 Stopping AI audio streaming...");
        isStreaming.set(false);

        if (aiAudioRecord != null) {
            try {
                if (aiAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    aiAudioRecord.stop();
                }
                aiAudioRecord.release();
                aiAudioRecord = null;
            } catch (Exception e) {
                Log.w(TAG, "Error stopping AI audio: " + e.getMessage());
            }
        }

        notifyCallback(cb -> cb.onStreamingStopped());
        Log.d(TAG, "✅ AI streaming stopped");
    }

    /**
     * Initialize AI AudioRecord with specific source
     */
    private boolean initializeAIAudioWithSource(int audioSource) {
        try {
            aiBufferSize = AudioRecord.getMinBufferSize(AI_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

            aiAudioRecord = new AudioRecord(
                    audioSource,
                    AI_SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    aiBufferSize
            );

            if (aiAudioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                // Test if this source captures real audio (not silence)
                aiAudioRecord.startRecording();

                byte[] testBuffer = new byte[320];
                int bytesRead = aiAudioRecord.read(testBuffer, 0, testBuffer.length);

                boolean hasRealAudio = false;
                if (bytesRead > 0) {
                    for (int i = 0; i < bytesRead - 1; i += 2) {
                        short sample = (short) ((testBuffer[i + 1] << 8) | (testBuffer[i] & 0xFF));
                        if (Math.abs(sample) > 100) {
                            hasRealAudio = true;
                            break;
                        }
                    }
                }

                if (hasRealAudio) {
                    Log.d(TAG, "🎯 AI audio source " + getAudioSourceName(audioSource) + " - HAS REAL AUDIO");
                    return true;
                } else {
                    Log.w(TAG, "⚠️ AI audio source " + getAudioSourceName(audioSource) + " - SILENCE ONLY");
                    aiAudioRecord.stop();
                    aiAudioRecord.release();
                    aiAudioRecord = null;
                    return false;
                }
            } else {
                Log.w(TAG, "❌ AI audio source " + getAudioSourceName(audioSource) + " - NOT AVAILABLE");
                if (aiAudioRecord != null) {
                    aiAudioRecord.release();
                    aiAudioRecord = null;
                }
                return false;
            }

        } catch (Exception e) {
            Log.w(TAG, "❌ AI audio source " + getAudioSourceName(audioSource) + " failed: " + e.getMessage());
            if (aiAudioRecord != null) {
                try {
                    aiAudioRecord.release();
                } catch (Exception ignored) {}
                aiAudioRecord = null;
            }
            return false;
        }
    }

    /**
     * Start the streaming thread
     */
    private void startStreamingThread() {
        isStreaming.set(true);
        executorService.execute(this::streamingLoop);
    }

    /**
     * Main streaming loop - captures and streams audio chunks
     */
    private void streamingLoop() {
        if (aiAudioRecord == null) {
            Log.e(TAG, "AI audio record not initialized");
            return;
        }

        byte[] buffer = new byte[aiBufferSize / 4];
        totalChunksSent = 0;
        chunksWithRealAudio = 0;
        lastLogTime = System.currentTimeMillis();

        try {
            Log.d(TAG, "🤖 AI streaming thread started");

            while (isStreaming.get()) {
                try {
                    int bytesRead = aiAudioRecord.read(buffer, 0, buffer.length);

                    if (bytesRead > 0 && isWebSocketConnected.get()) {
                        byte[] audioChunk = new byte[bytesRead];
                        System.arraycopy(buffer, 0, audioChunk, 0, bytesRead);

                        // Check if this chunk has real audio
                        boolean hasRealAudio = !isAudioSilence(audioChunk);
                        if (hasRealAudio) {
                            chunksWithRealAudio++;
                        }

                        // Stream to WebSocket
                        streamChunkToWebSocket(audioChunk);
                        totalChunksSent++;

                        // Notify callback
                        notifyCallback(cb -> cb.onChunkStreamed(bytesRead, hasRealAudio));
                    }

                    // Periodic logging
                    logPeriodicStatus();

                } catch (Exception e) {
                    Log.w(TAG, "AI streaming error: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "AI streaming thread error: " + e.getMessage());
        } finally {
            isStreaming.set(false);
            Log.d(TAG, "🤖 AI streaming thread stopped");
        }
    }

    /**
     * Stream audio chunk to WebSocket in ElevenLabs format
     */



    /**
     * Check if audio data is mostly silence
     */
    private boolean isAudioSilence(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return true;
        }

        int silenceThreshold = 100;
        int totalSamples = 0;
        int silentSamples = 0;

        // Process 16-bit PCM samples (2 bytes per sample)
        for (int i = 0; i < audioData.length - 1; i += 2) {
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            totalSamples++;

            if (Math.abs(sample) <= silenceThreshold) {
                silentSamples++;
            }
        }

        if (totalSamples == 0) {
            return true;
        }

        // Consider it silence if more than 90% of samples are below threshold
        double silencePercentage = (double) silentSamples / totalSamples;
        return silencePercentage > 0.90;
    }

    /**
     * Log streaming status periodically
     */
    private void logPeriodicStatus() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastLogTime >= 5000) {
            int percentRealAudio = totalChunksSent > 0 ? (chunksWithRealAudio * 100 / totalChunksSent) : 0;

            Log.d(TAG, "📡 AI Streaming Status:");
            Log.d(TAG, "  📤 Total chunks sent: " + totalChunksSent);
            Log.d(TAG, "  🎵 Chunks with real audio: " + chunksWithRealAudio + " (" + percentRealAudio + "%)");
            Log.d(TAG, "  🔗 WebSocket Connected: " + isWebSocketConnected.get());

            // Reset counters
            totalChunksSent = 0;
            chunksWithRealAudio = 0;
            lastLogTime = currentTime;
        }
    }

    /**
     * Get status information for debugging
     */
    public void logStreamingStatus() {
        Log.d(TAG, "=== AI STREAMING STATUS ===");
        Log.d(TAG, "Streaming Active: " + isStreaming.get());
        Log.d(TAG, "WebSocket Connected: " + isWebSocketConnected.get());
        Log.d(TAG, "Audio Record: " + (aiAudioRecord != null ? "Available" : "null"));
        Log.d(TAG, "Buffer Size: " + aiBufferSize);
        Log.d(TAG, "Sample Rate: " + AI_SAMPLE_RATE);
    }

    private void notifyCallback(CallbackAction action) {
        if (callback != null) {
            action.execute(callback);
        }
    }

    @FunctionalInterface
    private interface CallbackAction {
        void execute(StreamingCallback callback);
    }

    private String getAudioSourceName(int audioSource) {
        switch (audioSource) {
            case MediaRecorder.AudioSource.VOICE_CALL: return "VOICE_CALL";
            case MediaRecorder.AudioSource.VOICE_COMMUNICATION: return "VOICE_COMMUNICATION";
            case MediaRecorder.AudioSource.VOICE_UPLINK: return "VOICE_UPLINK";
            case MediaRecorder.AudioSource.VOICE_DOWNLINK: return "VOICE_DOWNLINK";
            case MediaRecorder.AudioSource.MIC: return "MIC";
            case MediaRecorder.AudioSource.DEFAULT: return "DEFAULT";
            default: return "UNKNOWN(" + audioSource + ")";
        }
    }

    // Public getters
    public boolean isStreaming() { return isStreaming.get(); }
    public boolean isWebSocketConnected() { return isWebSocketConnected.get(); }
    public int getTotalChunksSent() { return totalChunksSent; }
    public int getChunksWithRealAudio() { return chunksWithRealAudio; }

    /**
     * Clean up resources
     */
    public void cleanup() {
        stopStreaming();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}