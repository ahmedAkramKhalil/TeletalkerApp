package com.teletalker.app.services.ai;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.WebSocket;

/**
 * Helper class to configure ElevenLabs AI with conversation instructions
 * for scheduled calls
 */
public class ElevenLabsConversationHelper {
    private static final String TAG = "ElevenLabsConvHelper";

    /**
     * Send custom conversation instructions to ElevenLabs AI
     * This should be called after WebSocket connection is established
     *
     * @param webSocket The active WebSocket connection to ElevenLabs
     * @param conversationNotes The instructions for what the AI should say/discuss
     */
    public static void sendConversationInstructions(WebSocket webSocket, String conversationNotes) {
        if (webSocket == null) {
            Log.e(TAG, "Cannot send instructions - WebSocket is null");
            return;
        }

        if (conversationNotes == null || conversationNotes.trim().isEmpty()) {
            Log.w(TAG, "No conversation notes provided");
            return;
        }

        try {
            Log.d(TAG, "📝 Sending conversation instructions to ElevenLabs AI");
            Log.d(TAG, "Instructions: " + conversationNotes);

            // Method 1: Send as a conversation configuration update
            JSONObject configUpdate = new JSONObject();
            configUpdate.put("type", "conversation_config_update");

            JSONObject config = new JSONObject();
            config.put("conversation_context", conversationNotes);
            config.put("initial_message", conversationNotes);
            config.put("system_prompt", "You are a helpful AI assistant. " + conversationNotes);

            configUpdate.put("config", config);

            boolean sent = webSocket.send(configUpdate.toString());

            if (sent) {
                Log.d(TAG, "✅ Conversation instructions sent successfully");
            } else {
                Log.w(TAG, "⚠️ Failed to send conversation instructions");
            }

        } catch (JSONException e) {
            Log.e(TAG, "❌ Error creating conversation instructions: " + e.getMessage());
        }
    }

    /**
     * Send initial system message that the AI will use to guide the conversation
     * This is sent as the first "user" message to set context
     */
    public static void sendInitialContext(WebSocket webSocket, String conversationNotes) {
        if (webSocket == null || conversationNotes == null) {
            return;
        }

        try {
            Log.d(TAG, "🎯 Sending initial context message");

            // Send as a user message to establish conversation context
            JSONObject contextMessage = new JSONObject();
            contextMessage.put("type", "user_message");
            contextMessage.put("message", "[SYSTEM] " + conversationNotes);
            contextMessage.put("is_context", true);

            boolean sent = webSocket.send(contextMessage.toString());

            if (sent) {
                Log.d(TAG, "✅ Initial context sent");
            } else {
                Log.w(TAG, "⚠️ Failed to send initial context");
            }

        } catch (JSONException e) {
            Log.e(TAG, "❌ Error sending initial context: " + e.getMessage());
        }
    }

    /**
     * Update the existing ElevenLabsWebSocketConfig to include conversation notes
     * Add this method to ElevenLabsWebSocketConfig class
     */
    public static void sendInitialConfigurationWithNotes(WebSocket webSocket, String agentId, String conversationNotes) {
        try {
            JSONObject config = new JSONObject();
            config.put("type", "conversation_initiation_client_data");

            // Conversation configuration
            JSONObject conversationConfig = new JSONObject();
            conversationConfig.put("agent_id", agentId);

            // Add conversation notes as system prompt or context
            if (conversationNotes != null && !conversationNotes.isEmpty()) {
                conversationConfig.put("conversation_context", conversationNotes);
                conversationConfig.put("initial_instructions", conversationNotes);
                Log.d(TAG, "📋 Including conversation notes in initial config");
            }

            // Audio input configuration
            JSONObject audioConfig = new JSONObject();
            audioConfig.put("input_sample_rate", 16000);
            audioConfig.put("output_sample_rate", 16000);
            audioConfig.put("input_encoding", "pcm_16000");
            audioConfig.put("output_encoding", "pcm_16000");

            conversationConfig.put("audio_interface", audioConfig);
            config.put("conversation_config", conversationConfig);

            // Send configuration
            boolean sent = webSocket.send(config.toString());

            if (sent) {
                Log.d(TAG, "✅ Initial configuration with notes sent successfully");
                Log.d(TAG, "🎯 Agent ID: " + agentId);
                Log.d(TAG, "📝 Conversation Notes: " + conversationNotes);
            } else {
                Log.e(TAG, "❌ Failed to send initial configuration");
            }

        } catch (JSONException e) {
            Log.e(TAG, "❌ Error creating initial configuration: " + e.getMessage());
        }
    }

    /**
     * Inject conversation notes into an active conversation
     * Useful if the notes need to be updated mid-call
     */
    public static void updateConversationContext(WebSocket webSocket, String newNotes) {
        if (webSocket == null || newNotes == null) {
            return;
        }

        try {
            Log.d(TAG, "🔄 Updating conversation context");

            JSONObject update = new JSONObject();
            update.put("type", "context_update");
            update.put("context", newNotes);
            update.put("timestamp", System.currentTimeMillis());

            boolean sent = webSocket.send(update.toString());

            if (sent) {
                Log.d(TAG, "✅ Context updated");
            }

        } catch (JSONException e) {
            Log.e(TAG, "❌ Error updating context: " + e.getMessage());
        }
    }
}