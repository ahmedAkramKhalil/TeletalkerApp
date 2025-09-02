package com.teletalker.app.network;

import com.teletalker.app.features.agent_type.AgentTypeActivity.Agent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiService {

    private static final String BASE_URL = "https://api.elevenlabs.io/v1/convai/"; // Replace with your actual API URL
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static ApiService instance;
    private OkHttpClient httpClient;

    private ApiService() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized ApiService getInstance() {
        if (instance == null) {
            instance = new ApiService();
        }
        return instance;
    }

    // Generic callback interface
    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    // Get agents from API with detailed information
    public void getAgents(String apiKey, ApiCallback<List<Agent>> callback) {
        String url = BASE_URL + "agents";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("xi-api-key", apiKey)  // Changed from "Authorization: Bearer"
                .addHeader("Content-Type", "application/json")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        List<Agent> agents = parseAgentsResponse(responseBody);
                        callback.onSuccess(agents);
                    } catch (JSONException e) {
                        callback.onError("Failed to parse response: " + e.getMessage());
                    }
                } else {
                    callback.onError("API error: " + response.code() + " " + response.message());
                }
            }
        });
    }

    // Select agent via API
    public void selectAgent(String apiKey, String agentId, ApiCallback<Boolean> callback) {
        String url = BASE_URL + "agents/" + agentId + "/select";

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("agent_id", agentId);
            jsonBody.put("timestamp", System.currentTimeMillis());
        } catch (JSONException e) {
            callback.onError("Failed to create request: " + e.getMessage());
            return;
        }

        RequestBody requestBody = RequestBody.create(jsonBody.toString(), JSON);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError("Failed to select agent: " + response.code() + " " + response.message());
                }
            }
        });
    }

    // Update user preferences via API with detailed agent information
    public void updateUserPreferences(String apiKey, Agent selectedAgent, ApiCallback<Boolean> callback) {
        String url = BASE_URL + "user/preferences";

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("selected_agent_id", selectedAgent.getId());
            jsonBody.put("selected_agent_name", selectedAgent.getName());
            jsonBody.put("selected_agent_type", selectedAgent.getType());
            jsonBody.put("preferred_language", selectedAgent.getLanguage());
            jsonBody.put("agent_capabilities", selectedAgent.getCapabilities());
            jsonBody.put("agent_version", selectedAgent.getVersion());
            jsonBody.put("updated_at", System.currentTimeMillis());
        } catch (JSONException e) {
            callback.onError("Failed to create request: " + e.getMessage());
            return;
        }

        RequestBody requestBody = RequestBody.create(jsonBody.toString(), JSON);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .put(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    callback.onSuccess(true);
                } else {
                    callback.onError("Failed to update preferences: " + response.code() + " " + response.message());
                }
            }
        });
    }

    private List<Agent> parseAgentsResponse(String responseBody) throws JSONException {
        List<Agent> agents = new ArrayList<>();

        JSONObject jsonResponse = new JSONObject(responseBody);

        // Handle different possible response formats
        JSONArray agentsArray;
        if (jsonResponse.has("agents")) {
            agentsArray = jsonResponse.getJSONArray("agents");
        } else if (jsonResponse.has("data")) {
            agentsArray = jsonResponse.getJSONArray("data");
        } else {
            // Assume the response itself is an array
            agentsArray = new JSONArray(responseBody);
        }

        for (int i = 0; i < agentsArray.length(); i++) {
            JSONObject agentObj = agentsArray.getJSONObject(i);

            // Create agent with full details
            Agent agent = new Agent(
                    agentObj.optString("id", ""),
                    agentObj.optString("name", "Unknown Agent"),
                    agentObj.optString("type", "unknown"),
                    agentObj.optString("language", "en"),
                    agentObj.optString("description", ""),
                    agentObj.optString("avatar_url", ""),
                    agentObj.optBoolean("is_active", true),
                    agentObj.optString("capabilities", ""),
                    agentObj.optString("version", "1.0")
            );

            agents.add(agent);
        }

        return agents;
    }

    // Get agent details by ID
    public void getAgentDetails(String apiKey, String agentId, ApiCallback<Agent> callback) {
        String url = BASE_URL + "agents/" + agentId;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        JSONObject agentObj = new JSONObject(responseBody);

                        Agent agent = new Agent(
                                agentObj.optString("id", ""),
                                agentObj.optString("name", "Unknown Agent"),
                                agentObj.optString("type", "unknown"),
                                agentObj.optString("language", "en"),
                                agentObj.optString("description", ""),
                                agentObj.optString("avatar_url", ""),
                                agentObj.optBoolean("is_active", true),
                                agentObj.optString("capabilities", ""),
                                agentObj.optString("version", "1.0")
                        );

                        callback.onSuccess(agent);
                    } catch (JSONException e) {
                        callback.onError("Failed to parse agent details: " + e.getMessage());
                    }
                } else {
                    callback.onError("Failed to get agent details: " + response.code() + " " + response.message());
                }
            }
        });
    }

    // Generic GET request
    public void get(String endpoint, String apiKey, ApiCallback<String> callback) {
        String url = BASE_URL + endpoint;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body().string());
                } else {
                    callback.onError("API error: " + response.code() + " " + response.message());
                }
            }
        });
    }

    public void shutdown() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
        }
    }
}