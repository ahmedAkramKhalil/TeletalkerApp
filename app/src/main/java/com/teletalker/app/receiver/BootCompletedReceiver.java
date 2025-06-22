package com.teletalker.app.receiver;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


import java.io.File;

/**
 * Simple boot completion handler - much simpler than BCR's DirectBootMigrationService
 * Just ensures the service is ready after device reboot
 */
public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = "BootCompletedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {

            Log.d(TAG, "Device boot completed - initializing TeleTalker");

            // Simple tasks after boot:
            // 1. Clean up any temporary files
            cleanupTempFiles(context);

            // 2. Ensure InCallService is ready (it will auto-start when needed)
            // No need to manually start - InCallService starts automatically

            // 3. Log that we're ready
            Log.d(TAG, "TeleTalker ready for call recording");
        }
    }

    private void cleanupTempFiles(Context context) {
        try {
            // Clean up any incomplete recordings from before reboot
            File tempDir = new File(context.getCacheDir(), "temp_recordings");
            if (tempDir.exists()) {
                File[] tempFiles = tempDir.listFiles();
                if (tempFiles != null) {
                    for (File file : tempFiles) {
                        if (file.delete()) {
                            Log.d(TAG, "Cleaned up temp file: " + file.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up temp files", e);
        }
    }
}

