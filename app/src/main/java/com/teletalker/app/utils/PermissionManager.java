package com.teletalker.app.features.home;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages all app permissions including runtime and media permissions
 */
public class PermissionManager {
    private static final String TAG = "PermissionManager";
    private static final int REQUEST_PERMISSIONS_CODE = 1001;

    private final Activity activity;
    private PermissionCallback callback;

    public interface PermissionCallback {
        void onPermissionsGranted();
        void onPermissionsMissing(List<String> missingPermissions);
        void onPermissionsDenied(List<String> deniedPermissions);
    }

    public PermissionManager(Activity activity) {
        this.activity = activity;
    }

    /**
     * Check and request all required permissions
     */
    public void checkAndRequestAllPermissions(PermissionCallback callback) {
        this.callback = callback;

        List<String> allRequiredPermissions = getAllRequiredPermissions();
        List<String> missingPermissions = getMissingPermissions(allRequiredPermissions);

        if (missingPermissions.isEmpty()) {
            Log.d(TAG, "✅ All permissions already granted");
            callback.onPermissionsGranted();
        } else {
            Log.d(TAG, "⚠️ Missing " + missingPermissions.size() + " permissions");
            callback.onPermissionsMissing(missingPermissions);
            requestPermissions(missingPermissions);
        }
    }

    /**
     * Get all required permissions based on device API level
     */
    private List<String> getAllRequiredPermissions() {
        List<String> permissions = new ArrayList<>();

        // Add core permissions
        permissions.addAll(getCorePermissions());

        // Add storage permissions (API level dependent)
        permissions.addAll(getStoragePermissions());

        // Add notification permissions (API level dependent)
        permissions.addAll(getNotificationPermissions());

        return permissions;
    }

    /**
     * Core phone and audio permissions
     */
    private List<String> getCorePermissions() {
        return Arrays.asList(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.POST_NOTIFICATIONS
        );
    }

    /**
     * Storage permissions based on API level
     */
    private List<String> getStoragePermissions() {
        List<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+) - Use granular media permissions
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11-12 (API 30-32) - Still use READ_EXTERNAL_STORAGE
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        } else {
            // Android 10 and below (API 29 and below)
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        return permissions;
    }

    /**
     * Notification permissions for newer Android versions
     */
    private List<String> getNotificationPermissions() {
        List<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        return permissions;
    }

    /**
     * Check which permissions are missing
     */
    private List<String> getMissingPermissions(List<String> requiredPermissions) {
        List<String> missingPermissions = new ArrayList<>();

        for (String permission : requiredPermissions) {
            if (ActivityCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        return missingPermissions;
    }

    /**
     * Request the missing permissions
     */
    private void requestPermissions(List<String> permissions) {
        Log.d(TAG, "Requesting " + permissions.size() + " permissions");

        ActivityCompat.requestPermissions(
                activity,
                permissions.toArray(new String[0]),
                REQUEST_PERMISSIONS_CODE
        );
    }

    /**
     * Handle permission request result
     */
    public void handlePermissionResult(String[] permissions, int[] grantResults) {
        if (callback == null) {
            Log.w(TAG, "No callback set for permission result");
            return;
        }

        List<String> deniedPermissions = new ArrayList<>();
        int grantedCount = 0;

        for (int i = 0; i < permissions.length; i++) {
            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                grantedCount++;
                Log.d(TAG, "✅ Granted: " + permissions[i]);
            } else {
                deniedPermissions.add(permissions[i]);
                Log.w(TAG, "❌ Denied: " + permissions[i]);
            }
        }

        Log.d(TAG, "Permission result: " + grantedCount + "/" + permissions.length + " granted");

        if (deniedPermissions.isEmpty()) {
            callback.onPermissionsGranted();
        } else {
            callback.onPermissionsDenied(deniedPermissions);
        }
    }

    /**
     * Get permission status map
     */
    public Map<String, Boolean> getPermissionStatus() {
        Map<String, Boolean> statusMap = new HashMap<>();
        List<String> allPermissions = getAllRequiredPermissions();

        for (String permission : allPermissions) {
            boolean isGranted = ActivityCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED;
            statusMap.put(permission, isGranted);
        }

        return statusMap;
    }

    /**
     * Check if all permissions are granted
     */
    public boolean areAllPermissionsGranted() {
        return getMissingPermissions(getAllRequiredPermissions()).isEmpty();
    }

    /**
     * Check specific permission categories
     */
    public boolean areCorePermissionsGranted() {
        return getMissingPermissions(getCorePermissions()).isEmpty();
    }

    public boolean areStoragePermissionsGranted() {
        return getMissingPermissions(getStoragePermissions()).isEmpty();
    }

    public boolean areNotificationPermissionsGranted() {
        return getMissingPermissions(getNotificationPermissions()).isEmpty();
    }
}