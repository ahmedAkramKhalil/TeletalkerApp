package com.teletalker.app.utils;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.telecom.TelecomManager;
import android.util.Log;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

/**
 * Requests the default-dialer role (ROLE_DIALER). For a call app this is the
 * legitimate, cross-device way to obtain phone/call privileges (CALL_PHONE,
 * call log, InCallService binding) through the platform Role framework, without
 * relying on root grants or reflection.
 *
 * Opt-in and user-consented via the system dialog; fully reversible under
 * Settings > Default apps. The app already binds its InCallService via the
 * privileged CONTROL_INCALL_EXPERIENCE permission, so this is a fallback for
 * device models where that path alone is insufficient.
 */
public final class DialerRoleHelper {
    private static final String TAG = "DialerRole";
    public static final int REQ_DIALER_ROLE = 6021;

    private DialerRoleHelper() {}

    public static boolean isDefaultDialer(Context context) {
        TelecomManager tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        return tm != null && context.getPackageName().equals(tm.getDefaultDialerPackage());
    }

    /**
     * Fragment overload: launches via the Fragment so the accept/deny result is
     * delivered to Fragment.onActivityResult(REQ_DIALER_ROLE) (an Activity-launched
     * request would route the result to the Activity instead, never reaching the
     * Fragment that needs to refresh its UI).
     */
    public static void requestDefaultDialer(Fragment fragment) {
        Context context = fragment.getContext();
        if (context == null) return;
        if (isDefaultDialer(context)) {
            Toast.makeText(context, "Already the default phone app", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RoleManager rm = (RoleManager) context.getSystemService(Context.ROLE_SERVICE);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    Intent intent = rm.createRequestRoleIntent(RoleManager.ROLE_DIALER);
                    fragment.startActivityForResult(intent, REQ_DIALER_ROLE);
                    return;
                }
            }
            Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
            intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.getPackageName());
            fragment.startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "default-dialer request failed: " + e.getMessage());
            Toast.makeText(context, "Couldn't open the default-app picker", Toast.LENGTH_LONG).show();
        }
    }

    /** Launches the system prompt to make this app the default phone app. */
    public static void requestDefaultDialer(Activity activity) {
        if (isDefaultDialer(activity)) {
            Toast.makeText(activity, "Already the default phone app", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RoleManager rm = (RoleManager) activity.getSystemService(Context.ROLE_SERVICE);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    Intent intent = rm.createRequestRoleIntent(RoleManager.ROLE_DIALER);
                    activity.startActivityForResult(intent, REQ_DIALER_ROLE);
                    return;
                }
            }
            // Pre-Q / fallback path.
            Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
            intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.getPackageName());
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "default-dialer request failed: " + e.getMessage());
            Toast.makeText(activity, "Couldn't open the default-app picker", Toast.LENGTH_LONG).show();
        }
    }
}
