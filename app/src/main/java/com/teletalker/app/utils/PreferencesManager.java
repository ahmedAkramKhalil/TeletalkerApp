package com.teletalker.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;


import com.teletalker.app.features.agent_type.Agent;

import org.json.JSONException;
import org.json.JSONObject;

public class PreferencesManager {

    public static final String PREF_AI_MODE = "PREF_AI_MODE";
    private static final String PREFS_NAME = "TeleTalkerPrefs";
    public static final String PREF_INJECTION_ENABLED = "injection_enabled";

    // Keys
    public static final String API_KEY = "api_key";
    public static final String SELECTED_AGENT = "selected_agent";
    public static final String SELECTED_AGENT_ID = "selected_agent_id";
    public static final String SELECTED_AGENT_TYPE = "selected_agent_type";
    public static final String SELECTED_AGENT_LANGUAGE = "selected_agent_language";
    public static final String SELECTED_AGENT_DESCRIPTION = "selected_agent_description";
    public static final String SELECTED_AGENT_AVATAR_URL = "selected_agent_avatar_url";
    public static final String SELECTED_AGENT_CAPABILITIES = "selected_agent_capabilities";
    public static final String SELECTED_AGENT_VERSION = "selected_agent_version";
    public static final String SELECTED_AGENT_IS_ACTIVE = "selected_agent_is_active";
    public static final String SELECTED_AGENT_JSON = "selected_agent_json";
    public static final String IS_BOT_ACTIVE = "IS_BOT_ACTIVE";




    public static final String PREF_CALL_RECORDING_ENABLED = "call_recording_enabled";
    public static final String PREF_AUTO_ANSWER_ENABLED = "auto_answer_enabled";
    public static final String PREF_AUTO_ANSWER_DELAY = "auto_answer_delay";
//    public static final String PREF_AUTO_ANSWER_SPEAKER = "auto_answer_speaker";



    public static final String USER_TOKEN = "user_token";
    public static final String USER_ID = "user_id";
    public static final String IS_FIRST_TIME = "is_first_time";
    public static final String LAST_AGENT_SYNC = "last_agent_sync";

    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;
    private static PreferencesManager instance;

    private PreferencesManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    public static synchronized PreferencesManager getInstance(Context context) {
        if (instance == null) {
            instance = new PreferencesManager(context.getApplicationContext());
        }
        return instance;
    }

    public boolean isAutoAnswerEnabled(){
        return prefs.getBoolean(PREF_AUTO_ANSWER_ENABLED, true);
    }

    // Generic methods
    public void saveString(String key, String value) {
        editor.putString(key, value);
        editor.apply();
    }

    public String getString(String key, String defaultValue) {
        return prefs.getString(key, defaultValue);
    }

    public void saveInt(String key, int value) {
        editor.putInt(key, value);
        editor.apply();
    }

    public void setIsBotActive(boolean botActive){
        saveBoolean(IS_BOT_ACTIVE,botActive);
    }

    public boolean isBotActive(){
        return getBoolean(IS_BOT_ACTIVE,false);
    }
    public int getInt(String key, int defaultValue) {
        return prefs.getInt(key, defaultValue);
    }

    public void saveBoolean(String key, boolean value) {
        editor.putBoolean(key, value);
        editor.apply();
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }

    public void saveLong(String key, long value) {
        editor.putLong(key, value);
        editor.apply();
    }

    public long getLong(String key, long defaultValue) {
        return prefs.getLong(key, defaultValue);
    }

    public void remove(String key) {
        editor.remove(key);
        editor.apply();
    }

    public void clearAll() {
        editor.clear();
        editor.apply();
    }

    // Enhanced Agent-specific methods with full details
    public void saveSelectedAgent(String agentId, String agentName, String agentType, String agentLanguage) {
        editor.putString(SELECTED_AGENT_ID, agentId);
        editor.putString(SELECTED_AGENT, agentName);
        editor.putString(SELECTED_AGENT_TYPE, agentType);
        editor.putString(SELECTED_AGENT_LANGUAGE, agentLanguage);
        editor.putLong(LAST_AGENT_SYNC, System.currentTimeMillis());
        editor.apply();

        Log.d("Agent Detailes","SELECTED_AGENT_ID=" + agentId);
        Log.d("Agent Detailes","agentName=" +agentName );
        Log.d("Agent Detailes","agentType=" + agentType);
        Log.d("Agent Detailes","agentLanguage=" + agentLanguage);


    }

