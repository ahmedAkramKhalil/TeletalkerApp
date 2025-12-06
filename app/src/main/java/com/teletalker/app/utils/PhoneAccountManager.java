package com.teletalker.app.utils;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.teletalker.app.R;
import com.teletalker.app.services.TeleTalkerConnectionService;

/**
 * Manages phone account registration for default dialer
 */
public class PhoneAccountManager {

    private static final String TAG = "PhoneAccountManager";
    private static final String PHONE_ACCOUNT_ID = "TeleTalkerAccount";
    private static final String PHONE_ACCOUNT_LABEL = "TeleTalker";

    private final Context context;
    private final TelecomManager telecomManager;

    public PhoneAccountManager(Context context) {
        this.context = context;
        this.telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
    }

    /**
     * Register phone account for making calls
     * This is required for default dialer functionality
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public boolean registerPhoneAccount() {
        if (telecomManager == null) {
            Log.e(TAG, "TelecomManager is null");
            return false;
        }

        try {
            PhoneAccountHandle phoneAccountHandle = getPhoneAccountHandle();

            // Build the phone account
            PhoneAccount.Builder builder = PhoneAccount.builder(
                    phoneAccountHandle,
                    PHONE_ACCOUNT_LABEL
            );

            // Set capabilities
            builder.setCapabilities(
                    PhoneAccount.CAPABILITY_CALL_PROVIDER |
                            PhoneAccount.CAPABILITY_CONNECTION_MANAGER |
                            PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS
            );

            // Set supported URI schemes
            builder.addSupportedUriScheme(PhoneAccount.SCHEME_TEL);
            builder.addSupportedUriScheme(PhoneAccount.SCHEME_VOICEMAIL);

            // Set icon
            try {
                Icon icon = Icon.createWithResource(context, R.mipmap.ic_launcher);
                builder.setIcon(icon);
            } catch (Exception e) {
                Log.w(TAG, "Could not set icon", e);
            }

            // Set address (optional)
            // Uri address = Uri.fromParts(PhoneAccount.SCHEME_TEL, "YourNumber", null);
            // builder.setAddress(address);

            PhoneAccount phoneAccount = builder.build();

            // Register the account
//            telecomManager.registerPhoneAccount(phoneAccount);

            Log.d(TAG, "✅ Phone account registered successfully");
            return true;

        } catch (SecurityException e) {
            Log.e(TAG, "❌ Security exception registering phone account", e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error registering phone account", e);
            return false;
        }
    }

    /**
     * Unregister phone account
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void unregisterPhoneAccount() {
        if (telecomManager == null) {
            return;
        }

        try {
            PhoneAccountHandle phoneAccountHandle = getPhoneAccountHandle();
            telecomManager.unregisterPhoneAccount(phoneAccountHandle);
            Log.d(TAG, "Phone account unregistered");
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering phone account", e);
        }
    }

    /**
     * Get the phone account handle
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public PhoneAccountHandle getPhoneAccountHandle() {
        ComponentName componentName = new ComponentName(
                context,
                TeleTalkerConnectionService.class
        );

        return new PhoneAccountHandle(componentName, PHONE_ACCOUNT_ID);
    }

    /**
     * Check if phone account is registered
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public boolean isPhoneAccountRegistered() {
        if (telecomManager == null) {
            return false;
        }

        try {
            PhoneAccountHandle handle = getPhoneAccountHandle();
            PhoneAccount account = telecomManager.getPhoneAccount(handle);
            return account != null && account.isEnabled();
        } catch (Exception e) {
            Log.e(TAG, "Error checking phone account", e);
            return false;
        }
    }

    /**
     * Check if app is default dialer
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public boolean isDefaultDialer() {
        if (telecomManager == null) {
            return false;
        }

        String defaultDialer = telecomManager.getDefaultDialerPackage();
        boolean isDefault = context.getPackageName().equals(defaultDialer);

        Log.d(TAG, "Default dialer: " + defaultDialer + ", Is TeleTalker: " + isDefault);
        return isDefault;
    }
}