package com.teletalker.app.services;


import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.teletalker.app.services.CallDetector;

/**
 * Manages app services including CallDetector and other background services
 */
public class ServiceManager {
    private static final String TAG = "ServiceManager";

    private final Context context;
    private boolean isCallDetectorRunning = false;

    public ServiceManager(Context context) {
        this.context = context;
    }

    /**
     * Start the CallDetector foreground service
     */
    public void startCallDetectorService() {
        if (isCallDetectorRunning) {
            Log.d(TAG, "CallDetector service already running");
            return;
        }

        try {
            Intent serviceIntent = new Intent(context, CallDetector.class);

            // For Android 8.0+ (API 26+), must use startForegroundService
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startService(serviceIntent);
                Log.d(TAG, "Started CallDetector as foreground service");
            } else {
                context.startService(serviceIntent);
                Log.d(TAG, "Started CallDetector as regular service");
            }

            isCallDetectorRunning = true;
            showServiceStartedToast();

        } catch (Exception e) {
            Log.e(TAG, "Failed to start CallDetector service: " + e.getMessage());
            showServiceErrorToast(e.getMessage());
        }
    }

    /**
     * Stop the CallDetector service
     */
    public void stopCallDetectorService() {
        if (!isCallDetectorRunning) {
            Log.d(TAG, "CallDetector service not running");
            return;
        }

        try {
            Intent serviceIntent = new Intent(context, CallDetector.class);
            context.stopService(serviceIntent);

            isCallDetectorRunning = false;
            Log.d(TAG, "Stopped CallDetector service");

            Toast.makeText(context, "AI Call Assistant stopped", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "Failed to stop CallDetector service: " + e.getMessage());
        }
    }

    /**
     * Restart the CallDetector service
     */
    public void restartCallDetectorService() {
        Log.d(TAG, "Restarting CallDetector service");
        stopCallDetectorService();

        // Small delay to ensure service is fully stopped
        new android.os.Handler().postDelayed(() -> {
            startCallDetectorService();
        }, 1000);
    }

    /**
     * Check if CallDetector service is running
     */
    public boolean isCallDetectorRunning() {
        return isCallDetectorRunning;
    }

    /**
     * Start all required services
     */
    public void startAllServices() {
        Log.d(TAG, "Starting all required services");
        startCallDetectorService();

        // Add other services here if needed
        // startOtherService();
    }

    /**
     * Stop all services
     */
    public void stopAllServices() {
        Log.d(TAG, "Stopping all services");
        stopCallDetectorService();

        // Stop other services here if needed
        // stopOtherService();
    }

    /**
     * Show success toast when service starts
     */
    private void showServiceStartedToast() {
        Toast.makeText(context, "AI Call Assistant service started", Toast.LENGTH_SHORT).show();
    }

    /**
     * Show error toast when service fails to start
     */
    private void showServiceErrorToast(String error) {
        Toast.makeText(context,
                "Failed to start AI assistant: " + error,
                Toast.LENGTH_LONG).show();
    }

    /**
     * Get service status information
     */
    public ServiceStatus getServiceStatus() {
        return new ServiceStatus(isCallDetectorRunning);
    }

    /**
     * Service status data class
     */
    public static class ServiceStatus {
        private final boolean callDetectorRunning;

        public ServiceStatus(boolean callDetectorRunning) {
            this.callDetectorRunning = callDetectorRunning;
        }

        public boolean isCallDetectorRunning() {
            return callDetectorRunning;
        }

        public boolean areAllServicesRunning() {
            return callDetectorRunning;
            // Add other service checks here
        }

        public String getStatusSummary() {
            StringBuilder status = new StringBuilder();
            status.append("CallDetector: ").append(callDetectorRunning ? "Running" : "Stopped");

            // Add other services status here
            // status.append("\nOtherService: ").append(otherServiceRunning ? "Running" : "Stopped");

            return status.toString();
        }
    }
}