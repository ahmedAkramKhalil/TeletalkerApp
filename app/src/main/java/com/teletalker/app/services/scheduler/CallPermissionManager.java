package com.teletalker.app.services.scheduler;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Manages call permissions for scheduled calls
 */
public class CallPermissionManager {
    private static final String TAG = "CallPermissionManager";
    public static final int CALL_PERMISSION_REQUEST_CODE = 1001;

    /**
     * Check if CALL_PHONE permission is granted
     */
    public static boolean hasCallPermission(Context context) {
        int result = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE);
        boolean granted = result == PackageManager.PERMISSION_GRANTED;
        Log.d(TAG, "CALL_PHONE permission granted: " + granted);
        return granted;
    }

    /**
     * Check if permission was permanently denied
     */
    public static boolean isPermissionPermanentlyDenied(Activity activity) {
        return !ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.CALL_PHONE);
    }

    /**
     * Request CALL_PHONE permission
     */
    public static void requestCallPermission(Activity activity) {
        Log.d(TAG, "Requesting CALL_PHONE permission");

        if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                Manifest.permission.CALL_PHONE)) {
            // Show explanation before requesting
            showPermissionRationale(activity);
        } else {
            // Request permission directly
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.CALL_PHONE},
                    CALL_PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * Show why we need the permission
     */
    private static void showPermissionRationale(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Phone Call Permission Required")
                .setMessage("This app needs permission to make phone calls to execute " +
                        "your scheduled calls automatically.\n\n" +
                        "Without this permission, scheduled calls cannot be made.")
                .setPositiveButton("Grant Permission", (dialog, which) -> {
                    ActivityCompat.requestPermissions(activity,
                            new String[]{Manifest.permission.CALL_PHONE},
                            CALL_PERMISSION_REQUEST_CODE);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Handle permission permanently denied - guide to settings
     */
    public static void handlePermissionDenied(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Permission Required")
                .setMessage("Call permission is required to make scheduled calls. " +
                        "You have denied this permission. Please enable it in Settings.\n\n" +
                        "Settings → Apps → " + activity.getApplicationInfo().loadLabel(
                        activity.getPackageManager()) + " → Permissions → Phone")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    openAppSettings(activity);
                })
                .setNegativeButton("Cancel", null)
                .setCancelable(false)
                .show();
    }

    /**
     * Open app settings
     */
    public static void openAppSettings(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        activity.startActivity(intent);
    }

    /**
     * Get all required permissions for scheduled calls
     */
    public static String[] getAllRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            return new String[]{
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_PHONE_STATE
            };
        }
    }

    /**
     * Check if all required permissions are granted
     */
    public static boolean hasAllRequiredPermissions(Context context) {
        for (String permission : getAllRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Missing permission: " + permission);
                return false;
            }
        }
        return true;
    }

    /**
     * Request all required permissions
     */
    public static void requestAllPermissions(Activity activity) {
        ActivityCompat.requestPermissions(activity,
                getAllRequiredPermissions(),
                CALL_PERMISSION_REQUEST_CODE);
    }

    /**
     * Log current permission status for debugging
     */
    public static void logPermissionStatus(Context context) {
        Log.d(TAG, "=== PERMISSION STATUS ===");
        for (String permission : getAllRequiredPermissions()) {
            boolean granted = ContextCompat.checkSelfPermission(context, permission)
                    == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, permission + ": " + (granted ? "GRANTED" : "DENIED"));
        }
        Log.d(TAG, "========================");
    }
}