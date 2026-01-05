package com.teletalker.app.utils;

import android.content.Context;
import android.util.Log;

import com.teletalker.app.network.FirebaseFunctionsManager;
import com.teletalker.app.network.UserBalance;

/**
 * Caches subscription status to avoid excessive API calls
 */
public class SubscriptionCacheManager {
    private static final String TAG = "SubscriptionCache";

    // Cache for 1 hour
    private static final long CACHE_DURATION = 60 * 60 * 1000;

    private final PreferencesManager prefs;

    public SubscriptionCacheManager(Context context) {
        this.prefs = PreferencesManager.getInstance(context);
    }

    /**
     * Get cached subscription status (returns immediately)
     */
    public CachedSubscription getCachedSubscription() {
        String appVersion = prefs.getString("cached_app_version", "lite");
        double freeMinutes = prefs.getDouble("cached_free_minutes", 0);
        double paidMinutes = prefs.getDouble("cached_paid_minutes", 0);
        long lastSync = prefs.getLong("last_subscription_sync", 0);

        Log.d("Cashed" , "Cashed:::" + " cached_app_version==" + appVersion);
        Log.d("Cashed" , "Cashed:::" +" freeMinutes==" + freeMinutes);
        Log.d("Cashed" , "Cashed:::" +" paidMinutes==" + paidMinutes);
        Log.d("Cashed" , "Cashed:::" +" lastSync==" + lastSync);
        return new CachedSubscription(appVersion, freeMinutes, paidMinutes, lastSync);
    }

    /**
     * Check if cache is still valid
     */
    public boolean isCacheValid() {
        long lastSync = prefs.getLong("last_subscription_sync", 0);
        long now = System.currentTimeMillis();
        boolean valid = (now - lastSync) < CACHE_DURATION;

        Log.d(TAG, "Cache valid: " + valid + " (age: " + ((now - lastSync) / 1000) + "s)");
        return valid;
    }

    /**
     * Sync subscription from backend (only if cache expired)
     */
    public void syncIfNeeded(SubscriptionCallback callback) {
        if (isCacheValid()) {
            Log.d(TAG, "Using cached subscription data");
            callback.onSuccess(getCachedSubscription());
            return;
        }

        Log.d(TAG, "Cache expired, syncing from backend...");
        syncFromBackend(callback);
    }

    /**
     * Force sync from backend (ignores cache)
     */
    public void forceSync(SubscriptionCallback callback) {
        Log.d(TAG, "Force syncing subscription from backend...");
        syncFromBackend(callback);
    }

    public void  updateCashedPlan(String plan){
        prefs.putString("cached_app_version",plan );

    }

    private void syncFromBackend(SubscriptionCallback callback) {
        FirebaseFunctionsManager.getInstance().getBalance(new FirebaseFunctionsManager.OnBalanceCallback() {
            @Override
            public void onSuccess(UserBalance balance) {
                // Cache the data
                prefs.putString("cached_app_version", balance.getAppVersion());
                prefs.putDouble("cached_free_minutes", balance.getFreeMinutesBalance());
                prefs.putDouble("cached_paid_minutes", balance.getPaidMinutesBalance());
                prefs.putLong("last_subscription_sync", System.currentTimeMillis());

                Log.d(TAG, "Subscription synced: " + balance.getAppVersion() +
                        ", " + (balance.getFreeMinutesBalance() + balance.getPaidMinutesBalance()) + " mins");

                callback.onSuccess(new CachedSubscription(
                        balance.getAppVersion(),
                        balance.getFreeMinutesBalance(),
                        balance.getPaidMinutesBalance(),
                        System.currentTimeMillis()
                ));
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Sync failed: " + error);

                // Return cached data even if expired
                callback.onError(error, getCachedSubscription());
            }
        });
    }

    /**
     * Clear cached subscription data
     */
    public void clearCache() {
        prefs.remove("cached_app_version");
        prefs.remove("cached_free_minutes");
        prefs.remove("cached_paid_minutes");
        prefs.remove("last_subscription_sync");
    }

    public interface SubscriptionCallback {
        void onSuccess(CachedSubscription subscription);
        default void onError(String error, CachedSubscription cachedData) {
            // Optional: use cached data on error
        }
    }

    public static class CachedSubscription {
        public final String appVersion;
        public final double freeMinutes;
        public final double paidMinutes;
        public final long lastSyncTime;

        public CachedSubscription(String appVersion, double freeMinutes, double paidMinutes, long lastSyncTime) {
            this.appVersion = appVersion != null ? appVersion : "lite";
            this.freeMinutes = freeMinutes;
            this.paidMinutes = paidMinutes;
            this.lastSyncTime = lastSyncTime;
        }

        public double getTotalMinutes() {
            return freeMinutes + paidMinutes;
        }

        public boolean isStandard() {
            return "standard".equalsIgnoreCase(appVersion);
        }

        public boolean hasMinutes() {
            return getTotalMinutes() > 0;
        }
    }
}