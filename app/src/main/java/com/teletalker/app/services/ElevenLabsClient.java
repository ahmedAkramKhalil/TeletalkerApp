package com.teletalker.app.services;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ElevenLabsClient {
    private static final String TAG = "ElevenLabsClient";
    private static final String API_BASE_URL = "https://api.elevenlabs.io/v1";
    private static final String CONVAI_ENDPOINT = "/v1/convai/conversation/stream";

    // Replace with your actual API key
    private static final String API_KEY = "sk_0c231a8a65c81b722ef703dfcf1293a1913a36fd83eb52e5";

    // Replace with your agent ID or conversation ID
    private static final String AGENT_ID = "YOUR_AGENT_ID";

    private OkHttpClient client;
    private Context context;

    public ElevenLabsClient(Context context) {
        this.context = context;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

//    public void sendAudio(byte[] audioData, RealTimeCallProcessor.AudioResponseCallback callback) {
//        Log.d(TAG, "Sending audio to ElevenLabs API, size: " + audioData.length + " bytes");
//
//        RequestBody audioBody = RequestBody.create(
//                MediaType.parse("audio/wav"), audioData);
//
//        MultipartBody.Builder requestBodyBuilder = new MultipartBody.Builder()
//                .setType(MultipartBody.FORM)
//                .addFormDataPart("audio", "audio.wav", audioBody);
//
//        // Add agent ID if using conversational AI
//        if (AGENT_ID != null && !AGENT_ID.isEmpty()) {
//            requestBodyBuilder.addFormDataPart("agent_id", AGENT_ID);
//        }
//
//        RequestBody requestBody = requestBodyBuilder.build();
//
//        Request request = new Request.Builder()
//                .url(API_BASE_URL + CONVAI_ENDPOINT)
//                .addHeader("xi-api-key", API_KEY)
//                .addHeader("Accept", "audio/mpeg")
//                .post(requestBody)
//                .build();
//
//        client.newCall(request).enqueue(new Callback() {
//            @Override
//            public void onResponse(Call call, Response response) throws IOException {
//                if (response.isSuccessful()) {
//                    ResponseBody responseBody = response.body();
//                    if (responseBody != null) {
//                        byte[] responseAudio = responseBody.bytes();
//                        Log.d(TAG, "Received audio response, size: " + responseAudio.length + " bytes");
//                        callback.onAudioReceived(responseAudio);
//                    } else {
//                        Log.e(TAG, "Response body is null");
//                        callback.onError(new Exception("Empty response body"));
//                    }
//                } else {
//                    String errorBody = "";
//                    if (response.body() != null) {
//                        errorBody = response.body().string();
//                    }
//                    Log.e(TAG, "API request failed: " + response.code() + " - " + errorBody);
//                    callback.onError(new Exception("API request failed: " + response.code() + " - " + errorBody));
//                }
//                response.close();
//            }
//
//            @Override
//            public void onFailure(Call call, IOException e) {
//                Log.e(TAG, "Network request failed: " + e.getMessage());
//                callback.onError(e);
//            }
//        });
//    }

    // Alternative method for text-to-speech if you want to send text instead of audio
//    public void sendText(String text, RealTimeCallProcessor.AudioResponseCallback callback) {
//        Log.d(TAG, "Sending text to ElevenLabs TTS: " + text);
//
//        RequestBody requestBody = new MultipartBody.Builder()
//                .setType(MultipartBody.FORM)
//                .addFormDataPart("text", text)
//                .addFormDataPart("voice_id", "YOUR_VOICE_ID") // Replace with actual voice ID
//                .build();
//
//        Request request = new Request.Builder()
//                .url(API_BASE_URL + "/text-to-speech/YOUR_VOICE_ID")
//                .addHeader("xi-api-key", API_KEY)
//                .addHeader("Accept", "audio/mpeg")
//                .post(requestBody)
//                .build();
//
//        client.newCall(request).enqueue(new Callback() {
//            @Override
//            public void onResponse(Call call, Response response) throws IOException {
//                if (response.isSuccessful()) {
//                    ResponseBody responseBody = response.body();
//                    if (responseBody != null) {
//                        byte[] responseAudio = responseBody.bytes();
//                        Log.d(TAG, "Received TTS audio response, size: " + responseAudio.length + " bytes");
//                        callback.onAudioReceived(responseAudio);
//                    } else {
//                        callback.onError(new Exception("Empty response body"));
//                    }
//                } else {
//                    String errorBody = "";
//                    if (response.body() != null) {
//                        errorBody = response.body().string();
//                    }
//                    Log.e(TAG, "TTS request failed: " + response.code() + " - " + errorBody);
//                    callback.onError(new Exception("TTS request failed: " + response.code()));
//                }
//                response.close();
//            }
//
//            @Override
//            public void onFailure(Call call, IOException e) {
//                Log.e(TAG, "TTS network request failed: " + e.getMessage());
//                callback.onError(e);
//            }
//        });
//    }

    // Method to test API connectivity
    public void testConnection(ConnectionTestCallback callback) {
        Request request = new Request.Builder()
                .url(API_BASE_URL + "/user")
                .addHeader("xi-api-key", API_KEY)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.d(TAG, "API connection test successful");
                    callback.onSuccess();
                } else {
                    Log.e(TAG, "API connection test failed: " + response.code());
                    callback.onFailure("Connection failed: " + response.code());
                }
                response.close();
            }

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "API connection test failed: " + e.getMessage());
                callback.onFailure("Network error: " + e.getMessage());
            }
        });
    }

    public interface ConnectionTestCallback {
        void onSuccess();
        void onFailure(String error);
    }
}