    // Save complete agent with all details
//    public void saveSelectedAgentComplete(Agent agent) {
//        editor.putString(SELECTED_AGENT_ID, agent.getId());
//        editor.putString(SELECTED_AGENT, agent.getName());
//        editor.putString(SELECTED_AGENT_TYPE, agent.getType());
//        editor.putString(SELECTED_AGENT_LANGUAGE, agent.getLanguage());
//        editor.putString(SELECTED_AGENT_DESCRIPTION, agent.getDescription());
//        editor.putString(SELECTED_AGENT_AVATAR_URL, agent.getAvatarUrl());
//        editor.putString(SELECTED_AGENT_CAPABILITIES, agent.getCapabilities());
//        editor.putString(SELECTED_AGENT_VERSION, agent.getVersion());
//        editor.putBoolean(SELECTED_AGENT_IS_ACTIVE, agent.isActive());
//        editor.putLong(LAST_AGENT_SYNC, System.currentTimeMillis());
//
//        // Save as JSON for easy retrieval
//        try {
//            JSONObject agentJson = new JSONObject();
//            agentJson.put("id", agent.getId());
//            agentJson.put("name", agent.getName());
//            agentJson.put("type", agent.getType());
//            agentJson.put("language", agent.getLanguage());
//            agentJson.put("description", agent.getDescription());
//            agentJson.put("avatar_url", agent.getAvatarUrl());
//            agentJson.put("capabilities", agent.getCapabilities());
//            agentJson.put("version", agent.getVersion());
//            agentJson.put("is_active", agent.isActive());
//
//            editor.putString(SELECTED_AGENT_JSON, agentJson.toString());
//        } catch (JSONException e) {
//            // JSON save failed, but individual fields are still saved
//        }
//
//        editor.apply();
//    }

    // Get complete agent from preferences
//    public Agent getSelectedAgentComplete() {
//        String agentJson = prefs.getString(SELECTED_AGENT_JSON, null);
//        if (agentJson != null) {
//            try {
//                JSONObject json = new JSONObject(agentJson);
//                return new Agent(
//                        json.optString("id", ""),
//                        json.optString("name", ""),
//                        json.optString("type", ""),
//                        json.optString("language", ""),
//                        json.optString("description", ""),
//                        json.optString("avatar_url", ""),
//                        json.optBoolean("is_active", true),
//                        json.optString("capabilities", ""),
//                        json.optString("version", "")
//                );
//            } catch (JSONException e) {
//                // Fall back to individual fields
//            }
//        }
//
//        // Fallback to individual fields
//        String id = getSelectedAgentId();
//        if (id != null) {
//            return new Agent(
//                    id,
//                    getSelectedAgentName(),
//                    getSelectedAgentType(),
//                    getSelectedAgentLanguage(),
//                    getSelectedAgentDescription(),
//                    getSelectedAgentAvatarUrl(),
//                    getSelectedAgentIsActive(),
//                    getSelectedAgentCapabilities(),
//                    getSelectedAgentVersion()
//            );
//        }
//
//        return null;
//    }

    public String getSelectedAgentId() {
        return prefs.getString(SELECTED_AGENT_ID, null);
    }

    public String getSelectedAgentName() {
        return prefs.getString(SELECTED_AGENT, null);
    }

    public String getSelectedAgentType() {
        return prefs.getString(SELECTED_AGENT_TYPE, null);
    }

    public String getSelectedAgentLanguage() {
        return prefs.getString(SELECTED_AGENT_LANGUAGE, null);
    }

    public String getSelectedAgentDescription() {
        return prefs.getString(SELECTED_AGENT_DESCRIPTION, "");
    }

    public String getSelectedAgentAvatarUrl() {
        return prefs.getString(SELECTED_AGENT_AVATAR_URL, "");
    }

    public String getSelectedAgentCapabilities() {
        return prefs.getString(SELECTED_AGENT_CAPABILITIES, "");
    }

