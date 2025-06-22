package com.teletalker.app.utils;


import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for permission-related operations
 */
public class PermissionUtils {
    private static final String TAG = "PermissionUtils";



    /**
     * Check if a specific permission is granted
     */
    public static boolean isPermissionGranted(Context context, String permission) {
        return ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Get human-readable permission names
     */
    public static String getPermissionDisplayName(String permission) {
        Map<String, String> permissionNames = new HashMap<>();

        // Phone permissions
        permissionNames.put(Manifest.permission.READ_PHONE_STATE, "Phone State");
        permissionNames.put(Manifest.permission.CALL_PHONE, "Make Phone Calls");
        permissionNames.put(Manifest.permission.READ_CALL_LOG, "Call History");
        permissionNames.put(Manifest.permission.ANSWER_PHONE_CALLS, "Answer Calls");

        // Contact permissions
        permissionNames.put(Manifest.permission.READ_CONTACTS, "Contacts");

        // Audio permissions
        permissionNames.put(Manifest.permission.RECORD_AUDIO, "Microphone");

        // Storage permissions
        permissionNames.put(Manifest.permission.READ_EXTERNAL_STORAGE, "Storage Access");
        permissionNames.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, "Storage Write");

        // Media permissions (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionNames.put(Manifest.permission.READ_MEDIA_AUDIO, "Audio Files");
            permissionNames.put(Manifest.permission.READ_MEDIA_IMAGES, "Image Files");
            permissionNames.put(Manifest.permission.READ_MEDIA_VIDEO, "Video Files");
        }

        // Notification permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionNames.put(Manifest.permission.POST_NOTIFICATIONS, "Notifications");
        }

        return permissionNames.getOrDefault(permission, permission.replace("android.permission.", ""));
    }

    /**
     * Get permission category for grouping
     */
    public static String getPermissionCategory(String permission) {
        if (permission.contains("PHONE") || permission.contains("CALL")) {
            return "Phone & Calls";
        } else if (permission.contains("CONTACT")) {
            return "Contacts";
        } else if (permission.contains("AUDIO") || permission.contains("RECORD")) {
            return "Audio & Recording";
        } else if (permission.contains("STORAGE") || permission.contains("MEDIA")) {
            return "Storage & Media";
        } else if (permission.contains("NOTIFICATION")) {
            return "Notifications";
        } else {
            return "Other";
        }
    }

    /**
     * Get explanation for why a permission is needed
     */
    public static String getPermissionExplanation(String permission) {
        Map<String, String> explanations = new HashMap<>();

        explanations.put(Manifest.permission.READ_PHONE_STATE,
                "Required to detect incoming and outgoing calls for AI assistance");
        explanations.put(Manifest.permission.CALL_PHONE,
                "Allows the app to make calls on your behalf when needed");
        explanations.put(Manifest.permission.READ_CALL_LOG,
                "Used to access call history for context and AI learning");
        explanations.put(Manifest.permission.ANSWER_PHONE_CALLS,
                "Enables automatic call handling and AI intervention");
        explanations.put(Manifest.permission.READ_CONTACTS,
                "Provides caller identification and personalized AI responses");
        explanations.put(Manifest.permission.RECORD_AUDIO,
                "Essential for recording conversations for AI processing");
        explanations.put(Manifest.permission.READ_EXTERNAL_STORAGE,
                "Needed to access and save call recordings");
        explanations.put(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                "Required to save call recordings and AI data");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            explanations.put(Manifest.permission.READ_MEDIA_AUDIO,
                    "Access to audio files for playback and processing");
            explanations.put(Manifest.permission.POST_NOTIFICATIONS,
                    "Show notifications about call status and AI activity");
        }

        return explanations.getOrDefault(permission, "Required for app functionality");
    }

    /**
     * Get list of critical permissions that are absolutely required
     */
    public static List<String> getCriticalPermissions() {
        List<String> critical = new ArrayList<>();
        critical.add(Manifest.permission.READ_PHONE_STATE);
        critical.add(Manifest.permission.RECORD_AUDIO);
        critical.add(Manifest.permission.CALL_PHONE);
        return critical;
    }

    /**
     * Check if critical permissions are granted
     */
    public static boolean areCriticalPermissionsGranted(Context context) {
        List<String> critical = getCriticalPermissions();
        for (String permission : critical) {
            if (!isPermissionGranted(context, permission)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get missing critical permissions
     */
    public static List<String> getMissingCriticalPermissions(Context context) {
        List<String> missing = new ArrayList<>();
        List<String> critical = getCriticalPermissions();

        for (String permission : critical) {
            if (!isPermissionGranted(context, permission)) {
                missing.add(permission);
            }
        }

        return missing;
    }

    /**
     * Create user-friendly permission summary
     */
    public static String createPermissionSummary(List<String> permissions) {
        if (permissions.isEmpty()) {
            return "No permissions required";
        }

        StringBuilder summary = new StringBuilder();
        Map<String, List<String>> categorized = categorizePermissions(permissions);

        for (Map.Entry<String, List<String>> entry : categorized.entrySet()) {
            summary.append("• ").append(entry.getKey()).append(": ");

            List<String> categoryPermissions = entry.getValue();
            for (int i = 0; i < categoryPermissions.size(); i++) {
                if (i > 0) summary.append(", ");
                summary.append(getPermissionDisplayName(categoryPermissions.get(i)));
            }
            summary.append("\n");
        }

        return summary.toString();
    }

    /**
     * Group permissions by category
     */
    private static Map<String, List<String>> categorizePermissions(List<String> permissions) {
        Map<String, List<String>> categorized = new HashMap<>();

        for (String permission : permissions) {
            String category = getPermissionCategory(permission);
            categorized.computeIfAbsent(category, k -> new ArrayList<>()).add(permission);
        }

        return categorized;
    }

    /**
     * Check if device supports specific permission
     */
    public static boolean isPermissionSupported(String permission) {
        // Some permissions are only available on certain API levels
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return !permission.equals(Manifest.permission.POST_NOTIFICATIONS) &&
                    !permission.equals(Manifest.permission.READ_MEDIA_AUDIO) &&
                    !permission.equals(Manifest.permission.READ_MEDIA_IMAGES) &&
                    !permission.equals(Manifest.permission.READ_MEDIA_VIDEO);
        }

        return true;
    }
}