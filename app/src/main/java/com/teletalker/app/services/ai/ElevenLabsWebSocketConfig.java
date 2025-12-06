package com.teletalker.app.services.ai;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Base64;

import okhttp3.WebSocket;

// Complete ElevenLabs WebSocket configuration and message handling
public class ElevenLabsWebSocketConfig {
    private static final String TAG = "ElevenLabsConfig";

    /**
     * Send initial configuration to ElevenLabs ConvAI API
     * This must be sent immediately after WebSocket connection opens
     */
//    public static void sendInitialConfiguration(WebSocket webSocket, String agentId) {
//        try {
//            JSONObject config = new JSONObject();
//            config.put("type", "conversation_initiation_client_data");
//
//            // Conversation configuration
//            JSONObject conversationConfig = new JSONObject();
//            conversationConfig.put("agent_id", agentId);
//
//            // Optional: Audio input configuration
//            JSONObject audioConfig = new JSONObject();
//            audioConfig.put("input_sample_rate", 16000);
//            audioConfig.put("output_sample_rate", 16000);
//            audioConfig.put("input_encoding", "pcm_16000");
//            audioConfig.put("output_encoding", "pcm_16000");
//
//            conversationConfig.put("audio_interface", audioConfig);
//            config.put("conversation_config", conversationConfig);
//
//            // Send configuration
//            boolean sent = webSocket.send(config.toString());
//
//            if (sent) {
//                Log.d(TAG, "✅ Initial configuration sent successfully");
//                Log.d(TAG, "🎯 Agent ID: " + agentId);
//                Log.d(TAG, "🎵 Audio: 16kHz PCM, Mono");
//            } else {
//                Log.e(TAG, "❌ Failed to send initial configuration");
//            }
//
//        } catch (JSONException e) {
//            Log.e(TAG, "❌ Error creating initial configuration: " + e.getMessage());
//        }
//    }

    // Update the sendInitialConfiguration method
    public static void sendInitialConfiguration(
            WebSocket webSocket,
            String agentId,
            JSONObject conversationInitData) {

        try {
            JSONObject config = new JSONObject();
            config.put("type", "conversation_initiation_client_data");

            // Add agent ID if not in URL
            if (agentId != null) {
                config.put("agent_id", agentId);
            }

            // Add conversation initiation data if provided
            if (conversationInitData != null) {
                // Add dynamic variables
                if (conversationInitData.has("dynamic_variables")) {
                    config.put("dynamic_variables",
                            conversationInitData.getJSONObject("dynamic_variables"));
                }

                // Add conversation config override
                if (conversationInitData.has("conversation_config_override")) {
                    config.put("conversation_config_override",
                            conversationInitData.getJSONObject("conversation_config_override"));
                }
            }

            boolean sent = webSocket.send(config.toString());

            if (sent) {
                Log.d("ElevenLabsConfig", "Initial configuration sent successfully");
                if (conversationInitData != null) {
                    Log.d("ElevenLabsConfig", "Included conversation initiation data: " +
                            conversationInitData.toString());
                }
            } else {
                Log.e("ElevenLabsConfig", "Failed to send initial configuration");
            }

        } catch (JSONException e) {
            Log.e("ElevenLabsConfig", "Error building configuration: " + e.getMessage());
        }
    }

    // Convenience method for backward compatibility
    public static void sendInitialConfiguration(WebSocket webSocket, String agentId) {
        sendInitialConfiguration(webSocket, agentId, null);
    }


    /**
     * Send conversation initiation (alternative method)
     * Some ElevenLabs setups may require this format instead
     */
    public static void sendConversationInitiation(WebSocket webSocket, String agentId) {
        try {
            JSONObject initMessage = new JSONObject();
            initMessage.put("type", "conversation_initiation");

            JSONObject agentConfig = new JSONObject();
            agentConfig.put("agent_id", agentId);

            initMessage.put("agent_config", agentConfig);

            boolean sent = webSocket.send(initMessage.toString());

            if (sent) {
                Log.d(TAG, "✅ Conversation initiation sent");
            } else {
                Log.e(TAG, "❌ Failed to send conversation initiation");
            }

        } catch (JSONException e) {
            Log.e(TAG, "❌ Error creating conversation initiation: " + e.getMessage());
        }
    }

