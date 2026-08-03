package com.teletalker.app.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

/**
 * OEM lifecycle helpers: battery-optimization exemption and per-manufacturer
 * autostart settings. These keep boot-restart and scheduled outbound calls
 * alive on aggressive OEMs (Xiaomi/MIUI, Oppo/Realme/ColorOS, Vivo/FuntouchOS,
 * Huawei, etc.), where they are otherwise killed regardless of permissions.
 */
public final class OemLifecycleHelper {
    private static final String TAG = "OemLifecycle";

    private OemLifecycleHelper() {}

    public static boolean isIgnoringBatteryOptimizations(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    @SuppressWarnings("BatteryLife")
    public static void requestIgnoreBatteryOptimizations(Context context) {
        if (isIgnoringBatteryOptimizations(context)) {
            Toast.makeText(context, "Battery optimization already disabled", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "battery-opt request failed: " + e.getMessage());
            try {
                Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            } catch (Exception ignored) {
                openAppDetails(context);
            }
        }
    }

    /** Opens the manufacturer's autostart / background-management screen if known. */
    public static void openAutoStartSettings(Context context) {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase();
        Intent intent = new Intent();
        try {
            switch (m) {
                case "xiaomi":
                case "redmi":
                case "poco":
                    intent.setClassName("com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity");
                    break;
                case "oppo":
                case "realme":
                    intent.setClassName("com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity");
                    break;
                case "vivo":
                    intent.setClassName("com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity");
                    break;
                case "huawei":
                case "honor":
                    intent.setClassName("com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity");
                    break;
                case "samsung":
                    intent.setClassName("com.samsung.android.lool",
                            "com.samsung.android.sm.ui.battery.BatteryActivity");
                    break;
                default:
                    openAppDetails(context);
                    return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "autostart settings not found for " + m + ": " + e.getMessage());
            openAppDetails(context);
        } catch (Exception e) {
            Log.w(TAG, "autostart open failed: " + e.getMessage());
            openAppDetails(context);
        }
    }

    private static void openAppDetails(Context context) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + context.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            Toast.makeText(context, "Enable autostart & disable battery limits for TeleTalker",
                    Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {
        }
    }
}
