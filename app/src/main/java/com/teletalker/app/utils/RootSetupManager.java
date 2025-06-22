package com.teletalker.app.utils;


import android.app.Activity;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import com.teletalker.app.utils.RootPermissionManager;

/**
 * Manages root permission setup and system-level permission granting
 */
public class RootSetupManager {
    private static final String TAG = "RootSetupManager";

    private final Activity activity;
    private RootSetupCallback callback;
    private AlertDialog progressDialog;

    // Root permissions that we want to try to grant
    private static final String[] ROOT_PERMISSIONS = {
            "android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.CAPTURE_AUDIO_OUTPUT"
    };

    public interface RootSetupCallback {
        void onRootSetupCompleted(boolean success, int grantedCount, int totalCount);
        void onRootSetupFailed(String reason);
    }

    public RootSetupManager(Activity activity) {
        this.activity = activity;
    }

    /**
     * Start the root setup process
     */
    public void startRootSetup(RootSetupCallback callback) {
        this.callback = callback;

        if (!RootPermissionManager.isDeviceRooted()) {
            if (callback != null) {
                callback.onRootSetupFailed("Device is not rooted");
            }
            return;
        }

        Log.d(TAG, "Starting root setup process");
        showProgressDialog();
        requestRootAccess();
    }

    /**
     * Show progress dialog during root setup
     */
    private void showProgressDialog() {
        progressDialog = new AlertDialog.Builder(activity)
                .setTitle("🔐 Requesting Root Access")
                .setMessage("Please grant root access when prompted...\n\nThis may take a few seconds.")
                .setCancelable(false)
                .show();
    }

    /**
     * Request root access from the user
     */
    private void requestRootAccess() {
        RootPermissionManager.requestRootAccess(activity, new RootPermissionManager.RootPermissionCallback() {
            @Override
            public void onRootAccessGranted() {
                activity.runOnUiThread(() -> {
                    Log.d(TAG, "✅ Root access granted - requesting system permissions");
                    updateProgressDialog("Root access granted!\n\nGranting system permissions...");
                    grantSystemPermissions();
                });
            }

            @Override
            public void onRootAccessDenied(String reason) {
                activity.runOnUiThread(() -> {
                    dismissProgressDialog();
                    Log.w(TAG, "❌ Root access denied: " + reason);

                    if (callback != null) {
                        callback.onRootSetupFailed("Root access denied: " + reason);
                    }
                });
            }

            @Override
            public void onPermissionGranted(String permission) {
                // Handled in grantSystemPermissions
            }

            @Override
            public void onPermissionDenied(String permission) {
                // Handled in grantSystemPermissions
            }
        });
    }

    /**
     * Grant system-level permissions using root
     */
    private void grantSystemPermissions() {
        final PermissionGrantTracker tracker = new PermissionGrantTracker(ROOT_PERMISSIONS.length);

        for (String permission : ROOT_PERMISSIONS) {
            RootPermissionManager.grantRootPermission(activity, permission, new RootPermissionManager.RootPermissionCallback() {
                @Override
                public void onRootAccessGranted() {
                    // Not used here
                }

                @Override
                public void onRootAccessDenied(String reason) {
                    // Not used here
                }

                @Override
                public void onPermissionGranted(String perm) {
                    activity.runOnUiThread(() -> {
                        tracker.onPermissionGranted(perm);
                        Log.d(TAG, "✅ Granted: " + perm + " (" + tracker.getCompletedCount() + "/" + tracker.getTotalCount() + ")");

                        updateProgressDialog("Root access granted!\n\nGranted " +
                                tracker.getCompletedCount() + "/" + tracker.getTotalCount() + " permissions...");

                        if (tracker.isComplete()) {
                            onAllRootPermissionsComplete(tracker);
                        }
                    });
                }

                @Override
                public void onPermissionDenied(String perm) {
                    activity.runOnUiThread(() -> {
                        tracker.onPermissionDenied(perm);
                        Log.w(TAG, "❌ Failed: " + perm);

                        if (tracker.isComplete()) {
                            onAllRootPermissionsComplete(tracker);
                        }
                    });
                }
            });
        }
    }

    /**
     * Handle completion of all root permission requests
     */
    private void onAllRootPermissionsComplete(PermissionGrantTracker tracker) {
        dismissProgressDialog();

        boolean success = tracker.getGrantedCount() == tracker.getTotalCount();

        Log.d(TAG, "Root permission setup complete: " + tracker.getGrantedCount() + "/" + tracker.getTotalCount() + " granted");

        if (callback != null) {
            callback.onRootSetupCompleted(success, tracker.getGrantedCount(), tracker.getTotalCount());
        }
    }

    /**
     * Update progress dialog message
     */
    private void updateProgressDialog(String message) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.setMessage(message);
        }
    }

    /**
     * Dismiss progress dialog
     */
    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    /**
     * Helper class to track permission granting progress
     */
    private static class PermissionGrantTracker {
        private final int totalCount;
        private int grantedCount = 0;
        private int deniedCount = 0;

        public PermissionGrantTracker(int totalCount) {
            this.totalCount = totalCount;
        }

        public void onPermissionGranted(String permission) {
            grantedCount++;
        }

        public void onPermissionDenied(String permission) {
            deniedCount++;
        }

        public boolean isComplete() {
            return (grantedCount + deniedCount) >= totalCount;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public int getGrantedCount() {
            return grantedCount;
        }

        public int getDeniedCount() {
            return deniedCount;
        }

        public int getCompletedCount() {
            return grantedCount + deniedCount;
        }
    }
}