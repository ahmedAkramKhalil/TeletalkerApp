package com.teletalker.app.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Thin REST client for ElevenLabs ConvAI endpoints — currently just the
 * agents-list endpoint used by the booking-agent picker.
 *
 * Independent from the WebSocket pipeline (which uses its own OkHttp setup
 * tuned for streaming). API calls run off the main thread; callbacks are
 * dispatched on the main thread so callers can update UI directly.
 */
public final class ElevenLabsApiClient {

    private static final String TAG = "ElevenLabsApiClient";
    private static final String BASE_URL = "https://api.elevenlabs.io";

    private final OkHttpClient http = new OkHttpClient();
    private final Handler main = new Handler(Looper.getMainLooper());

    public static final class Agent {
        public final String id;
        public final String name;
        public Agent(String id, String name) { this.id = id; this.name = name; }
    }

    public interface OnAgents {
        void onAgents(@NonNull List<Agent> agents);
        void onError(@NonNull String message);
    }

    /** Simple callback for API key validation. */
    public interface OnValidateKey {
        void onResult(boolean valid, @NonNull String message);
    }

    /**
     * Validate an ElevenLabs API key by calling GET /v1/user.
     * Returns valid=true if the key is accepted (HTTP 200).
     */
    public void validateApiKey(String apiKey, OnValidateKey cb) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            main.post(() -> cb.onResult(false, "API key cannot be empty"));
            return;
        }

        Request req = new Request.Builder()
                .url(BASE_URL + "/v1/user")
                .get()
                .addHeader("xi-api-key", apiKey.trim())
                .addHeader("accept", "application/json")
                .build();

        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                Log.e(TAG, "validateApiKey network failure", e);
                main.post(() -> cb.onResult(false, "Network error: " + e.getMessage()));
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response res) {
                try (Response r = res) {
                    if (r.isSuccessful()) {
                        main.post(() -> cb.onResult(true, "API key is valid"));
                    } else {
                        Log.w(TAG, "validateApiKey HTTP " + r.code());
                        main.post(() -> cb.onResult(false, "Invalid API key (HTTP " + r.code() + ")"));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "validateApiKey error", e);
                    main.post(() -> cb.onResult(false, "Error: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * List the agents accessible to the given API key. Returns up to 100 per
     * call — fine for any realistic merchant account.
     */
    public void listAgents(String apiKey, OnAgents cb) {
        if (apiKey == null || apiKey.isEmpty()) {
            postError(cb, "Missing ElevenLabs API key. Open Voice Engine settings first.");
            return;
        }

        Request req = new Request.Builder()
                .url(BASE_URL + "/v1/convai/agents?page_size=100")
                .get()
                .addHeader("xi-api-key", apiKey)
                .addHeader("accept", "application/json")
                .build();

        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                Log.e(TAG, "listAgents network failure", e);
                postError(cb, "Network error: " + e.getMessage());
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response res) {
                try (Response r = res) {
                    String body = r.body() != null ? r.body().string() : "";
                    if (!r.isSuccessful()) {
                        Log.w(TAG, "listAgents non-2xx (" + r.code() + "): " + body);
                        postError(cb, "ElevenLabs returned HTTP " + r.code());
                        return;
                    }
                    List<Agent> agents = parseAgentsList(body);
                    main.post(() -> cb.onAgents(agents));
                } catch (Exception e) {
                    Log.e(TAG, "listAgents parse failure", e);
                    postError(cb, "Could not parse agent list: " + e.getMessage());
                }
            }
        });
    }

    private void postError(OnAgents cb, String msg) {
        main.post(() -> cb.onError(msg));
    }

    /**
     * The response shape evolved over the years. We handle both:
     *   { "agents": [ { "agent_id": "...", "name": "..." } ] }
     *   { "agents": [ { "id": "...", "name": "..." } ] }
     */
    private static List<Agent> parseAgentsList(String body) throws Exception {
        List<Agent> out = new ArrayList<>();
        JSONObject root = new JSONObject(body);
        JSONArray arr = root.optJSONArray("agents");
        if (arr == null) arr = root.optJSONArray("items");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject a = arr.optJSONObject(i);
            if (a == null) continue;
            String id = a.optString("agent_id", a.optString("id", ""));
            String name = a.optString("name", id);
            if (!id.isEmpty()) out.add(new Agent(id, name));
        }
        return out;
    }
}
