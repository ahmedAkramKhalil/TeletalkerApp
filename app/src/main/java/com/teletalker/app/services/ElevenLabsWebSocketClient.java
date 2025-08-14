package com.teletalker.app.services;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ElevenLabsWebSocketClient {
    private static final String TAG = "ElevenLabsWS";
    private static final String WEBSOCKET_URL = "wss://api.elevenlabs.io/v1/convai/conversation";

    // Replace with your actual agent ID
    private static final String AGENT_ID = "agent_01jygb7h3aez9t265gdxnj1z8b";

    // Replace with your actual API key (only for private agents)
    private static final String API_KEY = "sk_75a50e8295302914451d5a7832860febcf118da4de87e9a7";


    private WebSocket webSocket;
    private Context context;
    private ConversationCallback callback;
    private boolean isConnected = false;
    private String conversationId;

    public ElevenLabsWebSocketClient(Context context) {
        this.context = context;
    }

    public void connect(ConversationCallback callback) {
        this.callback = callback;

        // Check internet connectivity first
        if (!isNetworkAvailable()) {
            Log.e(TAG, "No internet connection available");
            if (callback != null) {
                callback.onError(new Exception("No internet connection"));
            }
            return;
        }

        try {
            String url = WEBSOCKET_URL + "?agent_id=" + AGENT_ID;

            WebSocketFactory factory = new WebSocketFactory();
            factory.setConnectionTimeout(15000); // Increased timeout

            // Add DNS timeout and retry settings
            factory.setSocketTimeout(10000);

            webSocket = factory.createSocket(url);

            // Add authorization header for private agents
            if (API_KEY != null && !API_KEY.isEmpty() && !API_KEY.equals("YOUR_ELEVENLABS_API_KEY")) {
                webSocket.addHeader("Authorization", "Bearer " + API_KEY);
            }

            webSocket.addListener(new WebSocketAdapter() {
                @Override
                public void onConnected(WebSocket websocket, java.util.Map<String, java.util.List<String>> headers) {
                    Log.d(TAG, "WebSocket connected successfully");
                    isConnected = true;
                    if (callback != null) {
                        callback.onConnected();
                    }
                }

                @Override
                public void onTextMessage(WebSocket websocket, String text) {
                    Log.d(TAG, "Received message: " + text);
                    handleMessage(text);
                }

                @Override
                public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame,
                                           WebSocketFrame clientCloseFrame, boolean closedByServer) {
                    Log.w(TAG, "WebSocket disconnected. Server close: " + closedByServer);
                    isConnected = false;
                    if (callback != null) {
                        callback.onDisconnected();
                    }
                }

                @Override
                public void onError(WebSocket websocket, WebSocketException cause) {
                    Log.e(TAG, "WebSocket connection error: " + cause.getMessage());
                    isConnected = false;

                    // Check specific error types
                    if (cause.getMessage().contains("Unable to resolve host")) {
                        Log.e(TAG, "DNS resolution failed - check internet connection or DNS settings");
                    } else if (cause.getMessage().contains("timeout")) {
                        Log.e(TAG, "Connection timeout - check network speed");
                    }

                    if (callback != null) {
                        callback.onError(cause);
                    }
                }

                @Override
                public void onConnectError(WebSocket websocket, WebSocketException exception) {
                    Log.e(TAG, "WebSocket connect error: " + exception.getMessage());
                    if (callback != null) {
                        callback.onError(exception);
                    }
                }
            });

            Log.d(TAG, "Attempting to connect to: " + url);
            // Connect asynchronously with retry
            webSocket.connectAsynchronously();

        } catch (IOException e) {
            Log.e(TAG, "Failed to create WebSocket: " + e.getMessage());
            if (callback != null) {
                callback.onError(e);
            }
        }
    }

    private boolean isNetworkAvailable() {
        try {
            android.net.ConnectivityManager connectivityManager =
                    (android.net.ConnectivityManager) context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);

            if (connectivityManager != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    android.net.Network network = connectivityManager.getActiveNetwork();
                    if (network == null) return true; // Assume connected if can't check

                    android.net.NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                    return capabilities != null && (
                            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
                    );
                } else {
                    android.net.NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                    return activeNetworkInfo != null && activeNetworkInfo.isConnected();
                }
            }
            return true; // Assume connected if ConnectivityManager is null
        } catch (SecurityException e) {
            Log.w(TAG, "No ACCESS_NETWORK_STATE permission, assuming network is available");
            return true; // Assume connected if permission denied
        } catch (Exception e) {
            Log.e(TAG, "Error checking network availability: " + e.getMessage());
            return true; // Assume connected if other error
        }
    }

    public void sendAudio(byte[] audioData) {
        if (!isConnected || webSocket == null) {
            Log.w(TAG, "WebSocket not connected, cannot send audio");
            return;
        }

        try {
            // Convert audio to base64
            String audioBase64 = Base64.encodeToString(audioData, Base64.NO_WRAP);

            // Create audio message JSON
            JSONObject message = new JSONObject();
            message.put("type", "audio");

            JSONObject audioEvent = new JSONObject();
            audioEvent.put("audio_base_64", audioBase64);
            message.put("audio_event", audioEvent);

            Log.d(TAG, "Sending audio data, size: " + audioData.length + " bytes");
            webSocket.sendText(message.toString());

        } catch (JSONException e) {
            Log.e(TAG, "Failed to create audio message: " + e.getMessage());
        }
    }

    public void sendPong(int eventId) {
        if (!isConnected || webSocket == null) {
            return;
        }

        try {
            JSONObject message = new JSONObject();
            message.put("type", "pong");

            JSONObject pongEvent = new JSONObject();
            pongEvent.put("event_id", eventId);
            message.put("pong_event", pongEvent);

            webSocket.sendText(message.toString());

        } catch (JSONException e) {
            Log.e(TAG, "Failed to send pong: " + e.getMessage());
        }
    }

    public void sendContextualUpdate(String text) {
        if (!isConnected || webSocket == null) {
            return;
        }

        try {
            JSONObject message = new JSONObject();
            message.put("type", "contextual_update");
            message.put("text", text);

            webSocket.sendText(message.toString());

        } catch (JSONException e) {
            Log.e(TAG, "Failed to send contextual update: " + e.getMessage());
        }
    }

    private void handleMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String type = json.getString("type");

            switch (type) {
                case "conversation_initiation_metadata":
                    handleInitiationMetadata(json);
                    break;

                case "audio":
                    handleAudioResponse(json);
                    break;

                case "agent_response":
                    handleAgentResponse(json);
                    break;

                case "interruption":
                    handleInterruption(json);
                    break;

                case "ping":
                    handlePing(json);
                    break;

                case "user_transcript":
                    handleUserTranscript(json);
                    break;

                case "agent_response_correction":
                    handleAgentResponseCorrection(json);
                    break;

                default:
                    Log.d(TAG, "Unknown message type: " + type);
                    break;
            }

        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse message: " + e.getMessage());
        }
    }

    private void handleInitiationMetadata(JSONObject json) {
        try {
            JSONObject metadata = json.getJSONObject("conversation_initiation_metadata_event");
            conversationId = metadata.getString("conversation_id");
            String audioFormat = metadata.getString("agent_output_audio_format");

            Log.d(TAG, "Conversation initiated: " + conversationId + ", format: " + audioFormat);

            if (callback != null) {
                callback.onConversationStarted(conversationId, audioFormat);
            }

        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse initiation metadata: " + e.getMessage());
        }
    }

    private void handleAudioResponse(JSONObject json) {
        try {
            JSONObject audioEvent = json.getJSONObject("audio_event");
            String audioBase64 = audioEvent.getString("audio_base_64");
            int eventId = audioEvent.optInt("event_id", -1);

            // Decode base64 audio
            byte[] audioData = Base64.decode(audioBase64, Base64.DEFAULT);

            Log.d(TAG, "Received audio response, size: " + audioData.length + " bytes");

            if (callback != null) {
                callback.onAudioReceived(audioData, eventId);
            }

        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse audio response: " + e.getMessage());
        }
    }

    private void handleAgentResponse(JSONObject json) {
        try {
            JSONObject responseEvent = json.getJSONObject("agent_response_event");
            String agentResponse = responseEvent.getString("agent_response");

            Log.d(TAG, "Agent response: " + agentResponse);

            if (callback != null) {
                callback.onAgentResponse(agentResponse);
            }

        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse agent response: " + e.getMessage());
        }
    }

    private void handleInterruption(JSONObject json) {
        Log.d(TAG, "Interruption detected");
        if (callback != null) {
            callback.onInterruption();
        }
    }

    private void handlePing(JSONObject json) {
        try {
            JSONObject pingEvent = json.getJSONObject("ping_event");
            int eventId = pingEvent.getInt("event_id");

            // Send pong response
            sendPong(eventId);

        } catch (JSONException e) {
            Log.e(TAG, "Failed to handle ping: " + e.getMessage());
        }
    }

    private void handleUserTranscript(JSONObject json) {
        try {
            JSONObject transcriptEvent = json.getJSONObject("user_transcript_event");
            String transcript = transcriptEvent.getString("user_transcript");

            Log.d(TAG, "User transcript: " + transcript);

            if (callback != null) {
                callback.onUserTranscript(transcript);
            }

        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse user transcript: " + e.getMessage());
        }
    }

    private void handleAgentResponseCorrection(JSONObject json) {
        try {
            JSONObject correctionEvent = json.getJSONObject("agent_response_correction_event");
            String correctedResponse = correctionEvent.getString("corrected_agent_response");

            Log.d(TAG, "Agent response corrected: " + correctedResponse);

            if (callback != null) {
                callback.onAgentResponseCorrection(correctedResponse);
            }

        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse agent response correction: " + e.getMessage());
        }
    }

    public void disconnect() {
        if (webSocket != null) {
            isConnected = false;
            webSocket.disconnect();
            webSocket = null;
        }
    }

    public boolean isConnected() {
        return isConnected && webSocket != null && webSocket.isOpen();
    }

    public String getConversationId() {
        return conversationId;
    }

    // Callback interface for WebSocket events
    public interface ConversationCallback {
        void onConnected();
        void onDisconnected();
        void onError(Exception error);
        void onConversationStarted(String conversationId, String audioFormat);
        void onAudioReceived(byte[] audioData, int eventId);
        void onAgentResponse(String response);
        void onUserTranscript(String transcript);
        void onAgentResponseCorrection(String correctedResponse);
        void onInterruption();
    }
}