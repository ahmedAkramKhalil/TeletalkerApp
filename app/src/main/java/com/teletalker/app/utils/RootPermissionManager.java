package com.teletalker.app.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class RootPermissionManager {
    private static final String TAG = "RootPermissionManager";

    // Root permission callback interface
    public interface RootPermissionCallback {
        void onRootAccessGranted();
        void onRootAccessDenied(String reason);
        void onPermissionGranted(String permission);
        void onPermissionDenied(String permission);
    }

    // Check if device is rooted
    public static boolean isDeviceRooted() {
        return checkRootMethod1() || checkRootMethod2() || checkRootMethod3();
    }

    // Method 1: Check for su binary
    private static boolean checkRootMethod1() {
        String[] suPaths = {
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su",
                "/su/bin/su"
        };

        for (String path : suPaths) {
            if (new File(path).exists()) {
                Log.d(TAG, "Root binary found at: " + path);
                return true;
            }
        }
        return false;
    }

    // Method 2: Check for root management apps
    private static boolean checkRootMethod2() {
        String[] rootApps = {
                "com.noshufou.android.su",
                "com.noshufou.android.su.elite",
                "eu.chainfire.supersu",
                "com.koushikdutta.superuser",
                "com.thirdparty.superuser",
                "com.yellowes.su",
                "com.koushikdutta.rommanager",
                "com.koushikdutta.rommanager.license",
                "com.dimonvideo.luckypatcher",
                "com.chelpus.lackypatch",
                "com.ramdroid.appquarantine",
                "com.topjohnwu.magisk"
        };

        for (String app : rootApps) {
            try {
                PackageManager pm = null; // This would need context
                // pm.getPackageInfo(app, 0);
                // return true;
            } catch (Exception e) {
                // Package not found
            }
        }
        return false;
    }

    // Method 3: Try to execute su command
    private static boolean checkRootMethod3() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su");
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = in.readLine();
            process.destroy();
            return true;
        } catch (Exception e) {
            Log.v(TAG, "Root check method 3 failed: " + e.getMessage());
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    // Request root access from user
    public static void requestRootAccess(Context context, RootPermissionCallback callback) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Requesting root access...");

                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                BufferedReader is = new BufferedReader(new InputStreamReader(process.getInputStream()));

                // Send a simple command to test root access
                os.writeBytes("id\n");
                os.flush();
                os.writeBytes("exit\n");
                os.flush();

                // Wait for process to complete (with timeout)
                boolean finished = process.waitFor(10, TimeUnit.SECONDS);

                if (finished && process.exitValue() == 0) {
                    String result = is.readLine();
                    if (result != null && result.contains("uid=0")) {
                        Log.d(TAG, "✅ Root access granted");
                        if (callback != null) {
                            callback.onRootAccessGranted();
                        }
                    } else {
                        Log.w(TAG, "❌ Root access denied - invalid response");
                        if (callback != null) {
                            callback.onRootAccessDenied("Invalid root response");
                        }
                    }
                } else {
                    Log.w(TAG, "❌ Root access denied - command failed");
                    if (callback != null) {
                        callback.onRootAccessDenied("Su command failed or timed out");
                    }
                }

                process.destroy();

            } catch (Exception e) {
                Log.e(TAG, "❌ Root access request failed: " + e.getMessage());
                if (callback != null) {
                    callback.onRootAccessDenied(e.getMessage());
                }
            }
        }).start();
    }

    // Check if specific permission is granted
    public static void checkRootPermission(String permission, RootPermissionCallback callback) {
        new Thread(() -> {
            try {
                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                BufferedReader is = new BufferedReader(new InputStreamReader(process.getInputStream()));

                // Check if permission is granted using pm command
                String command = "pm list permissions -g | grep " + permission + "\n";
                os.writeBytes(command);
                os.flush();
                os.writeBytes("exit\n");
                os.flush();

                boolean finished = process.waitFor(5, TimeUnit.SECONDS);

                if (finished && process.exitValue() == 0) {
                    String result;
                    boolean hasPermission = false;
                    while ((result = is.readLine()) != null) {
                        if (result.contains(permission)) {
                            hasPermission = true;
                            break;
                        }
                    }

                    if (hasPermission) {
                        Log.d(TAG, "✅ Permission " + permission + " is available");
                        if (callback != null) {
                            callback.onPermissionGranted(permission);
                        }
                    } else {
                        Log.w(TAG, "❌ Permission " + permission + " is not available");
                        if (callback != null) {
                            callback.onPermissionDenied(permission);
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to check permission: " + permission);
                    if (callback != null) {
                        callback.onPermissionDenied(permission);
                    }
                }

                process.destroy();

            } catch (Exception e) {
                Log.e(TAG, "Error checking permission " + permission + ": " + e.getMessage());
                if (callback != null) {
                    callback.onPermissionDenied(permission);
                }
            }
        }).start();
    }

    // Grant permission using root
    public static void grantRootPermission(Context context, String permission, RootPermissionCallback callback) {
        new Thread(() -> {
            try {
                String packageName = context.getPackageName();
                Log.d(TAG, "Attempting to grant permission: " + permission + " to " + packageName);

                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                BufferedReader is = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader es = new BufferedReader(new InputStreamReader(process.getErrorStream()));

                // Grant permission using pm grant command
                String command = "pm grant " + packageName + " " + permission + "\n";
                os.writeBytes(command);
                os.flush();

                // Also try alternative methods
                String altCommand = "chmod 777 /system/bin/pm && pm grant " + packageName + " " + permission + "\n";
                os.writeBytes(altCommand);
                os.flush();

                os.writeBytes("exit\n");
                os.flush();

                boolean finished = process.waitFor(10, TimeUnit.SECONDS);

                // Read any error messages
                String error;
                while ((error = es.readLine()) != null) {
                    Log.w(TAG, "Error output: " + error);
                }

                if (finished && process.exitValue() == 0) {
                    Log.d(TAG, "✅ Permission grant command executed successfully");

                    // Verify the permission was actually granted
                    verifyPermissionGranted(context, permission, callback);
                } else {
                    Log.e(TAG, "❌ Permission grant command failed");
                    if (callback != null) {
                        callback.onPermissionDenied(permission);
                    }
                }

                process.destroy();

            } catch (Exception e) {
                Log.e(TAG, "Error granting permission " + permission + ": " + e.getMessage());
                if (callback != null) {
                    callback.onPermissionDenied(permission);
                }
            }
        }).start();
    }

    // Verify permission was actually granted
    private static void verifyPermissionGranted(Context context, String permission, RootPermissionCallback callback) {
        try {
            // Wait a moment for the system to update
            Thread.sleep(1000);

            // Check using PackageManager
            int result = context.getPackageManager().checkPermission(permission, context.getPackageName());

            if (result == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "✅ Permission verified as granted: " + permission);
                if (callback != null) {
                    callback.onPermissionGranted(permission);
                }
            } else {
                Log.w(TAG, "⚠️ Permission grant verification failed: " + permission);
                // Try alternative verification method
                verifyPermissionAlternative(context, permission, callback);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error verifying permission: " + e.getMessage());
            // Try alternative verification
            verifyPermissionAlternative(context, permission, callback);
        }
    }

    // Alternative permission verification
    private static void verifyPermissionAlternative(Context context, String permission, RootPermissionCallback callback) {
        new Thread(() -> {
            try {
                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                BufferedReader is = new BufferedReader(new InputStreamReader(process.getInputStream()));

                String command = "dumpsys package " + context.getPackageName() + " | grep " + permission + "\n";
                os.writeBytes(command);
                os.flush();
                os.writeBytes("exit\n");
                os.flush();

                boolean finished = process.waitFor(5, TimeUnit.SECONDS);

                if (finished) {
                    String result;
                    boolean found = false;
                    while ((result = is.readLine()) != null) {
                        if (result.contains(permission) && result.contains("granted=true")) {
                            found = true;
                            break;
                        }
                    }

                    if (found) {
                        Log.d(TAG, "✅ Alternative verification successful: " + permission);
                        if (callback != null) {
                            callback.onPermissionGranted(permission);
                        }
                    } else {
                        Log.w(TAG, "❌ Alternative verification failed: " + permission);
                        if (callback != null) {
                            callback.onPermissionDenied(permission);
                        }
                    }
                }

                process.destroy();

            } catch (Exception e) {
                Log.e(TAG, "Alternative verification error: " + e.getMessage());
                if (callback != null) {
                    callback.onPermissionDenied(permission);
                }
            }
        }).start();
    }

    // Grant multiple permissions
    public static void grantMultipleRootPermissions(Context context, String[] permissions,
                                                    MultiplePermissionCallback callback) {
        new MultiplePermissionGranter(context, permissions, callback).start();
    }

    // Callback for multiple permissions
    public interface MultiplePermissionCallback {
        void onAllPermissionsGranted();
        void onSomePermissionsDenied(String[] deniedPermissions);
        void onAllPermissionsDenied();
    }

    // Helper class for granting multiple permissions
    private static class MultiplePermissionGranter {
        private Context context;
        private String[] permissions;
        private MultiplePermissionCallback callback;
        private int grantedCount = 0;
        private int totalPermissions;

        public MultiplePermissionGranter(Context context, String[] permissions, MultiplePermissionCallback callback) {
            this.context = context;
            this.permissions = permissions;
            this.callback = callback;
            this.totalPermissions = permissions.length;
        }

        public void start() {
            for (String permission : permissions) {
                grantRootPermission(context, permission, new RootPermissionCallback() {
                    @Override
                    public void onRootAccessGranted() {
                        // Not used here
                    }

                    @Override
                    public void onRootAccessDenied(String reason) {
                        // Not used here
                    }

                    @Override
                    public void onPermissionGranted(String permission) {
                        synchronized (MultiplePermissionGranter.this) {
                            grantedCount++;
                            checkCompletion();
                        }
                    }

                    @Override
                    public void onPermissionDenied(String permission) {
                        synchronized (MultiplePermissionGranter.this) {
                            checkCompletion();
                        }
                    }
                });
            }
        }

        private void checkCompletion() {
            // This is a simplified check - in real implementation you'd track which specific permissions failed
            if (grantedCount == totalPermissions) {
                if (callback != null) {
                    callback.onAllPermissionsGranted();
                }
            } else {
                // For simplicity, we'll call this when all attempts are done
                // In practice, you'd want to track completion vs success separately
            }
        }
    }
}