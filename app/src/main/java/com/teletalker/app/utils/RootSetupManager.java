package com.teletalker.app.utils;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages root permission setup for system-level features
 * Follows the same pattern as PermissionManager
 */
public class RootSetupManager {
    private static final String TAG = "RootSetupManager";

    private WeakReference<Activity> activityRef;
    private ProgressDialog progressDialog;
    private RootSetupCallback callback;
    private Handler mainHandler;

    // System permissions that require root to grant
    private static final List<String> SYSTEM_PERMISSIONS = Arrays.asList(
            "android.permission.CAPTURE_AUDIO_OUTPUT",
            "android.permission.CONTROL_INCALL_EXPERIENCE",
            "android.permission.MODIFY_PHONE_STATE",
            "android.permission.CALL_PRIVILEGED"
    );

    public interface RootSetupCallback {
        void onRootSetupCompleted(boolean success, int grantedCount, int totalCount);
        void onRootSetupFailed(String reason);
    }

    public RootSetupManager(Activity activity) {
        this.activityRef = new WeakReference<>(activity);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Start root setup process
     * Similar to PermissionManager.checkAndRequestAllPermissions()
     */
    public void startRootSetup(RootSetupCallback callback) {
        this.callback = callback;

        if (!isActivityValid()) {
            Log.w(TAG, "Activity is not valid, cannot start root setup");
            notifySetupFailed("Activity is not available");
            return;
        }

        Log.d(TAG, "Starting root setup process");

        // Show progress dialog on main thread
        mainHandler.post(this::showProgressDialog);

        // Step 1: Request root access
        RootPermissionManager.requestRootAccess(new RootPermissionManager.RootPermissionCallback() {
            @Override
            public void onRootAccessGranted() {
                Log.d(TAG, "✅ Root access granted - requesting system permissions");

                // Step 2: Grant all system permissions
                grantAllSystemPermissions();
            }

            @Override
            public void onRootAccessDenied(String reason) {
                Log.e(TAG, "❌ Root access denied: " + reason);
                dismissProgressDialogSafe();
                notifySetupFailed("Root access denied: " + reason);
            }

            @Override
            public void onPermissionGranted(String permission) {
                // Not used in this callback
            }

            @Override
            public void onPermissionDenied(String permission) {
                // Not used in this callback
            }
        });
    }

    /**
     * Grant all system permissions using root
     */
    private void grantAllSystemPermissions() {
        Activity activity = activityRef.get();
        if (activity == null) {
            Log.e(TAG, "Activity is null, cannot grant permissions");
            dismissProgressDialogSafe();
            notifySetupFailed("Activity not available");
            return;
        }

        final int totalPermissions = SYSTEM_PERMISSIONS.size();
        final AtomicInteger grantedCount = new AtomicInteger(0);
        final AtomicInteger completedCount = new AtomicInteger(0);
        final List<String> failedPermissions = new ArrayList<>();

        Log.d(TAG, "Attempting to grant " + totalPermissions + " system permissions");

        // Grant each permission
        for (String permission : SYSTEM_PERMISSIONS) {
            Log.d(TAG, "Attempting to grant permission: " + permission);

            RootPermissionManager.grantRootPermission(
                    activity,
                    permission,
                    new RootPermissionManager.RootPermissionCallback() {
                        @Override
                        public void onRootAccessGranted() {
                            // Not used in this callback
                        }

                        @Override
                        public void onRootAccessDenied(String reason) {
                            // Not used in this callback
                        }

                        @Override
                        public void onPermissionGranted(String perm) {
                            Log.d(TAG, "✅ Granted: " + perm);
                            grantedCount.incrementAndGet();
                            checkCompletion(completedCount, totalPermissions, grantedCount, failedPermissions);
                        }

                        @Override
                        public void onPermissionDenied(String perm) {
                            Log.w(TAG, "❌ Denied: " + perm);
                            failedPermissions.add(perm);
                            checkCompletion(completedCount, totalPermissions, grantedCount, failedPermissions);
                        }
                    }
            );
        }
    }

    /**
     * Check if all permission grant attempts are complete
     */
    private void checkCompletion(AtomicInteger completedCount, int totalPermissions,
                                 AtomicInteger grantedCount, List<String> failedPermissions) {
        int completed = completedCount.incrementAndGet();

        Log.d(TAG, "Permission progress: " + completed + "/" + totalPermissions);

        // Check if all permissions have been processed
        if (completed >= totalPermissions) {
            int granted = grantedCount.get();
            boolean success = granted == totalPermissions;

            Log.d(TAG, "Root setup completed: " + granted + "/" + totalPermissions + " granted");

            if (!failedPermissions.isEmpty()) {
                Log.w(TAG, "Failed permissions: " + failedPermissions);
            }

            dismissProgressDialogSafe();
            notifySetupCompleted(success, granted, totalPermissions);
        }
    }

    // ============ DIALOG MANAGEMENT ============

    private void showProgressDialog() {
        if (!isActivityValid()) {
            Log.w(TAG, "Activity invalid, skipping progress dialog");
            return;
        }

        Activity activity = activityRef.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            Log.w(TAG, "Activity not available for dialog");
            return;
        }

        try {
            // Dismiss any existing dialog first
            dismissProgressDialog();

            progressDialog = new ProgressDialog(activity);
            progressDialog.setMessage("Configuring root features...");
            progressDialog.setCancelable(false);
            progressDialog.show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing progress dialog", e);
        }
    }

    private void dismissProgressDialog() {
        try {
            if (progressDialog != null && progressDialog.isShowing()) {
                Activity activity = activityRef != null ? activityRef.get() : null;
                if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                    progressDialog.dismiss();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error dismissing progress dialog", e);
        } finally {
            progressDialog = null;
        }
    }

    private void dismissProgressDialogSafe() {
        if (mainHandler != null) {
            mainHandler.post(this::dismissProgressDialog);
        }
    }

    public void dismissAllDialogs() {
        dismissProgressDialog();
    }

    // ============ LIFECYCLE MANAGEMENT ============

    public void cleanup() {
        Log.d(TAG, "Cleaning up RootSetupManager");

        dismissAllDialogs();

        if (activityRef != null) {
            activityRef.clear();
            activityRef = null;
        }

        callback = null;
        mainHandler = null;
    }

    private boolean isActivityValid() {
        if (activityRef == null) return false;

        Activity activity = activityRef.get();
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    // ============ CALLBACK NOTIFICATIONS ============

    private void notifySetupCompleted(boolean success, int grantedCount, int totalCount) {
        if (callback == null || mainHandler == null) return;

        mainHandler.post(() -> {
            if (callback != null && isActivityValid()) {
                callback.onRootSetupCompleted(success, grantedCount, totalCount);
            }
        });
    }

    private void notifySetupFailed(String reason) {
        if (callback == null || mainHandler == null) return;

        mainHandler.post(() -> {
            if (callback != null && isActivityValid()) {
                callback.onRootSetupFailed(reason);
            }
        });
    }

    // ============ PUBLIC UTILITY METHODS ============

    /**
     * Check if device is rooted
     */
    public boolean isDeviceRooted() {
        return RootPermissionManager.isDeviceRooted();
    }

    /**
     * Get list of system permissions that will be granted
     */
    public List<String> getSystemPermissions() {
        return new ArrayList<>(SYSTEM_PERMISSIONS);
    }
}
