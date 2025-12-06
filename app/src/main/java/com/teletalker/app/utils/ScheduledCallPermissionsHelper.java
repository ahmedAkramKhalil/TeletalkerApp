package com.teletalker.app.utils;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Helper class to manage permissions required for scheduled calls
 */
public class ScheduledCallPermissionsHelper {
    private static final String TAG = "ScheduledCallPerms";

    public static final int REQUEST_CODE_CALL_PHONE = 1001;
    public static final int REQUEST_CODE_EXACT_ALARM = 1002;
    public static final int REQUEST_CODE_ALL_PERMISSIONS = 1003;

    /**
     * Check if all required permissions are granted
     */
    public static boolean hasAllPermissions(Context context) {
        return hasCallPhonePermission(context) &&
                hasExactAlarmPermission(context);
    }

    /**
     * Check CALL_PHONE permission
     */
    public static boolean hasCallPhonePermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Check SCHEDULE_EXACT_ALARM permission (Android 12+)
     */
    public static boolean hasExactAlarmPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            return alarmManager != null && alarmManager.canScheduleExactAlarms();
        }
        return true; // Not required on older versions
    }

    /**
     * Request CALL_PHONE permission
     */
    public static void requestCallPhonePermission(Activity activity) {
        ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.CALL_PHONE},
                REQUEST_CODE_CALL_PHONE
        );
    }

    /**
     * Request SCHEDULE_EXACT_ALARM permission (Android 12+)
     */
    public static void requestExactAlarmPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivityForResult(intent, REQUEST_CODE_EXACT_ALARM);
        }
    }

    /**
     * Request all required permissions at once
     */
    public static void requestAllPermissions(Activity activity) {
        Log.d(TAG, "Requesting all scheduled call permissions");

        // Check what we need to request
        boolean needsCallPhone = !hasCallPhonePermission(activity);
        boolean needsExactAlarm = !hasExactAlarmPermission(activity);

        if (needsCallPhone) {
            requestCallPhonePermission(activity);
        }

        if (needsExactAlarm) {
            requestExactAlarmPermission(activity);
        }

        if (!needsCallPhone && !needsExactAlarm) {
            Log.d(TAG, "All permissions already granted");
        }
    }

    /**
     * Show explanation dialog before requesting permissions
     */
    public static void showPermissionExplanation(Activity activity, PermissionExplanationCallback callback) {
        new androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle("Permissions Required")
                .setMessage("To schedule and make calls automatically, TeleTalker needs:\n\n" +
                        "• CALL_PHONE - To make outbound calls\n" +
                        "• SCHEDULE_EXACT_ALARM - To trigger calls at exact scheduled times\n\n" +
                        "These permissions are necessary for the scheduled calls feature to work.")
                .setPositiveButton("Grant Permissions", (dialog, which) -> {
                    if (callback != null) callback.onUserAccepted();
                    requestAllPermissions(activity);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    if (callback != null) callback.onUserDeclined();
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Check permissions before scheduling a call
     */
    public static boolean checkPermissionsForScheduledCall(Activity activity,
                                                           PermissionCheckCallback callback) {
        if (hasAllPermissions(activity)) {
            if (callback != null) callback.onPermissionsGranted();
            return true;
        } else {
            if (callback != null) callback.onPermissionsMissing();

            // Show explanation and request
            showPermissionExplanation(activity, new PermissionExplanationCallback() {
                @Override
                public void onUserAccepted() {
                    // User accepted, permissions will be requested
                }

                @Override
                public void onUserDeclined() {
                    callback.onPermissionsDenied();
                }
            });

            return false;
        }
    }

    /**
     * Handle permission request result
     */
    public static void handlePermissionResult(int requestCode, String[] permissions,
                                              int[] grantResults,
                                              PermissionResultCallback callback) {
        if (requestCode == REQUEST_CODE_CALL_PHONE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "CALL_PHONE permission granted");
                if (callback != null) callback.onCallPhoneGranted();
            } else {
                Log.w(TAG, "CALL_PHONE permission denied");
                if (callback != null) callback.onCallPhoneDenied();
            }
        }
    }

    // Callback interfaces
    public interface PermissionExplanationCallback {
        void onUserAccepted();
        void onUserDeclined();
    }

    public interface PermissionCheckCallback {
        void onPermissionsGranted();
        void onPermissionsMissing();
        void onPermissionsDenied();
    }

    public interface PermissionResultCallback {
        void onCallPhoneGranted();
        void onCallPhoneDenied();
        void onExactAlarmGranted();
        void onExactAlarmDenied();
    }
}