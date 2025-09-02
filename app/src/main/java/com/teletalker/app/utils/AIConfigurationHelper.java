package com.teletalker.app.utils;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.teletalker.app.services.AICallRecorder;
import com.teletalker.app.services.CallDetector;

/**
 * Helper class to configure AI settings for the CallDetector service
 */
public class AIConfigurationHelper {
    private static final String TAG = "AIConfigurationHelper";

    /**
     * Configure AI settings for the CallDetector service
     *
     * @param context Application context
     * @param apiKey ElevenLabs API key
     * @param agentId ElevenLabs Agent ID
     * @param aiMode AI response mode
     * @param enabled Whether AI is enabled
     */
    public static void configureAI(Context context, String apiKey, String agentId,
                                   AICallRecorder.AIMode aiMode, boolean enabled) {
        try {
            // This would typically be done through a bound service or broadcast
            // For now, we'll save to SharedPreferences and the service will pick it up

            context.getSharedPreferences("teletalker_ai_config", Context.MODE_PRIVATE)
                    .edit()
                    .putString("elevenlabs_api_key", apiKey)
                    .putString("agent_id", agentId)
                    .putString("ai_mode", aiMode.name())
                    .putBoolean("ai_enabled", enabled)
                    .apply();

            PreferencesManager preferencesManager = PreferencesManager.getInstance(context);
            preferencesManager.saveApiKey(apiKey);
            preferencesManager.setIsBotActive(enabled);


            Log.d(TAG, "AI configuration saved - Enabled: " + enabled + ", Mode: " + aiMode);

        } catch (Exception e) {
            Log.e(TAG, "Failed to configure AI: " + e.getMessage());
        }
    }

    /**
     * Get current AI configuration
     */
    public static AIConfiguration getCurrentConfig(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("teletalker_ai_config", Context.MODE_PRIVATE);

            String apiKey = prefs.getString("elevenlabs_api_key", null);
            String agentId = prefs.getString("agent_id", null);
            boolean enabled = prefs.getBoolean("ai_enabled", true);

            String aiModeString = prefs.getString("ai_mode", AICallRecorder.AIMode.SMART_ASSISTANT.name());
            AICallRecorder.AIMode aiMode;
            try {
                aiMode = AICallRecorder.AIMode.valueOf(aiModeString);
            } catch (IllegalArgumentException e) {
                aiMode = AICallRecorder.AIMode.SMART_ASSISTANT;
            }

            return new AIConfiguration(apiKey, agentId, aiMode, enabled);

        } catch (Exception e) {
            Log.e(TAG, "Failed to get AI configuration: " + e.getMessage());
            return new AIConfiguration(null, null, AICallRecorder.AIMode.SMART_ASSISTANT, false);
        }
    }

    /**
     * Validate ElevenLabs credentials
     */
    public static boolean validateCredentials(String apiKey, String agentId) {
        return apiKey != null && !apiKey.trim().isEmpty() &&
                agentId != null && !agentId.trim().isEmpty();
    }

    /**
     * Data class for AI configuration
     */
    public static class AIConfiguration {
        public final String apiKey;
        public final String agentId;
        public final AICallRecorder.AIMode aiMode;
        public final boolean enabled;

        public AIConfiguration(String apiKey, String agentId, AICallRecorder.AIMode aiMode, boolean enabled) {
            this.apiKey = apiKey;
            this.agentId = agentId;
            this.aiMode = aiMode;
            this.enabled = enabled;
        }

        public boolean hasValidCredentials() {
            return validateCredentials(apiKey, agentId);
        }

        @Override
        public String toString() {
            return "AIConfiguration{" +
                    "hasCredentials=" + hasValidCredentials() +
                    ", aiMode=" + aiMode +
                    ", enabled=" + enabled +
                    '}';
        }
    }
}