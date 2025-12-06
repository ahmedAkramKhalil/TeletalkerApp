package com.teletalker.app.services.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

// Enhanced WebSocket management with better stability
public class WebSocketConnectionManager {
    private static final String TAG = "WebSocketManager";

    // Connection parameters
    private static final int MAX_RECONNECTION_ATTEMPTS = 8;
    private static final long INITIAL_RECONNECT_DELAY = 1000L;
    private static final long MAX_RECONNECT_DELAY = 30000L;
    private static final long PING_INTERVAL = 15000L;
    private static final long CONNECTION_TIMEOUT = 20000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger connectionAttempts = new AtomicInteger(0);
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicLong lastPongReceived = new AtomicLong(0);

    private volatile boolean shouldReconnect = true;
    private WebSocket currentWebSocket;
    private OkHttpClient httpClient;
    private ConnectionCallback callback; // FIXED: Added callback field

    public interface ConnectionCallback {
        void onConnected();
        void onDisconnected();
        void onConnectionFailed(String reason);
        void onMessage(JSONObject message);
        void onAudioMessage(byte[] audioData);
    }

    public WebSocketConnectionManager() {
        setupHttpClient();
        startConnectionHealthMonitoring();
    }

    // FIXED: Added missing callback methods
    public void setCallback(ConnectionCallback callback) {
        this.callback = callback;
    }

    public ConnectionCallback getCallback() {
        return this.callback;
    }

