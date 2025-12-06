package com.teletalker.app.subscription;


import android.util.Log;


import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class PaddleApiClient {

    private static final String TAG = "PaddleApiClient";
    private static Retrofit retrofit = null;

    public static Retrofit getClient() {
        if (retrofit == null) {

            // Create logging interceptor
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(
                    message -> Log.d(TAG, message)
            );
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

            // Build OkHttp client with auth and logging
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(new AuthInterceptor())
                    .addInterceptor(loggingInterceptor)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();

            // Build Retrofit instance
            retrofit = new Retrofit.Builder()
                    .baseUrl(PaddleConfig.API_URL + "/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            Log.d(TAG, "Paddle API Client initialized");
            Log.d(TAG, "Base URL: " + PaddleConfig.API_URL);
        }

        return retrofit;
    }

    /**
     * Interceptor to add Authorization header to all requests
     */
    private static class AuthInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request originalRequest = chain.request();

            // Build authenticated request
            Request authenticatedRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer " + PaddleConfig.getApiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .build();

            Log.d(TAG, "Request: " + authenticatedRequest.method() + " " + authenticatedRequest.url());

            return chain.proceed(authenticatedRequest);
        }
    }
}