    /**
     * Handle different types of messages from ElevenLabs
     */
    public static void handleElevenLabsMessage(JSONObject message, MessageHandler handler) {
        try {
            String type = message.optString("type");

            Log.v(TAG, "📩 ElevenLabs Message: " + type);

            switch (type) {
                case "audio":
                    handleAudioMessage(message, handler);
                    break;

                case "agent_response":
                    handleAgentResponse(message, handler);
                    break;

                case "agent_response_audio_transcript":
                    handleAgentTranscript(message, handler);
                    break;

                case "user_transcript":
                    handleUserTranscript(message, handler);
                    break;

                case "ping":
                    handlePing(message, handler);
                    break;

                case "conversation_initiation_metadata":
                    handleConversationMetadata(message, handler);
                    break;

                case "error":
                    handleError(message, handler);
                    break;

                case "session_created":
                    handleSessionCreated(message, handler);
                    break;

                default:
                    Log.d(TAG, "🔄 Unhandled message type: " + type);
                    Log.v(TAG, "📄 Raw message: " + message.toString());
                    break;
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling ElevenLabs message: " + e.getMessage());
        }
    }

    private static void handleAudioMessage(JSONObject message, MessageHandler handler) {
        try {
            // Method 1: Direct base64 audio
            if (message.has("audio_base_64")) {
                String base64Audio = message.getString("audio_base_64");
                byte[] audioData = Base64.getDecoder().decode(base64Audio);
                handler.onAudioReceived(audioData);
                return;
            }

            // Method 2: Audio event structure
            if (message.has("audio_event")) {
                JSONObject audioEvent = message.getJSONObject("audio_event");
                String base64Audio = audioEvent.optString("audio_base_64", "");

                if (!base64Audio.isEmpty()) {
                    byte[] audioData = Base64.getDecoder().decode(base64Audio);
                    handler.onAudioReceived(audioData);
                }
                return;
            }

            // Method 3: Nested audio data
            if (message.has("audio")) {
                JSONObject audio = message.getJSONObject("audio");
                String base64Audio = audio.optString("data", "");

                if (!base64Audio.isEmpty()) {
                    byte[] audioData = Base64.getDecoder().decode(base64Audio);
                    handler.onAudioReceived(audioData);
                }
                return;
            }

            Log.w(TAG, "⚠️ Audio message without recognized audio data format");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling audio message: " + e.getMessage());
        }
    }

    private static void handleAgentResponse(JSONObject message, MessageHandler handler) {
        try {
            String transcript = "";

            // Try different possible field names for transcript
            if (message.has("agent_response")) {
                transcript = message.getString("agent_response");
            } else if (message.has("text")) {
                transcript = message.getString("text");
            } else if (message.has("transcript")) {
                transcript = message.getString("transcript");
            } else if (message.has("agent_response_event")) {
                JSONObject responseEvent = message.getJSONObject("agent_response_event");
                transcript = responseEvent.optString("agent_response", "");
            }

            if (!transcript.isEmpty()) {
                Log.d(TAG, "🗣️ AI Response: '" + transcript + "'");
                handler.onAgentResponse(transcript);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling agent response: " + e.getMessage());
        }
    }

    private static void handleAgentTranscript(JSONObject message, MessageHandler handler) {
        try {
            String transcript = message.optString("transcript", "");
            if (!transcript.isEmpty()) {
                Log.d(TAG, "📝 Agent Transcript: '" + transcript + "'");
                handler.onAgentResponse(transcript);
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling agent transcript: " + e.getMessage());
        }
    }

    private static void handleUserTranscript(JSONObject message, MessageHandler handler) {
        try {
            String transcript = message.optString("transcript", "");
            boolean isFinal = message.optBoolean("is_final", false);

            if (!transcript.isEmpty()) {
                Log.d(TAG, "👤 User Said: '" + transcript + "' (final: " + isFinal + ")");
                handler.onUserTranscript(transcript, isFinal);
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling user transcript: " + e.getMessage());
        }
    }

    private static void handlePing(JSONObject message, MessageHandler handler) {
        try {
            int eventId = 0;

            // Try to get event_id from different possible locations
            if (message.has("event_id")) {
                eventId = message.getInt("event_id");
            } else if (message.has("ping_event")) {
                JSONObject pingEvent = message.getJSONObject("ping_event");
                eventId = pingEvent.optInt("event_id", 0);
            }

            // Send pong response
            JSONObject pongResponse = new JSONObject();
            pongResponse.put("type", "pong");
            if (eventId > 0) {
                pongResponse.put("event_id", eventId);
            }

            handler.onPingReceived(pongResponse);

            Log.v(TAG, "🏓 Pong response prepared (event_id: " + eventId + ")");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling ping: " + e.getMessage());
        }
    }

    private static void handleConversationMetadata(JSONObject message, MessageHandler handler) {
        try {
            Log.d(TAG, "📋 Conversation metadata received");

            if (message.has("conversation_id")) {
                String conversationId = message.getString("conversation_id");
                Log.d(TAG, "🆔 Conversation ID: " + conversationId);
                handler.onConversationMetadata(conversationId);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling conversation metadata: " + e.getMessage());
        }
    }

    private static void handleSessionCreated(JSONObject message, MessageHandler handler) {
        try {
            Log.d(TAG, "🎬 Session created");

            if (message.has("session_id")) {
                String sessionId = message.getString("session_id");
                Log.d(TAG, "🎫 Session ID: " + sessionId);
                handler.onSessionCreated(sessionId);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling session created: " + e.getMessage());
        }
    }

    private static void handleError(JSONObject message, MessageHandler handler) {
        try {
            String errorMessage = message.optString("message", "Unknown error");
            String errorCode = message.optString("code", "");

            Log.e(TAG, "🚨 ElevenLabs Error: " + errorMessage +
                    (errorCode.isEmpty() ? "" : " (Code: " + errorCode + ")"));

            // Check for specific error types
            if (errorMessage.contains("buffer size") || errorMessage.contains("audio format")) {
                Log.w(TAG, "🎵 Audio format error detected - may need to adjust chunk size or format");
            } else if (errorMessage.contains("rate limit") || errorMessage.contains("quota")) {
                Log.w(TAG, "⏰ Rate limit error - may need to slow down streaming");
            } else if (errorMessage.contains("authentication") || errorMessage.contains("unauthorized")) {
                Log.e(TAG, "🔐 Authentication error - check API key and agent ID");
            }

            handler.onError(errorMessage, errorCode);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling error message: " + e.getMessage());
        }
    }

    /**
     * Send end conversation message
     */
    public static void sendEndConversation(WebSocket webSocket) {
        try {
            JSONObject endMessage = new JSONObject();
            endMessage.put("type", "conversation_end");

            boolean sent = webSocket.send(endMessage.toString());

            if (sent) {
                Log.d(TAG, "👋 End conversation message sent");
            } else {
                Log.w(TAG, "⚠️ Failed to send end conversation message");
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error sending end conversation: " + e.getMessage());
        }
    }

    /**
     * Create properly formatted audio chunk message for ElevenLabs
     */
    public static JSONObject createAudioChunkMessage(byte[] audioData) throws JSONException {
        JSONObject message = new JSONObject();
        String base64Audio = Base64.getEncoder().encodeToString(audioData);
        message.put("user_audio_chunk", base64Audio);
        return message;
    }

    /**
     * Interface for handling different message types
     */
    public interface MessageHandler {
        void onAudioReceived(byte[] audioData);
        void onAgentResponse(String transcript);
        void onUserTranscript(String transcript, boolean isFinal);
        void onPingReceived(JSONObject pongResponse);
        void onError(String message, String code);
        void onConversationMetadata(String conversationId);
        void onSessionCreated(String sessionId);
    }

    /**
     * Validate audio chunk before sending
     */
    public static boolean validateAudioChunk(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            Log.w(TAG, "⚠️ Empty audio chunk");
            return false;
        }

        if (audioData.length % 2 != 0) {
            Log.w(TAG, "⚠️ Audio chunk length not even (16-bit PCM requires even byte count)");
            return false;
        }

        // Check for reasonable chunk size (10ms to 1000ms at 16kHz)
        int minSize = 16000 * 2 * 10 / 1000; // 10ms
        int maxSize = 16000 * 2 * 1000 / 1000; // 1000ms

        if (audioData.length < minSize) {
            Log.w(TAG, "⚠️ Audio chunk too small: " + audioData.length + " bytes");
            return false;
        }

        if (audioData.length > maxSize) {
            Log.w(TAG, "⚠️ Audio chunk too large: " + audioData.length + " bytes");
            return false;
        }

        return true;
    }

    /**
     * Get audio chunk duration in milliseconds
     */
    public static long getAudioDurationMs(byte[] audioData) {
        if (audioData == null || audioData.length == 0) return 0;

        // 16-bit PCM, 16kHz, mono
        // Duration = (bytes / 2) / sampleRate * 1000
        return (audioData.length / 2) * 1000L / 16000L;
    }
}