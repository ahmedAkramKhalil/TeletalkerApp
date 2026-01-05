package com.teletalker.app.utils;

import android.content.Context;
import android.util.Log;

import com.teletalker.app.billing.BillingManager;
import com.teletalker.app.features.home.ThemeManager;
import com.teletalker.app.network.FirebaseFunctionsManager;
import com.teletalker.app.network.UserBalance;

import java.util.ArrayList;
import java.util.List;

public class SubscriptionManager {
    private static final String TAG = "SubscriptionManager";
    private static SubscriptionManager instance;

    private final Context context;
    private final PreferencesManager prefs;
    private final SubscriptionCacheManager cacheManager; // ADD THIS
    private final List<SubscriptionListener> listeners = new ArrayList<>();

    private String currentPlan = "lite";
    private double minutesBalance = 0;
    private double freeMinutes = 0;
    private double paidMinutes = 0;

    public interface SubscriptionListener {
        default void onBalanceChanged(double newBalance) {}
        default void onPlanChanged(String newPlan) {}
    }

    private SubscriptionManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = PreferencesManager.getInstance(context);
        this.cacheManager = new SubscriptionCacheManager(context); // ADD THIS
        loadCached();
    }

    public static synchronized SubscriptionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SubscriptionManager(context);
        }
        return instance;
    }

    // ============================================
    // LISTENERS
    // ============================================

    public void addListener(SubscriptionListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(SubscriptionListener listener) {
        listeners.remove(listener);
    }

    private void notifyBalanceChanged(double balance) {
        for (SubscriptionListener l : listeners) {
            l.onBalanceChanged(balance);
        }
    }

    private void notifyPlanChanged(String plan) {
        for (SubscriptionListener l : listeners) {
            l.onPlanChanged(plan);
        }
    }

    // ============================================
    // GETTERS
    // ============================================

    public String getCurrentPlan() {
        return currentPlan;
    }

    public double getMinutesBalance() {
        return minutesBalance;
    }

    public double getFreeMinutes() {
        return freeMinutes;
    }

    public double getPaidMinutes() {
        return paidMinutes;
    }

    public boolean isStandard() {
        return "standard".equalsIgnoreCase(currentPlan);
    }

    public boolean isLite() {
        return "lite".equalsIgnoreCase(currentPlan);
    }

    // Get cache manager for offline access
    public SubscriptionCacheManager getCacheManager() {
        return cacheManager;
    }

    // ============================================
    // LOAD CACHED DATA
    // ============================================

    private void loadCached() {
        // Load from SubscriptionCacheManager (single source)
        SubscriptionCacheManager.CachedSubscription cached = cacheManager.getCachedSubscription();
        currentPlan = cached.appVersion;
        freeMinutes = cached.freeMinutes;
        paidMinutes = cached.paidMinutes;
        minutesBalance = cached.getTotalMinutes();

        Log.d(TAG, "Loaded cached: plan=" + currentPlan + ", balance=" + minutesBalance);
    }

    // ============================================
    // SYNC FROM FIREBASE
    // ============================================

    public void sync() {
        sync(null);
    }

    public void sync(Runnable onComplete) {
        FirebaseFunctionsManager.getInstance().getBalance(new FirebaseFunctionsManager.OnBalanceCallback() {
            @Override
            public void onSuccess(UserBalance balance) {
                String oldPlan = currentPlan;
                double oldBalance = minutesBalance;

                // Update local state
                currentPlan = balance.getAppVersion();
                freeMinutes = balance.getFreeMinutesBalance();
                paidMinutes = balance.getPaidMinutesBalance();
                minutesBalance = freeMinutes + paidMinutes;

                // Update SubscriptionCacheManager
                updateCache(currentPlan, freeMinutes, paidMinutes);

                // Notify balance change
                if (oldBalance != minutesBalance) {
                    Log.d(TAG, "Balance changed: " + oldBalance + " -> " + minutesBalance);
                    notifyBalanceChanged(minutesBalance);
                }

                // Update theme only if plan actually changed
                if (!oldPlan.equals(currentPlan)) {
                    Log.d(TAG, "Plan changed: " + oldPlan + " -> " + currentPlan);
                    ThemeManager.getInstance(context).setAppVersion(currentPlan);
                    notifyPlanChanged(currentPlan);
                }

                // Sync BillingManager
                BillingManager.getInstance(context).syncBalance();

                if (onComplete != null) onComplete.run();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Sync error: " + error);
                if (onComplete != null) onComplete.run();
            }
        });
    }

    // ============================================
    // UPDATE CACHE
    // ============================================

    private void updateCache(String plan, double free, double paid) {
        // Update PreferencesManager (for SubscriptionManager)
        prefs.putString("current_plan", plan);
        prefs.putDouble("minutes_balance", free + paid);

        // Update SubscriptionCacheManager (for offline usage)
        prefs.putString("cached_app_version", plan);
        prefs.putDouble("cached_free_minutes", free);
        prefs.putDouble("cached_paid_minutes", paid);
        prefs.putLong("last_subscription_sync", System.currentTimeMillis());

        Log.d(TAG, "Cache updated: plan=" + plan + ", free=" + free + ", paid=" + paid);
    }

    // ============================================
    // MANUAL UPDATES (from webhook/push)
    // ============================================

    public void updateBalance(double newBalance) {
        if (minutesBalance != newBalance) {
            minutesBalance = newBalance;
            prefs.putDouble("minutes_balance", newBalance);
            // Also update cache
            prefs.putDouble("cached_free_minutes", freeMinutes);
            prefs.putDouble("cached_paid_minutes", paidMinutes);
            prefs.putLong("last_subscription_sync", System.currentTimeMillis());
            notifyBalanceChanged(newBalance);
        }
    }

    public void updatePlan(String newPlan) {
        if (!currentPlan.equals(newPlan)) {
            currentPlan = newPlan;
            prefs.putString("current_plan", newPlan);
            // Also update cache
            cacheManager.updateCashedPlan(newPlan);
            ThemeManager.getInstance(context).setAppVersion(newPlan);
            notifyPlanChanged(newPlan);
        }
    }

    // ============================================
    // CLEAR CACHE (on logout)
    // ============================================

    public void clearAll() {
        currentPlan = "lite";
        minutesBalance = 0;
        freeMinutes = 0;
        paidMinutes = 0;

        prefs.remove("current_plan");
        prefs.remove("minutes_balance");
        cacheManager.clearCache();

        Log.d(TAG, "All subscription data cleared");
    }
}