    private void setupHttpClient() {
        httpClient = new OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .connectTimeout(20, TimeUnit.SECONDS)
                .pingInterval(PING_INTERVAL, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public void connect(String url, String apiKey, String agentId, ConnectionCallback callback) {
        this.callback = callback;
        shouldReconnect = true;
        connectWithRetry(url, apiKey, agentId);
    }

    private void connectWithRetry(String url, String apiKey, String agentId) {
        if (!shouldReconnect || isConnecting.get()) {
            return;
        }

        isConnecting.set(true);
        int attempt = connectionAttempts.incrementAndGet();

        Log.d(TAG, "Connection attempt " + attempt + "/" + MAX_RECONNECTION_ATTEMPTS);

        // Exponential backoff with jitter
        long delay = Math.min(INITIAL_RECONNECT_DELAY * (1L << (attempt - 1)), MAX_RECONNECT_DELAY);
        delay += (long) (Math.random() * 1000);

        if (attempt > 1) {
            mainHandler.postDelayed(() -> attemptConnection(url, apiKey, agentId), delay);
        } else {
            attemptConnection(url, apiKey, agentId);
        }
    }

    private void attemptConnection(String url, String apiKey, String agentId) {
        try {
            String wsUrl = url + "?agent_id=" + agentId;

            Request request = new Request.Builder()
                    .url(wsUrl)
                    .addHeader("xi-api-key", apiKey)
                    .addHeader("Connection", "Upgrade")
                    .addHeader("Upgrade", "websocket")
                    .addHeader("Sec-WebSocket-Version", "13")
                    .addHeader("User-Agent", "TeleTalker-AI/2.0")
                    .build();

            currentWebSocket = httpClient.newWebSocket(request, new EnhancedWebSocketListener());

            // Set connection timeout
            mainHandler.postDelayed(() -> {
                if (isConnecting.get() && !isConnected.get()) {
                    Log.w(TAG, "Connection timeout");
                    handleConnectionFailure("Connection timeout");
                }
            }, CONNECTION_TIMEOUT);

        } catch (Exception e) {
            Log.e(TAG, "Connection attempt failed: " + e.getMessage());
            handleConnectionFailure(e.getMessage());
        }
    }

    private void startConnectionHealthMonitoring() {
        Runnable healthCheck = new Runnable() {
            @Override
            public void run() {
                if (isConnected.get()) {
                    long timeSinceLastPong = System.currentTimeMillis() - lastPongReceived.get();

                    if (timeSinceLastPong > PING_INTERVAL * 2) {
                        Log.w(TAG, "No pong received for " + timeSinceLastPong + "ms");
                        handleConnectionFailure("Ping timeout");
                        return;
                    }

                    sendPing();
                }

                mainHandler.postDelayed(this, PING_INTERVAL);
            }
        };

        mainHandler.postDelayed(healthCheck, PING_INTERVAL);
    }

    private void sendPing() {
        if (currentWebSocket != null && isConnected.get()) {
            try {
                JSONObject ping = new JSONObject();
                ping.put("type", "ping");
                ping.put("timestamp", System.currentTimeMillis());
                currentWebSocket.send(ping.toString());
            } catch (Exception e) {
                Log.e(TAG, "Failed to send ping: " + e.getMessage());
            }
        }
    }

    private class EnhancedWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.d(TAG, "WebSocket connected - Code: " + response.code());

            isConnected.set(true);
            isConnecting.set(false);
            connectionAttempts.set(0);
            lastPongReceived.set(System.currentTimeMillis());

            if (callback != null) {
                callback.onConnected();
            }

            // Send initial configuration
            ElevenLabsWebSocketConfig.sendInitialConfiguration(webSocket, ""); // Will need agent ID
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                JSONObject message = new JSONObject(text);
                String type = message.optString("type");

                if ("pong".equals(type)) {
                    lastPongReceived.set(System.currentTimeMillis());
                    Log.v(TAG, "Pong received");
                    return;
                }

                if ("ping".equals(type)) {
                    handlePingRequest(webSocket, message);
                    return;
                }

                if (callback != null) {
                    callback.onMessage(message);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error parsing message: " + e.getMessage());
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            if (callback != null) {
                callback.onAudioMessage(bytes.toByteArray());
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            Log.e(TAG, "WebSocket failure: " + t.getMessage());

            String errorDetails = "";
            if (response != null) {
                errorDetails = " (Code: " + response.code() + ")";
            }

            handleConnectionFailure(t.getMessage() + errorDetails);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.d(TAG, "WebSocket closed: " + code + " - " + reason);

            isConnected.set(false);
            isConnecting.set(false);

            if (callback != null) {
                callback.onDisconnected();
            }

            if (shouldReconnect && code != 1000) {
                handleConnectionFailure("Unexpected closure: " + reason);
            }
        }
    }

    private void handlePingRequest(WebSocket webSocket, JSONObject pingMessage) {
        try {
            JSONObject pong = new JSONObject();
            pong.put("type", "pong");

            if (pingMessage.has("event_id")) {
                pong.put("event_id", pingMessage.getInt("event_id"));
            }

            webSocket.send(pong.toString());
            Log.v(TAG, "Pong sent");

        } catch (Exception e) {
            Log.e(TAG, "Failed to send pong: " + e.getMessage());
        }
    }

    private void handleConnectionFailure(String reason) {
        isConnected.set(false);
        isConnecting.set(false);

        if (currentWebSocket != null) {
            currentWebSocket.close(1000, "Reconnecting");
            currentWebSocket = null;
        }

        if (callback != null) {
            callback.onConnectionFailed(reason);
        }

        if (shouldReconnect && connectionAttempts.get() < MAX_RECONNECTION_ATTEMPTS) {
            Log.d(TAG, "Scheduling reconnection...");
            connectWithRetry("", "", ""); // URL/credentials should be stored
        } else {
            Log.e(TAG, "Max reconnection attempts reached");
            connectionAttempts.set(0);
        }
    }

    public boolean sendMessage(String message) {
        if (currentWebSocket != null && isConnected.get()) {
            return currentWebSocket.send(message);
        }
        return false;
    }

    public void disconnect() {
        shouldReconnect = false;
        isConnected.set(false);
        isConnecting.set(false);

        if (currentWebSocket != null) {
            currentWebSocket.close(1000, "Normal closure");
            currentWebSocket = null;
        }
    }

    public boolean isConnected() {
        return isConnected.get();
    }
}