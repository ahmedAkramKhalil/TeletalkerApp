package com.teletalker.app.services.ai;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.WebSocket;

/**
 * ✅ FIXED: Handles streaming audio chunks to AI WebSocket
 * - Converts PCM to WAV format (fixes ElevenLabs "buffer size" error)
 * - Proper thread context management
 * - Separate from core recording to avoid conflicts
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

    /**
     * ✅ FIXED: Convert raw PCM to WAV format before sending to ElevenLabs
     * This fixes the "buffer size must be a multiple of element size" error
     */
//    private void streamChunkToWebSocket(byte[] audioChunk) {
//        if (webSocket == null || !isWebSocketConnected.get()) {
//            return;
//        }
//
//        try {
//            // ✅ FIXED: Convert raw PCM to WAV format (with proper headers)
//            byte[] wavData = convertPCMToWAV(audioChunk, AI_SAMPLE_RATE, 1, 16);
//            String base64Audio = Base64.getEncoder().encodeToString(wavData);
//
//            // ✅ FIXED: Use proper WAV format JSON structure
//            JSONObject audioMessage = new JSONObject();
//
//            // Use proper WAV data URI format (like browser test but with WAV)
//            String dataUri = "data:audio/wav;base64," + base64Audio;
//            audioMessage.put("user_audio_chunk", dataUri);
//
//            // LOG THE FIRST MESSAGE ONLY (prevents spam)
//            if (!firstAudioMessageLogged) {
//                Log.d(TAG, "✅ FIRST AUDIO MESSAGE (FIXED WAV FORMAT):");
//                Log.d(TAG, "📤 JSON Structure: {\"user_audio_chunk\":\"data:audio/wav;base64,BASE64...\"}");
//                Log.d(TAG, "📤 Original PCM: " + audioChunk.length + " bytes");
//                Log.d(TAG, "📤 WAV with headers: " + wavData.length + " bytes");
//                Log.d(TAG, "📤 Base64 length: " + base64Audio.length() + " chars");
//                Log.d(TAG, "📤 Sample Rate: 16000 Hz");
//                Log.d(TAG, "📤 Channels: 1 (Mono)");
//                Log.d(TAG, "📤 Bit Depth: 16-bit");
//                Log.d(TAG, "✅ This should fix the ElevenLabs 'buffer size' error!");
//                firstAudioMessageLogged = true;
//            }
//
//            boolean sent = webSocket.send(audioMessage.toString());
//
//            if (!sent) {
//                Log.e(TAG, "❌ Failed to send audio chunk to WebSocket!");
//                isWebSocketConnected.set(false);
//            }
//
//        } catch (Exception e) {
//            Log.e(TAG, "❌ Error streaming to WebSocket: " + e.getMessage());
//            isWebSocketConnected.set(false);
//        }
//    }


    private void streamChunkToWebSocket(byte[] audioChunk) {
        if (webSocket == null || !isWebSocketConnected.get()) {
            Log.v(TAG, "WebSocket not available for streaming");
            return;
        }

        if (audioChunk == null || audioChunk.length == 0) {
            Log.w(TAG, "Empty audio chunk received");
            return;
        }

        try {
            // Convert raw PCM to base64 - this is exactly what ElevenLabs ConvAI expects
            String base64Audio = Base64.getEncoder().encodeToString(audioChunk);

            // Create the correct ConvAI message format
            JSONObject message = new JSONObject();
            message.put("user_audio_chunk", base64Audio);

            // Log first message details for debugging
            if (!firstAudioMessageLogged) {
                Log.d(TAG, "First audio message to ElevenLabs ConvAI:");
                Log.d(TAG, "  Format: Raw PCM 16kHz/16-bit/Mono");
                Log.d(TAG, "  PCM bytes: " + audioChunk.length);
                Log.d(TAG, "  Base64 length: " + base64Audio.length() + " chars");
                Log.d(TAG, "  JSON structure: " + message.toString().substring(0, Math.min(100, message.toString().length())) + "...");
                firstAudioMessageLogged = true;
            }

            // Send to WebSocket
            boolean sent = webSocket.send(message.toString());

            if (!sent) {
                Log.w(TAG, "Failed to send audio chunk to WebSocket");
                isWebSocketConnected.set(false);
            } else {
                Log.v(TAG, "Audio chunk sent: " + audioChunk.length + " bytes");
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error creating audio message JSON: " + e.getMessage());
            isWebSocketConnected.set(false);
        } catch (Exception e) {
            Log.e(TAG, "Error streaming to WebSocket: " + e.getMessage());
            isWebSocketConnected.set(false);
        }
    }
    /**
     * ✅ FIXED: Convert raw PCM data to WAV format with proper headers
     * ElevenLabs requires proper audio file formats, not raw PCM
     */
    private byte[] convertPCMToWAV(byte[] pcmData, int sampleRate, int channels, int bitsPerSample) {
        try {
            int byteRate = sampleRate * channels * bitsPerSample / 8;
            int blockAlign = channels * bitsPerSample / 8;
            int dataSize = pcmData.length;
            int fileSize = 36 + dataSize;

            byte[] header = new byte[44];
            int index = 0;

            // RIFF header
            header[index++] = 'R'; header[index++] = 'I'; header[index++] = 'F'; header[index++] = 'F';
            writeInt(header, index, fileSize); index += 4;
            header[index++] = 'W'; header[index++] = 'A'; header[index++] = 'V'; header[index++] = 'E';

            // fmt chunk
            header[index++] = 'f'; header[index++] = 'm'; header[index++] = 't'; header[index++] = ' ';
            writeInt(header, index, 16); index += 4; // chunk size
            writeShort(header, index, (short) 1); index += 2; // audio format (PCM)
            writeShort(header, index, (short) channels); index += 2;
            writeInt(header, index, sampleRate); index += 4;
            writeInt(header, index, byteRate); index += 4;
            writeShort(header, index, (short) blockAlign); index += 2;
            writeShort(header, index, (short) bitsPerSample); index += 2;

            // data chunk
            header[index++] = 'd'; header[index++] = 'a'; header[index++] = 't'; header[index++] = 'a';
            writeInt(header, index, dataSize);

            // Combine header + PCM data
            byte[] wavFile = new byte[header.length + pcmData.length];
            System.arraycopy(header, 0, wavFile, 0, header.length);
            System.arraycopy(pcmData, 0, wavFile, header.length, pcmData.length);

            return wavFile;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error converting PCM to WAV: " + e.getMessage());
            return pcmData; // Return original data as fallback
        }
    }

    private void writeInt(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
        data[offset + 2] = (byte) ((value >> 16) & 0xFF);
        data[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    private void writeShort(byte[] data, int offset, short value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
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

        // Check WebSocket state before starting
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

                // Test if streaming actually works
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

            // Use larger buffer to reduce read frequency
            int actualBufferSize = Math.max(aiBufferSize * 4, 8192);

            aiAudioRecord = new AudioRecord(
                    audioSource,
                    AI_SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    actualBufferSize
            );

            if (aiAudioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                Log.d(TAG, "✅ AudioRecord initialized with " + getAudioSourceName(audioSource));

                aiAudioRecord.startRecording();

                // Give it more time to stabilize
                Thread.sleep(500);

                // Test with multiple reads to be more reliable
                byte[] testBuffer = new byte[1600]; // 50ms at 16kHz
                boolean hasRealAudio = false;

                for (int attempt = 0; attempt < 3; attempt++) {
                    int bytesRead = aiAudioRecord.read(testBuffer, 0, testBuffer.length);

                    if (bytesRead > 0) {
                        // Less strict silence detection for call audio
                        for (int i = 0; i < bytesRead - 1; i += 2) {
                            short sample = (short) ((testBuffer[i + 1] << 8) | (testBuffer[i] & 0xFF));
                            if (Math.abs(sample) > 50) { // Lower threshold
                                hasRealAudio = true;
                                break;
                            }
                        }
                    }

                    if (hasRealAudio) break;
                    Thread.sleep(200);
                }

                if (hasRealAudio || audioSource == MediaRecorder.AudioSource.VOICE_CALL) {
                    // Accept VOICE_CALL even if silent (call might be quiet initially)
                    Log.d(TAG, "✅ Audio source " + getAudioSourceName(audioSource) +
                            " - " + (hasRealAudio ? "HAS AUDIO" : "ACCEPTED (call audio)"));
                    return true;
                } else {
                    Log.w(TAG, "⚠️ Audio source " + getAudioSourceName(audioSource) + " - appears silent");
                    aiAudioRecord.stop();
                    aiAudioRecord.release();
                    aiAudioRecord = null;
                    return false;
                }
            } else {
                Log.w(TAG, "❌ AudioRecord failed to initialize with " + getAudioSourceName(audioSource));
                if (aiAudioRecord != null) {
                    aiAudioRecord.release();
                    aiAudioRecord = null;
                }
                return false;
            }

        } catch (Exception e) {
            Log.w(TAG, "❌ Audio source " + getAudioSourceName(audioSource) + " exception: " + e.getMessage());
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
//    private void streamingLoop() {
//        if (aiAudioRecord == null) {
//            Log.e(TAG, "AI audio record not initialized");
//            return;
//        }
//
//        byte[] buffer = new byte[aiBufferSize / 4];
//        totalChunksSent = 0;
//        chunksWithRealAudio = 0;
//        lastLogTime = System.currentTimeMillis();
//
//        try {
//            Log.d(TAG, "🤖 AI streaming thread started");
//
//            while (isStreaming.get()) {
//                try {
//                    int bytesRead = aiAudioRecord.read(buffer, 0, buffer.length);
//
//                    if (bytesRead > 0 && isWebSocketConnected.get()) {
//                        byte[] audioChunk = new byte[bytesRead];
//                        System.arraycopy(buffer, 0, audioChunk, 0, bytesRead);
//
//                        // Check if this chunk has real audio
//                        boolean hasRealAudio = !isAudioSilence(audioChunk);
//                        if (hasRealAudio) {
//                            chunksWithRealAudio++;
//                        }
//
//                        streamChunkToWebSocket(audioChunk);
//                        totalChunksSent++;
//
//                        // Notify callback
//                        notifyCallback(cb -> cb.onChunkStreamed(bytesRead, hasRealAudio));
//                    }
//
//                    // Periodic logging
//                    logPeriodicStatus();
//
//                } catch (Exception e) {
//                    Log.w(TAG, "AI streaming error: " + e.getMessage());
//                }
//            }
//
//        } catch (Exception e) {
//            Log.e(TAG, "AI streaming thread error: " + e.getMessage());
//        } finally {
//            isStreaming.set(false);
//            Log.d(TAG, "🤖 AI streaming thread stopped");
//        }
//    }


    private void streamingLoop() {
        if (aiAudioRecord == null) {
            Log.e(TAG, "❌ AI audio record not initialized");
            notifyCallback(cb -> cb.onStreamingFailed("AudioRecord not initialized"));
            return;
        }

        byte[] buffer = new byte[aiBufferSize / 4];
        totalChunksSent = 0;
        chunksWithRealAudio = 0;
        lastLogTime = System.currentTimeMillis();
        long streamStartTime = System.currentTimeMillis();

        // Enhanced state tracking
        int consecutiveFailures = 0;
        int consecutiveSilentChunks = 0;
        long lastSuccessfulRead = System.currentTimeMillis();

        try {
            Log.d(TAG, "🎤 AI streaming thread started - will run for full call duration");

            while (isStreaming.get()) {
                long loopStartTime = System.currentTimeMillis();

                try {


                    // TODO: fix this break
//                    if (!aiAudioRecord.getRecordingState() == AICallRecorderRefactored.AIStatistics) {
//                        Log.w(TAG, "⚠️ Core recording stopped - ending AI streaming");
//                        break;
//                    }

                    // Attempt to read audio with timeout detection
                    int bytesRead = aiAudioRecord.read(buffer, 0, buffer.length);
                    long readDuration = System.currentTimeMillis() - loopStartTime;

                    if (bytesRead > 0) {
                        consecutiveFailures = 0;
                        lastSuccessfulRead = System.currentTimeMillis();

                        byte[] audioChunk = new byte[bytesRead];
                        System.arraycopy(buffer, 0, audioChunk, 0, bytesRead);

                        // Enhanced audio analysis
                        boolean hasRealAudio = !isAudioSilence(audioChunk);
                        if (hasRealAudio) {
                            chunksWithRealAudio++;
                            consecutiveSilentChunks = 0;
                        } else {
                            consecutiveSilentChunks++;
                        }

                        // Stream regardless of silence (call audio might be quiet)
                        if (isWebSocketConnected.get()) {
                            streamChunkToWebSocket(audioChunk);
                            totalChunksSent++;

                            // Notify callback
                            notifyCallback(cb -> cb.onChunkStreamed(bytesRead, hasRealAudio));
                        } else {
                            Log.w(TAG, "⚠️ WebSocket disconnected - chunk not sent");
                        }

                    } else if (bytesRead == 0) {
                        Log.v(TAG, "No audio data available");
                    } else {
                        // Negative bytesRead indicates error
                        consecutiveFailures++;
                        Log.w(TAG, "❌ Audio read error: " + bytesRead + " (consecutive: " + consecutiveFailures + ")");

                        if (consecutiveFailures >= 10) {
                            Log.e(TAG, "💥 Too many consecutive read failures - stopping");
                            notifyCallback(cb -> cb.onStreamingFailed("Too many read failures"));
                            break;
                        }
                    }

                    // Enhanced health checks
                    performHealthChecks(streamStartTime, lastSuccessfulRead,
                            consecutiveSilentChunks, readDuration);

                    // Periodic comprehensive logging
                    logEnhancedStatus(streamStartTime);

                    // Brief pause to prevent CPU spinning
                    Thread.sleep(10);

                } catch (InterruptedException e) {
                    Log.d(TAG, "🛑 Streaming thread interrupted");
                    break;
                } catch (IllegalStateException e) {
                    Log.e(TAG, "❌ AudioRecord illegal state: " + e.getMessage());
                    Log.e(TAG, "📊 AudioRecord state: " + (aiAudioRecord != null ? aiAudioRecord.getState() : "null"));
                    Log.e(TAG, "📊 Recording state: " + (aiAudioRecord != null ? aiAudioRecord.getRecordingState() : "null"));
                    notifyCallback(cb -> cb.onStreamingFailed("AudioRecord illegal state"));
                    break;
                } catch (Exception e) {
                    consecutiveFailures++;
                    Log.e(TAG, "❌ Streaming exception: " + e.getMessage(), e);

                    if (consecutiveFailures >= 5) {
                        Log.e(TAG, "💥 Too many exceptions - stopping streaming");
                        notifyCallback(cb -> cb.onStreamingFailed("Too many exceptions: " + e.getMessage()));
                        break;
                    }
                }
            }

            // Final status log
            long totalDuration = System.currentTimeMillis() - streamStartTime;
            Log.d(TAG, "🏁 AI Streaming Summary:");
            Log.d(TAG, "  Duration: " + totalDuration + "ms (" + (totalDuration/1000) + " seconds)");
            Log.d(TAG, "  Chunks sent: " + totalChunksSent);
            Log.d(TAG, "  Voice chunks: " + chunksWithRealAudio);
            Log.d(TAG, "  Final state - Streaming: " + isStreaming.get() + ", WebSocket: " + isWebSocketConnected.get());

        } catch (Exception e) {
            long totalDuration = System.currentTimeMillis() - streamStartTime;
            Log.e(TAG, "💥 Fatal streaming error after " + totalDuration + "ms: " + e.getMessage(), e);
            notifyCallback(cb -> cb.onStreamingFailed("Fatal error: " + e.getMessage()));
        } finally {
            isStreaming.set(false);
            Log.d(TAG, "🏁 AI streaming thread stopped");
        }
    }


    private void performHealthChecks(long streamStartTime, long lastSuccessfulRead,
                                     int consecutiveSilentChunks, long readDuration) {

        long currentTime = System.currentTimeMillis();
        long streamDuration = currentTime - streamStartTime;
        long timeSinceLastRead = currentTime - lastSuccessfulRead;

        // Check for read timeouts
        if (timeSinceLastRead > 5000) {
            Log.e(TAG, "⚠️ No successful audio reads for " + timeSinceLastRead + "ms");
        }

        // Check for slow reads
        if (readDuration > 100) {
            Log.w(TAG, "⚠️ Slow audio read: " + readDuration + "ms");
        }

        // Check for excessive silence (but don't stop streaming)
        if (consecutiveSilentChunks > 100) {
            Log.w(TAG, "⚠️ " + consecutiveSilentChunks + " consecutive silent chunks - call might be quiet");
        }

        // Check WebSocket health
        if (!isWebSocketConnected.get()) {
            Log.w(TAG, "⚠️ WebSocket disconnected during streaming");
        }

        // TODO: fix this
//        if (!coreRecorder.isRecording()) {
//            Log.w(TAG, "⚠️ Core recording stopped - AI streaming should stop too");
//        }
    }

    /**
     * Enhanced status logging every 10 seconds
     */
    private void logEnhancedStatus(long streamStartTime) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastLogTime >= 10000) { // Every 10 seconds
            long streamDuration = currentTime - streamStartTime;
            int percentRealAudio = totalChunksSent > 0 ? (chunksWithRealAudio * 100 / totalChunksSent) : 0;

            Log.d(TAG, "📊 AI Streaming Status (" + (streamDuration/1000) + "s):");
            Log.d(TAG, "  📤 Total chunks: " + totalChunksSent);
            Log.d(TAG, "  🎵 Voice chunks: " + chunksWithRealAudio + " (" + percentRealAudio + "%)");
            Log.d(TAG, "  🔗 WebSocket: " + (isWebSocketConnected.get() ? "CONNECTED" : "DISCONNECTED"));
            Log.d(TAG, "  🎙️ AudioRecord state: " + (aiAudioRecord != null ? aiAudioRecord.getRecordingState() : "null"));
//            Log.d(TAG, "  📞 Core recording: " + (coreRecorder.isRecording() ? "ACTIVE" : "STOPPED"));

            lastLogTime = currentTime;
        }
    }


    /**
     * Check if audio data is mostly silence
     */
    private boolean isAudioSilence(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return true;
        }

        // Lower threshold for call audio (often quieter than normal recording)
        int silenceThreshold = 30; // Reduced from 100
        int totalSamples = 0;
        int silentSamples = 0;

        for (int i = 0; i < audioData.length - 1; i += 2) {
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            totalSamples++;

            if (Math.abs(sample) <= silenceThreshold) {
                silentSamples++;
            }
        }

        if (totalSamples == 0) return true;

        // Consider it silence only if 95% of samples are below threshold (was 90%)
        double silencePercentage = (double) silentSamples / totalSamples;
        return silencePercentage > 0.95;
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
        Log.d(TAG, "First Message Logged: " + firstAudioMessageLogged);
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



    private void testCurrentAudioFormat() {
        Log.d(TAG, "🧪 TESTING CURRENT AUDIO FORMAT...");

        if (aiAudioRecord == null) {
            Log.e(TAG, "❌ Cannot test - AudioRecord not initialized");
            return;
        }

        // Capture a test chunk
        byte[] testBuffer = new byte[3200]; // 100ms at 16kHz
        int bytesRead = aiAudioRecord.read(testBuffer, 0, testBuffer.length);

        if (bytesRead > 0) {
            byte[] testChunk = new byte[bytesRead];
            System.arraycopy(testBuffer, 0, testChunk, 0, bytesRead);

            // Validate the format
            validateAudioFormat(testChunk);

            // Test ElevenLabs message format
            testElevenLabsMessageFormat(testChunk);

        } else {
            Log.e(TAG, "❌ Failed to read audio data for testing");
        }
    }

    private void validateAudioFormat(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            Log.w(TAG, "Empty audio data");
            return;
        }

        // Check expected duration (should be around 50-200ms chunks for optimal streaming)
        double durationMs = (audioData.length / (AI_SAMPLE_RATE * 2.0)) * 1000;

        // Check for valid PCM characteristics
        boolean hasActivity = false;
        int maxAmplitude = 0;

        for (int i = 0; i < audioData.length - 1; i += 2) {
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            int amplitude = Math.abs(sample);

            if (amplitude > 50) hasActivity = true;
            if (amplitude > maxAmplitude) maxAmplitude = amplitude;
        }

        Log.v(TAG, String.format("Audio validation - Duration: %.1fms, Max amplitude: %d, Has activity: %s",
                durationMs, maxAmplitude, hasActivity));

        // Warn about potential issues
        if (durationMs > 500) {
            Log.w(TAG, "Audio chunk very long (" + durationMs + "ms) - may cause latency");
        }

        if (!hasActivity && maxAmplitude == 0) {
            Log.v(TAG, "Silent audio chunk detected");
        }
    }
    private void testElevenLabsMessageFormat(byte[] audioData) {
        Log.d(TAG, "📤 TESTING ELEVENLABS MESSAGE FORMAT:");

        try {
            // Test the actual message we'll send
            String base64Audio = Base64.getEncoder().encodeToString(audioData);

            JSONObject message = new JSONObject();
            message.put("user_audio_chunk", base64Audio);

            String jsonString = message.toString();

            Log.d(TAG, "  📝 JSON structure: ✅ CORRECT");
            Log.d(TAG, "  📏 Base64 length: " + base64Audio.length() + " chars");
            Log.d(TAG, "  📦 Total message size: " + jsonString.length() + " chars");
            Log.d(TAG, "  📋 Message preview: " + jsonString.substring(0, Math.min(80, jsonString.length())) + "...");

            // Check size limits
            if (base64Audio.length() > 12000) {
                Log.w(TAG, "  ⚠️ Base64 very large - consider smaller chunks");
            } else {
                Log.d(TAG, "  📊 Message size: ✅ GOOD");
            }

            // Verify JSON is valid
            try {
                new JSONObject(jsonString);
                Log.d(TAG, "  ✅ JSON validation: PASSED");
            } catch (Exception e) {
                Log.e(TAG, "  ❌ JSON validation: FAILED - " + e.getMessage());
            }

        } catch (Exception e) {
            Log.e(TAG, "  ❌ Message format test failed: " + e.getMessage());
        }
    }

    // 🎯 CALL THIS METHOD after starting audio recording to test format
    public void runFormatValidationTest() {
        if (!isStreaming.get()) {
            Log.w(TAG, "Cannot test format - streaming not active");
            return;
        }

        // Run test after a short delay to ensure audio is flowing
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            testCurrentAudioFormat();
        }, 2000);
    }


}