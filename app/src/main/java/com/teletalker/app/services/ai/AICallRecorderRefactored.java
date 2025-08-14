package com.teletalker.app.services.ai;

import android.Manifest;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import com.teletalker.app.services.CallAudioInjector;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Refactored AI Call Recorder - Coordinates all components
 * Core recording ALWAYS works, AI features are completely optional
 */
public class AICallRecorderRefactored {
    private static final String TAG = "AICallRecorder";

    // ElevenLabs Configuration
    private static final String ELEVENLABS_WS_URL = "wss://api.elevenlabs.io/v1/convai/conversation";

    // AI Response modes
    public enum AIMode {
        LISTEN_ONLY("AI listens but doesn't respond"),
        RESPOND_TO_USER("AI responds only to user"),
        RESPOND_TO_CALLER("AI responds only to caller"),
        RESPOND_TO_BOTH("AI responds to both parties"),
        SMART_ASSISTANT("AI acts as smart assistant");

        private final String description;
        AIMode(String description) { this.description = description; }
        public String getDescription() { return description; }
    }

    // Enhanced callback interface with all methods - properly nested
    public static interface AIRecordingCallback extends CallRecorder.RecordingCallback {
        // AI Connection callbacks
        void onAIConnected();
        void onAIDisconnected();
        void onAIError(String error);
        void onAIResponse(String transcript, boolean isPlaying);
        void onAIStreamingStarted(String audioSource);
        void onAIStreamingStopped();

        // Audio Injection callbacks (for call audio injection)
        void onAudioInjectionStarted(String method);
        void onAudioInjectionStopped();
        void onAudioInjected(int chunkSize, long totalBytes);
        void onAudioInjectionError(String error);
    }

    // Core components
    private final Context context;
    private final Handler mainHandler;

    // Recording components
    private final CallRecorder coreRecorder;
    private final AIChunkStreamer chunkStreamer;
    private final AIResponseBuffer responseBuffer;
    private final AudioResponseAccumulator audioAccumulator;
    private final CallAudioInjector audioInjector;

    // WebSocket components
    private WebSocket elevenLabsSocket;
    private OkHttpClient httpClient;
    private String elevenLabsApiKey;
    private String agentId;
    private AIMode currentAIMode = AIMode.SMART_ASSISTANT;

    // Callback
    private AIRecordingCallback callback;

    // AI State management
    private final AtomicBoolean isAIEnabled = new AtomicBoolean(false);
    private final AtomicBoolean isAIConnected = new AtomicBoolean(false);

    // Connection management
    private int connectionAttempts = 0;
    private static final int MAX_CONNECTION_ATTEMPTS = 5;
    private static final long RECONNECT_DELAY_MS = 3000;
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private Handler reconnectHandler = new Handler(Looper.getMainLooper());

    public AICallRecorderRefactored(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());

        // Initialize core components
        this.coreRecorder = new CallRecorder(context);
        this.chunkStreamer = new AIChunkStreamer();
        this.responseBuffer = new AIResponseBuffer();
        this.audioAccumulator = new AudioResponseAccumulator();

        // Initialize your existing audio injector
        this.audioInjector = new CallAudioInjector(context);