    public String getSelectedAgentVersion() {
        return prefs.getString(SELECTED_AGENT_VERSION, "1.0");
    }

    public boolean getSelectedAgentIsActive() {
        return prefs.getBoolean(SELECTED_AGENT_IS_ACTIVE, true);
    }

    public long getLastAgentSync() {
        return prefs.getLong(LAST_AGENT_SYNC, 0);
    }

    // API methods
    public void saveApiKey(String apiKey) {
        editor.putString(API_KEY, apiKey);
        editor.apply();
    }

    public String getApiKey() {
        return prefs.getString(API_KEY, null);
    }

    // User methods
    public void saveUserToken(String token) {
        editor.putString(USER_TOKEN, token);
        editor.apply();
    }

    public String getUserToken() {
        return prefs.getString(USER_TOKEN, null);
    }

    public void saveUserId(String userId) {
        editor.putString(USER_ID, userId);
        editor.apply();
    }

    public String getUserId() {
        return prefs.getString(USER_ID, null);
    }

    // App state methods
    public void setFirstTime(boolean isFirstTime) {
        editor.putBoolean(IS_FIRST_TIME, isFirstTime);
        editor.apply();
    }

    public boolean isFirstTime() {
        return prefs.getBoolean(IS_FIRST_TIME, true);
    }

    // Check if user is logged in
    public boolean isLoggedIn() {
        return getApiKey() != null && getUserToken() != null;
    }

    // Check if agent is selected and still valid
    public boolean hasValidSelectedAgent() {
        return getSelectedAgentId() != null && getSelectedAgentName() != null;
    }

    // Check if agent data needs refresh (older than 24 hours)
    public boolean needsAgentRefresh() {
        long lastSync = getLastAgentSync();
        long now = System.currentTimeMillis();
        long dayInMillis = 24 * 60 * 60 * 1000; // 24 hours
        return (now - lastSync) > dayInMillis;
    }

    // Clear agent selection
    public void clearSelectedAgent() {
        editor.remove(SELECTED_AGENT_ID);
        editor.remove(SELECTED_AGENT);
        editor.remove(SELECTED_AGENT_TYPE);
        editor.remove(SELECTED_AGENT_LANGUAGE);
        editor.remove(SELECTED_AGENT_DESCRIPTION);
        editor.remove(SELECTED_AGENT_AVATAR_URL);
        editor.remove(SELECTED_AGENT_CAPABILITIES);
        editor.remove(SELECTED_AGENT_VERSION);
        editor.remove(SELECTED_AGENT_IS_ACTIVE);
        editor.remove(SELECTED_AGENT_JSON);
        editor.remove(LAST_AGENT_SYNC);
        editor.apply();
    }

    // Logout - clear all user data
    public void logout() {
        editor.remove(API_KEY);
        editor.remove(USER_TOKEN);
        editor.remove(USER_ID);
        clearSelectedAgent();
        editor.apply();
    }

    // Export agent settings for backup/restore
//    public String exportAgentSettings() {
//        try {
//            JSONObject settings = new JSONObject();
//            settings.put("agent_id", getSelectedAgentId());
//            settings.put("agent_name", getSelectedAgentName());
//            settings.put("agent_type", getSelectedAgentType());
//            settings.put("agent_language", getSelectedAgentLanguage());
//            settings.put("last_sync", getLastAgentSync());
//            return settings.toString();
//        } catch (JSONException e) {
//            return null;
//        }
//    }

    // Import agent settings from backup
//    public boolean importAgentSettings(String settingsJson) {
//        try {
//            JSONObject settings = new JSONObject(settingsJson);
//            editor.putString(SELECTED_AGENT_ID, settings.optString("agent_id"));
//            editor.putString(SELECTED_AGENT, settings.optString("agent_name"));
//            editor.putString(SELECTED_AGENT_TYPE, settings.optString("agent_type"));
//            editor.putString(SELECTED_AGENT_LANGUAGE, settings.optString("agent_language"));
//            editor.putLong(LAST_AGENT_SYNC, settings.optLong("last_sync", 0));
//            editor.apply();
//            return true;
//        } catch (JSONException e) {
//            return false;
//        }
//    }
}