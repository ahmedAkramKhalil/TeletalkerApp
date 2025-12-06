package com.teletalker.app.utils;

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

public class PermissionManager {
    private static final String TAG = "PermissionManager";
    private static final int REQUEST_PERMISSIONS_CODE = 1001;

    private final Activity activity;
    private PermissionCallback callback;

    // Define critical permissions that app cannot work without
    private static final List<String> CRITICAL_PERMISSIONS = Arrays.asList(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE
    );

    public interface PermissionCallback {
        void onPermissionsGranted();
        void onPermissionsMissing(List<String> missingPermissions);
        void onPermissionsDenied(List<String> deniedPermissions);
    }

    public PermissionManager(Activity activity) {
        this.activity = activity;
    }

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

    private List<String> getAllRequiredPermissions() {
        List<String> permissions = new ArrayList<>();

        // Core permissions - all in one list
        permissions.add(Manifest.permission.RECORD_AUDIO);
        permissions.add(Manifest.permission.CALL_PHONE);
        permissions.add(Manifest.permission.READ_PHONE_STATE);
        permissions.add(Manifest.permission.READ_CONTACTS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.SCHEDULE_EXACT_ALARM);
        }
//        permissions.add(Manifest.permission.READ_CALL_LOG);
//        permissions.add(Manifest.permission.WRITE_CALL_LOG); // ADD THIS for default dialer
        permissions.add(Manifest.permission.BIND_INCALL_SERVICE);

        // Add ANSWER_PHONE_CALLS for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions.add(Manifest.permission.ANSWER_PHONE_CALLS);

        }

        // Add READ_CALL_LOG if needed
//        permissions.add(Manifest.permission.READ_CALL_LOG);

        // Storage permissions based on API level
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.USE_EXACT_ALARM);
            permissions.add(Manifest.permission.SCHEDULE_EXACT_ALARM);

            permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        return permissions;
    }

    private List<String> getMissingPermissions(List<String> requiredPermissions) {
        List<String> missingPermissions = new ArrayList<>();

        for (String permission : requiredPermissions) {
            if (ActivityCompat.checkSelfPermission(activity, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        return missingPermissions;
    }

    private void requestPermissions(List<String> permissions) {
        Log.d(TAG, "Requesting " + permissions.size() + " permissions at once");

        ActivityCompat.requestPermissions(
                activity,
                permissions.toArray(new String[0]),
                REQUEST_PERMISSIONS_CODE
        );
    }

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
     * Check if minimum critical permissions are granted
     */
    public boolean hasMinimumRequiredPermissions() {
        for (String permission : CRITICAL_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(activity, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public boolean areAllPermissionsGranted() {
        return getMissingPermissions(getAllRequiredPermissions()).isEmpty();
    }
}