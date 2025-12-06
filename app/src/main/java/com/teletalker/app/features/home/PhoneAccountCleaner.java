package com.teletalker.app.features.home;


import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import java.util.List;

public class PhoneAccountCleaner {
    private static final String TAG = "PhoneAccountCleaner";

    /**
     * Unregister ALL PhoneAccounts for this app
     * No IDs needed - automatically finds and removes all
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    @RequiresApi(api = Build.VERSION_CODES.M)
    public static int unregisterAllPhoneAccounts(Context context) {
        TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
        if (telecomManager == null) {
            Log.e(TAG, "❌ TelecomManager not available");
            return 0;
        }

        Log.d(TAG, "═══════════════════════════════════════════");
        Log.d(TAG, "🧹 CLEANING UP PHONE ACCOUNTS");
        Log.d(TAG, "═══════════════════════════════════════════");

        String ourPackage = context.getPackageName();
        int unregisteredCount = 0;

        // Get ALL call-capable phone accounts
        List<PhoneAccountHandle> handles = telecomManager.getCallCapablePhoneAccounts();

        Log.d(TAG, "Found " + handles.size() + " total PhoneAccounts");

        for (PhoneAccountHandle handle : handles) {
            ComponentName component = handle.getComponentName();

            // Only unregister OUR app's accounts
            if (component.getPackageName().equals(ourPackage)) {
                try {
                    Log.d(TAG, "───────────────────────────────────────");
                    Log.d(TAG, "Found TeleTalker PhoneAccount:");
                    Log.d(TAG, "  Component: " + component.getClassName());
                    Log.d(TAG, "  ID: " + handle.getId());
                    Log.d(TAG, "  Unregistering...");

                    telecomManager.unregisterPhoneAccount(handle);
                    unregisteredCount++;

                    Log.d(TAG, "✅ Successfully unregistered!");

                } catch (SecurityException e) {
                    Log.e(TAG, "❌ SecurityException - no permission to unregister", e);
                } catch (Exception e) {
                    Log.e(TAG, "❌ Failed to unregister: " + e.getMessage(), e);
                }
            }
        }

        Log.d(TAG, "═══════════════════════════════════════════");
        Log.d(TAG, "🎉 Cleanup Complete!");
        Log.d(TAG, "   Unregistered: " + unregisteredCount + " PhoneAccounts");
        Log.d(TAG, "═══════════════════════════════════════════");

        // Verify cleanup
        verifyCleanup(context);

        return unregisteredCount;
    }

    /**
     * Verify all PhoneAccounts are removed
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static void verifyCleanup(Context context) {
        TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
        if (telecomManager == null) return;

        String ourPackage = context.getPackageName();
        List<PhoneAccountHandle> remainingHandles = telecomManager.getCallCapablePhoneAccounts();

        int ourAccountsRemaining = 0;
        for (PhoneAccountHandle handle : remainingHandles) {
            if (handle.getComponentName().getPackageName().equals(ourPackage)) {
                ourAccountsRemaining++;
                Log.w(TAG, "⚠️ Still registered: " + handle.getId());
            }
        }

        if (ourAccountsRemaining == 0) {
            Log.d(TAG, "✅ VERIFICATION: All PhoneAccounts successfully removed!");
        } else {
            Log.w(TAG, "⚠️ VERIFICATION: " + ourAccountsRemaining + " PhoneAccounts still remain");
        }
    }

    /**
     * Get count of registered PhoneAccounts for this app
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public static int getPhoneAccountCount(Context context) {
        TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
        if (telecomManager == null) return 0;

        String ourPackage = context.getPackageName();
        List<PhoneAccountHandle> handles = telecomManager.getCallCapablePhoneAccounts();

        int count = 0;
        for (PhoneAccountHandle handle : handles) {
            if (handle.getComponentName().getPackageName().equals(ourPackage)) {
                count++;
            }
        }

        return count;
    }
}