package com.teletalker.app.utils;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.teletalker.app.network.FirebaseFunctionsManager;

/**
 * Reports this handset to the backend on login: device fingerprint (ANDROID_ID),
 * model/OS/app-version, a last-active stamp, and any pending installer/referral
 * code entered at sign-up. Caches the returned account status so the pre-call
 * guard ({@link com.teletalker.app.services.scheduler.ScheduledCallExecutor})
 * can block calls for a suspended/banned account without a network round-trip.
 *
 * Fail-open: any error leaves the cached status untouched (defaults to active),
 * so a transient backend problem never blocks a legitimate user's calls.
 */
public final class DeviceReporter {

    private static final String TAG = "DeviceReporter";
    public static final String KEY_ACCOUNT_STATUS = "account_status";
    public static final String KEY_PENDING_REFERRAL = "pending_referral_code";

    private DeviceReporter() {}

    /** Best-effort device registration. Safe to call on every app launch. */
    public static void report(Context context) {
        try {
            if (FirebaseAuth.getInstance().getCurrentUser() == null) return; // not signed in yet

            final Context app = context.getApplicationContext();
            final PreferencesManager prefs = PreferencesManager.getInstance(app);

            // Refresh the FCM push token on the backend. The device's token can
            // rotate (app reinstall / device reset) and the app otherwise never
            // re-sends it → createBooking hits a stale token → 503. Push the
            // current token on every login so bookings can always reach us.
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                        .addOnSuccessListener(tok -> {
                            if (tok == null || tok.isEmpty()) return;
                            FirebaseFunctionsManager.getInstance().updateFCMToken(tok,
                                    new FirebaseFunctionsManager.OnTokenUpdateCallback() {
                                        @Override public void onSuccess() { Log.d(TAG, "FCM token refreshed on login"); }
                                        @Override public void onError(String error) { Log.w(TAG, "FCM token refresh failed: " + error); }
                                    });
                        });
            } catch (Exception e) {
                Log.w(TAG, "FCM token refresh failed (non-fatal)", e);
            }

            String deviceId = null;
            try {
                deviceId = Settings.Secure.getString(app.getContentResolver(), Settings.Secure.ANDROID_ID);
            } catch (Exception e) {
                Log.w(TAG, "ANDROID_ID unavailable", e);
            }

            final String model = Build.MANUFACTURER + " " + Build.MODEL;
            final String osVersion = "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
            String versionName = "";
            try {
                versionName = app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName;
            } catch (Exception e) {
                Log.w(TAG, "versionName unavailable", e);
            }
            final String appVersion = versionName;
            final String referral = prefs.getString(KEY_PENDING_REFERRAL, "");

            FirebaseFunctionsManager.getInstance().registerDevice(
                    deviceId, model, osVersion, appVersion,
                    referral == null ? "" : referral,
                    new FirebaseFunctionsManager.OnRegisterDeviceCallback() {
                        @Override
                        public void onSuccess(String status) {
                            prefs.putString(KEY_ACCOUNT_STATUS, status == null ? "active" : status);
                            // Referral is first-write-wins on the backend; clear the
                            // pending code so we never resend it on later launches.
                            if (referral != null && !referral.isEmpty()) {
                                prefs.putString(KEY_PENDING_REFERRAL, "");
                            }
                            Log.d(TAG, "Device registered — account status: " + status);
                        }

                        @Override
                        public void onError(String error) {
                            Log.w(TAG, "registerDevice failed (non-fatal): " + error);
                        }
                    });
        } catch (Exception e) {
            Log.w(TAG, "report() failed (non-fatal)", e);
        }
    }
}