        // Initialize AI components
        initializeAIComponents();
        setupCallbacks();
    }

    public void setCallback(AIRecordingCallback callback) {
        this.callback = callback;
        this.coreRecorder.setCallback(callback);
    }

    public void setElevenLabsConfig(String apiKey, String agentId) {
        this.elevenLabsApiKey = apiKey;
        this.agentId = agentId;
        this.isAIEnabled.set(apiKey != null && agentId != null);

        Log.d(TAG, "ElevenLabs config - AI Enabled: " + isAIEnabled.get() +
                ", API Key: " + (apiKey != null ? "***set***" : "null") +
                ", Agent ID: " + agentId);
    }

    public void setAIMode(AIMode mode) {
        this.currentAIMode = mode;
        Log.d(TAG, "AI Mode set to: " + mode.getDescription());
    }

    /**
     * Start recording with optional AI features
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public boolean startRecording(String filename) {
        Log.d(TAG, "🚀 Starting AI-enhanced call recording: " + filename);
        Log.d(TAG, "AI Features: " + (isAIEnabled.get() ? "Enabled" : "Disabled"));

        // === CORE RECORDING (ALWAYS WORKS) ===
        boolean coreRecordingStarted = coreRecorder.startRecording(filename);

        if (coreRecordingStarted) {
            // === OPTIONAL AI FEATURES (Don't affect core recording) ===
            if (isAIEnabled.get()) {
                startAIFeatures();
            } else {
                Log.d(TAG, "⚠️ AI features disabled - recording without AI");
            }

            return true;
        } else {
            Log.e(TAG, "❌ Failed to start core recording");
            return false;
        }
    }

    /**
     * Stop recording and AI features
     */
    public void stopRecording() {
        Log.d(TAG, "🛑 Stopping AI-enhanced call recording...");

        // Stop AI features first
        if (isAIEnabled.get()) {
            stopAIFeatures();
        }

        // Stop core recording
        coreRecorder.stopRecording();
    }

    // ============================================================================
    // AI FEATURES - Completely independent of core recording
    // ============================================================================

    private void initializeAIComponents() {
        try {
            // Initialize HTTP client for WebSocket
            httpClient = new OkHttpClient.Builder()
                    .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                    .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();

            Log.d(TAG, "✅ AI components initialized");
        } catch (Exception e) {
            Log.e(TAG, "⚠️ Failed to initialize AI components: " + e.getMessage());
        }
    }

    private void setupCallbacks() {
        // Setup chunk streamer callback
        chunkStreamer.setCallback(new AIChunkStreamer.StreamingCallback() {
            @Override
            public void onStreamingStarted(String audioSource) {
                Log.d(TAG, "🎤 AI streaming started with: " + audioSource);
                notifyCallback(cb -> cb.onAIStreamingStarted(audioSource));
            }

            @Override
            public void onStreamingFailed(String reason) {
                Log.w(TAG, "⚠️ AI streaming failed: " + reason);
                notifyCallback(cb -> cb.onAIError("Streaming failed: " + reason));
            }

            @Override
            public void onStreamingStopped() {
                Log.d(TAG, "🎤 AI streaming stopped");
                notifyCallback(cb -> cb.onAIStreamingStopped());
            }

            @Override
            public void onChunkStreamed(int chunkSize, boolean hasRealAudio) {
                // Optional: Log chunk streaming stats
                // Log.v(TAG, "📤 Chunk streamed: " + chunkSize + " bytes, real audio: " + hasRealAudio);
            }
        });

        // Setup response buffer callback
        responseBuffer.setCallback(new AIResponseBuffer.ResponseCallback() {
            @Override
            public void onResponseStarted() {
                Log.d(TAG, "🔊 AI response playback started");
            }

            @Override
            public void onResponseStopped() {
                Log.d(TAG, "🔊 AI response playback stopped");
            }

            @Override
            public void onResponseError(String error) {
                Log.e(TAG, "❌ AI response error: " + error);
                notifyCallback(cb -> cb.onAIError("Response error: " + error));
            }

            @Override
            public void onAudioReceived(int audioSize) {
                // Optional: Log audio reception
                // Log.v(TAG, "🔊 AI audio received: " + audioSize + " bytes");
            }
        });

        // Setup audio injector callback (using your existing CallAudioInjector)
        // Note: Your CallAudioInjector doesn't set a persistent callback
        // Instead, callbacks are passed per injection call

        // Setup audio accumulator callback
        audioAccumulator.setCallback(new AudioResponseAccumulator.AccumulatorCallback() {
            @Override
            public void onResponseStarted() {
                Log.d(TAG, "🎬 AI response accumulation started");
            }

            @Override
            public void onChunkAccumulated(int chunkSize, int totalSize) {
                Log.v(TAG, "📦 Audio chunk accumulated: " + chunkSize + " bytes, total: " + totalSize);
            }

            @Override
            public void onResponseCompleted(byte[] completeAudio, long durationMs) {
                Log.d(TAG, "🎯 Complete AI response ready: " + completeAudio.length + " bytes, " + durationMs + "ms");
                // Now inject the complete response
                injectCompleteAudioResponse(completeAudio);
            }

            @Override
            public void onResponseTimeout(byte[] partialAudio, long durationMs) {
                Log.w(TAG, "⏰ AI response timeout: " + partialAudio.length + " bytes, " + durationMs + "ms");
                // Inject partial response anyway
                injectCompleteAudioResponse(partialAudio);
            }

            @Override
            public void onAccumulatorError(String error) {
                Log.e(TAG, "❌ Audio accumulator error: " + error);
                notifyCallback(cb -> cb.onAIError("Audio accumulation error: " + error));
            }
        });
    }

    private void startAIFeatures() {
        Log.d(TAG, "🤖 Starting AI features...");

        try {
            // Initialize response buffer
            if (responseBuffer.initialize()) {
                // Your CallAudioInjector doesn't need initialization
                // It validates environment per injection call
                Log.d(TAG, "🎧 Using existing CallAudioInjector (script-based)");

                // Connect to ElevenLabs
                connectToElevenLabs();

                Log.d(TAG, "✅ AI features initialization started");
            } else {
                Log.w(TAG, "⚠️ AI response buffer failed - continuing with recording only");
                notifyCallback(cb -> cb.onAIError("AI audio playback unavailable"));
            }
        } catch (Exception e) {
            Log.e(TAG, "⚠️ AI features failed to start: " + e.getMessage());
            notifyCallback(cb -> cb.onAIError("AI initialization failed: " + e.getMessage()));
        }
    }

    private void stopAIFeatures() {
        Log.d(TAG, "🛑 Stopping AI features...");

        // Stop connection health monitoring
        reconnectHandler.removeCallbacksAndMessages(null);

        // Reset connection state
        isAIConnected.set(false);
        isConnecting.set(false);
        connectionAttempts = 0;

        // Force complete any pending audio response
        audioAccumulator.forceCompleteResponse();

        // Stop chunk streaming
        chunkStreamer.stopStreaming();

        // DON'T cleanup audio injector here - it might still receive responses
        // audioInjector.cleanup(); // ❌ REMOVED - this causes the executor shutdown error

        // Stop response buffer
        responseBuffer.shutdown();

        // Reset accumulator
        audioAccumulator.reset();

        // Close WebSocket properly
        if (elevenLabsSocket != null) {
            try {
                JSONObject endMessage = new JSONObject();
                endMessage.put("type", "conversation_end");
                elevenLabsSocket.send(endMessage.toString());
                Thread.sleep(500);
            } catch (Exception e) {
                Log.w(TAG, "Error sending end message: " + e.getMessage());
            }

            elevenLabsSocket.close(1000, "Call ended normally");
            elevenLabsSocket = null;
            Log.d(TAG, "🔌 ElevenLabs connection properly closed");
        }

        Log.d(TAG, "✅ AI features stopped");
    }

    // ============================================================================
    // WEBSOCKET CONNECTION MANAGEMENT
    // ============================================================================

    private void connectToElevenLabs() {
        connectToElevenLabsWithRetry();
    }

    private void connectToElevenLabsWithRetry() {
        if (elevenLabsApiKey == null || agentId == null) {
            Log.w(TAG, "⚠️ ElevenLabs credentials not set");
            return;
        }

        if (isConnecting.get()) {
            Log.d(TAG, "🔄 Connection already in progress...");
            return;
        }

        isConnecting.set(true);
        connectionAttempts++;

        try {
            Log.d(TAG, "🔌 Connecting to ElevenLabs (attempt " + connectionAttempts + "/" + MAX_CONNECTION_ATTEMPTS + ")...");

            String wsUrl = ELEVENLABS_WS_URL + "?agent_id=" + agentId;

            Request request = new Request.Builder()
                    .url(wsUrl)
                    .addHeader("xi-api-key", elevenLabsApiKey)
                    .addHeader("Connection", "Upgrade")
                    .addHeader("Upgrade", "websocket")
                    .addHeader("Sec-WebSocket-Version", "13")
                    .addHeader("User-Agent", "TeleTalker-AI/1.0")
                    .build();

            elevenLabsSocket = httpClient.newWebSocket(request, new ElevenLabsWebSocketListener());

            // Wait for connection result with timeout
            waitForConnectionResult();

        } catch (Exception e) {
            Log.e(TAG, "❌ Connection attempt " + connectionAttempts + " failed: " + e.getMessage());
            handleConnectionFailure(e);
        }
    }

    private void waitForConnectionResult() {
        new Thread(() -> {
            try {
                // Wait up to 15 seconds for connection
                for (int i = 0; i < 30; i++) {
                    Thread.sleep(500);

                    if (isAIConnected.get()) {
                        Log.d(TAG, "✅ Connected successfully on attempt " + connectionAttempts);
                        connectionAttempts = 0;
                        isConnecting.set(false);

                        // Start chunk streaming after successful connection
                        if (coreRecorder.isRecording()) {
                            chunkStreamer.startStreaming(coreRecorder.getCurrentAudioSource());
                        }
                        return;
                    }

                    if (!coreRecorder.isRecording()) {
                        Log.d(TAG, "🛑 Recording stopped, canceling connection");
                        isConnecting.set(false);
                        return;
                    }
                }

                // Timeout reached
                Log.w(TAG, "⏰ Connection timeout on attempt " + connectionAttempts);
                handleConnectionFailure(new Exception("Connection timeout"));

            } catch (InterruptedException e) {
                Log.w(TAG, "🛑 Connection wait interrupted");
                isConnecting.set(false);
            }
        }).start();
    }

    private void handleConnectionFailure(Exception error) {
        isConnecting.set(false);
        isAIConnected.set(false);

        if (elevenLabsSocket != null) {
            try {
                elevenLabsSocket.close(1000, "Reconnecting");
            } catch (Exception e) {
                // Ignore close errors
            }
            elevenLabsSocket = null;
        }

        if (connectionAttempts < MAX_CONNECTION_ATTEMPTS && coreRecorder.isRecording()) {
            long delay = RECONNECT_DELAY_MS * connectionAttempts;
            Log.d(TAG, "🔄 Retrying connection in " + delay + "ms... (attempt " + (connectionAttempts + 1) + "/" + MAX_CONNECTION_ATTEMPTS + ")");

            reconnectHandler.postDelayed(() -> {
                if (coreRecorder.isRecording()) {
                    connectToElevenLabsWithRetry();
                }
            }, delay);

        } else {
            Log.e(TAG, "❌ All connection attempts failed or recording stopped");
            connectionAttempts = 0;
            notifyCallback(cb -> cb.onAIError("AI connection failed after " + MAX_CONNECTION_ATTEMPTS + " attempts: " + error.getMessage()));
        }
    }

    // ============================================================================
    // WEBSOCKET LISTENER
    // ============================================================================

    private class ElevenLabsWebSocketListener extends WebSocketListener {

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.d(TAG, "✅ Connected to ElevenLabs AI - Response Code: " + response.code());

            isAIConnected.set(true);
            isConnecting.set(false);
            connectionAttempts = 0;

            // Set WebSocket for chunk streamer
            chunkStreamer.setWebSocket(webSocket);

            notifyCallback(cb -> cb.onAIConnected());
            sendInitialConfiguration(webSocket);
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                JSONObject message = new JSONObject(text);
                String type = message.optString("type");

                Log.d(TAG, "📩 ElevenLabs Message: " + type);

                switch (type) {
                    case "audio":
                        handleAudioEvent(message);
                        break;
                    case "error":
                        handleErrorEvent(message);
                        break;
                    case "ping":
                        handlePingEvent(webSocket, message);
                        break;
                    case "agent_response":
                        handleAgentResponseEvent(message);
                        break;
                    case "user_transcript":
                        handleUserTranscriptEvent(message);
                        break;
                    default:
                        Log.d(TAG, "📄 Unhandled message type: " + type);
                        break;
                }

            } catch (JSONException e) {
                Log.e(TAG, "❌ Error parsing ElevenLabs message: " + e.getMessage());
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            byte[] audioData = bytes.toByteArray();
            Log.d(TAG, "🔊 Binary audio received: " + audioData.length + " bytes");

            // Add to response buffer for local playback
            responseBuffer.addAudioResponse(audioData);

            // Add to accumulator for complete response injection
            audioAccumulator.addAudioChunk(audioData);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            Log.e(TAG, "❌ ElevenLabs connection failure: " + t.getMessage());

            if (response != null) {
                Log.e(TAG, "📄 Response: " + response.code() + " - " + response.message());
            }

            if (isNetworkError(t)) {
                Log.w(TAG, "🌐 Network error detected, attempting reconnection...");
                handleConnectionFailure(new Exception(t.getMessage()));
            } else {
                isAIConnected.set(false);
                isConnecting.set(false);
                chunkStreamer.setWebSocket(null);
                notifyCallback(cb -> cb.onAIError("AI connection failed: " + t.getMessage()));
            }
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "🔌 ElevenLabs connection closed: " + code + " - " + reason);
            isAIConnected.set(false);
            isConnecting.set(false);
            chunkStreamer.setWebSocket(null);

            if (coreRecorder.isRecording() && code != 1000) {
                Log.w(TAG, "🔄 Unexpected closure during recording, attempting reconnection...");
                handleConnectionFailure(new Exception("Connection closed unexpectedly: " + reason));
            } else {
                notifyCallback(cb -> cb.onAIDisconnected());
            }
        }
    }

    // ============================================================================
    // MESSAGE HANDLERS
    // ============================================================================

    private void sendInitialConfiguration(WebSocket webSocket) {
        try {
            JSONObject config = new JSONObject();
            config.put("type", "conversation_initiation_client_data");

            JSONObject conversationConfig = new JSONObject();
            conversationConfig.put("agent_id", agentId);

            config.put("conversation_config", conversationConfig);
            webSocket.send(config.toString());

            Log.d(TAG, "📤 Initial configuration sent");

        } catch (JSONException e) {
            Log.w(TAG, "Error sending AI config: " + e.getMessage());
        }
    }

    private void handleAudioEvent(JSONObject message) {
        try {
            JSONObject audioEvent = message.optJSONObject("audio_event");
            if (audioEvent == null) return;

            String base64Audio = audioEvent.optString("audio_base_64", "");
            if (base64Audio.isEmpty()) return;

            byte[] audioData = Base64.getDecoder().decode(base64Audio);

            // Add to response buffer for local playback
            responseBuffer.addAudioResponse(audioData);

            // Add to accumulator for complete response injection
            audioAccumulator.addAudioChunk(audioData);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling audio event: " + e.getMessage());
        }
    }

    /**
     * Inject complete AI audio response (called by accumulator when response is complete)
     */
    private void injectCompleteAudioResponse(byte[] completeAudio) {
        if (completeAudio == null || completeAudio.length == 0) {
            Log.w(TAG, "⚠️ Empty complete audio response");
            return;
        }

        Log.d(TAG, "🎯 Injecting complete AI response: " + completeAudio.length + " bytes");

        // Safety check: Don't inject if we're not recording anymore
        if (!coreRecorder.isRecording()) {
            Log.d(TAG, "⚠️ Skipping complete audio injection - recording not active");
            return;
        }

        // Create callback for this complete injection
        CallAudioInjector.InjectionCallback injectionCallback = new CallAudioInjector.InjectionCallback() {
            @Override
            public void onInjectionStarted() {
                Log.d(TAG, "🎧 Complete audio injection started");
                notifyCallback(cb -> cb.onAudioInjectionStarted("CallAudioInjector (Complete Response)"));
            }

            @Override
            public void onInjectionCompleted(boolean success) {
                if (success) {
                    Log.d(TAG, "🎧 Complete audio injection completed successfully");
                    notifyCallback(cb -> cb.onAudioInjected(completeAudio.length, completeAudio.length));
                } else {
                    Log.w(TAG, "⚠️ Complete audio injection completed with failure");
                    notifyCallback(cb -> cb.onAudioInjectionError("Complete injection script failed"));
                }
                notifyCallback(cb -> cb.onAudioInjectionStopped());
            }

            @Override
            public void onInjectionError(String error) {
                Log.e(TAG, "❌ Complete audio injection error: " + error);
                notifyCallback(cb -> cb.onAudioInjectionError(error));
                notifyCallback(cb -> cb.onAudioInjectionStopped());
            }

            @Override
            public void onInjectionProgress(long elapsedMs, long expectedMs) {
                Log.v(TAG, "🎯 Complete injection progress: " + elapsedMs + "/" + expectedMs + "ms");
            }

            @Override
            public void onAudioValidated(long durationMs, int sampleRate) {
                Log.d(TAG, "✅ Complete audio validated: " + durationMs + "ms @ " + sampleRate + "Hz");
            }

            @Override
            public void onScriptOutput(String output) {
                Log.v(TAG, "📜 Complete injection script output: " + output);
            }

            @Override
            public void onCallStateValidated(String audioMode) {
                Log.d(TAG, "📞 Complete injection call state validated: " + audioMode);
            }
        };

        try {
            // Use your existing CallAudioInjector method with complete audio
            audioInjector.injectAudio16kMono(completeAudio, injectionCallback);
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to inject complete audio: " + e.getMessage(), e);
            // Don't crash the whole system if injection fails
            notifyCallback(cb -> cb.onAudioInjectionError("Complete injection failed: " + e.getMessage()));
        }
    }

    private void handleErrorEvent(JSONObject message) {
        String errorMessage = message.optString("message", "Unknown error");
        Log.e(TAG, "❌ ElevenLabs Error: " + errorMessage);
        notifyCallback(cb -> cb.onAIError("ElevenLabs Error: " + errorMessage));
    }

    private void handlePingEvent(WebSocket webSocket, JSONObject message) {
        try {
            JSONObject pingEvent = message.optJSONObject("ping_event");
            int eventId = pingEvent != null ? pingEvent.optInt("event_id", 0) : 0;

            JSONObject pongResponse = new JSONObject();
            pongResponse.put("type", "pong");
            pongResponse.put("event_id", eventId);

            webSocket.send(pongResponse.toString());
            Log.d(TAG, "🏓 Pong sent (event_id: " + eventId + ")");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling ping event: " + e.getMessage());
        }
    }

    private void handleAgentResponseEvent(JSONObject message) {
        try {
            String transcript;

            if (message.has("agent_response_event")) {
                JSONObject responseEvent = message.optJSONObject("agent_response_event");
                if (responseEvent != null) {
                    transcript = responseEvent.optString("agent_response", "");
                } else {
                    transcript = "";
                }
            } else if (message.has("agent_response_audio_transcript")) {
                transcript = message.optString("agent_response_audio_transcript", "");
            } else if (message.has("transcript")) {
                transcript = message.optString("transcript", "");
            } else {
                transcript = "";
            }

            if (!transcript.isEmpty()) {
                Log.d(TAG, "🗣️ AI Response: '" + transcript + "'");
                notifyCallback(cb -> cb.onAIResponse(transcript, responseBuffer.isCurrentlyPlaying()));
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling agent response: " + e.getMessage());
        }
    }

    private void handleUserTranscriptEvent(JSONObject message) {
        String transcript = message.optString("transcript", "");
        boolean isFinal = message.optBoolean("is_final", false);

        if (!transcript.isEmpty()) {
            Log.d(TAG, "👤 User Said: '" + transcript + "' (final: " + isFinal + ")");
        }
    }

    private boolean isNetworkError(Throwable t) {
        String message = t.getMessage();
        if (message == null) return false;

        return message.contains("unexpected end of stream") ||
                message.contains("timeout") ||
                message.contains("connection") ||
                message.contains("network") ||
                message.contains("host") ||
                t instanceof java.net.SocketTimeoutException ||
                t instanceof java.net.ConnectException ||
                t instanceof java.io.IOException;
    }

    // ============================================================================
    // PUBLIC GETTERS AND STATUS
    // ============================================================================

    public boolean isRecording() {
        return coreRecorder.isRecording();
    }

    public CallRecorder.RecordingMode getCurrentRecordingMode() {
        return coreRecorder.getCurrentRecordingMode();
    }

    public String getCurrentRecordingFile() {
        return coreRecorder.getCurrentRecordingFile();
    }

    public boolean isRooted() {
        return coreRecorder.isRooted();
    }

    public boolean hasVoiceCallAccess() {
        return coreRecorder.hasVoiceCallAccess();
    }

    public boolean isAIEnabled() {
        return isAIEnabled.get();
    }

    public boolean isAIConnected() {
        return isAIConnected.get();
    }

    public boolean isAIResponding() {
        return responseBuffer.isCurrentlyPlaying();
    }

    public boolean isAudioInjectionActive() {
        return audioInjector.isCurrentlyInjecting();
    }

    public String getAudioInjectionStatus() {
        return audioInjector.getInjectionStatus();
    }

    public AIMode getCurrentAIMode() {
        return currentAIMode;
    }

    public void logAIStatus() {
        Log.d(TAG, "=== COMPLETE AI STATUS ===");
        Log.d(TAG, "AI Enabled: " + isAIEnabled.get());
        Log.d(TAG, "AI Connected: " + isAIConnected.get());
        Log.d(TAG, "AI Responding: " + responseBuffer.isCurrentlyPlaying());
        Log.d(TAG, "Audio Injection: " + audioInjector.getInjectionStatus());
        Log.d(TAG, "Audio Accumulation: " + audioAccumulator.getAccumulationStatus());
        Log.d(TAG, "Recording Active: " + coreRecorder.isRecording());

        chunkStreamer.logStreamingStatus();
        responseBuffer.logResponseStatus();
        audioInjector.logStatus();
        audioAccumulator.logStatus();
    }

    private void notifyCallback(CallbackAction action) {
        if (callback != null) {
            mainHandler.post(() -> action.execute(callback));
        }
    }

    @FunctionalInterface
    private interface CallbackAction {
        void execute(AIRecordingCallback callback);
    }

    /**
     * Cleanup all resources
     */
    public void cleanup() {
        stopRecording();
        chunkStreamer.cleanup();
        responseBuffer.destroy();
        audioAccumulator.cleanup();
        audioInjector.cleanup();

        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
        }
    }
}