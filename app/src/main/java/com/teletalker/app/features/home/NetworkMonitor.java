package com.teletalker.app.features.home;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

public class NetworkMonitor {
    private static final String TAG = "NetworkMonitor";

    public interface NetworkStatusListener {
        void onNetworkStatusChanged(StatusModel.InternetStatus status);
    }

    private final Context context;
    private final NetworkStatusListener listener;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Handler handler;

    public NetworkMonitor(Context context, NetworkStatusListener listener) {
        this.context = context;
        this.listener = listener;
        this.handler = new Handler(Looper.getMainLooper());
        setupNetworkMonitoring();
    }

    private void setupNetworkMonitoring() {
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                checkConnection();
            }

            @Override
            public void onLost(@NonNull Network network) {
                handler.post(() -> listener.onNetworkStatusChanged(StatusModel.InternetStatus.DISCONNECTED));
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
                checkConnection();
            }
        };

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        connectivityManager.registerNetworkCallback(request, networkCallback);
        checkConnection();
    }

    private void checkConnection() {
        new Thread(() -> {
            StatusModel.InternetStatus status = getCurrentStatus();
            handler.post(() -> listener.onNetworkStatusChanged(status));
        }).start();
    }

    private StatusModel.InternetStatus getCurrentStatus() {
        if (connectivityManager == null) {
            return StatusModel.InternetStatus.DISCONNECTED;
        }

        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return StatusModel.InternetStatus.DISCONNECTED;
        }

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) {
            return StatusModel.InternetStatus.DISCONNECTED;
        }

        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            return StatusModel.InternetStatus.DISCONNECTED;
        }

        // Check for poor connection on cellular
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            int signalStrength = capabilities.getSignalStrength();
            if (signalStrength < -85) {
                return StatusModel.InternetStatus.POOR;
            }
        }

        return StatusModel.InternetStatus.CONNECTED;
    }

    public void cleanup() {
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
    }
}
