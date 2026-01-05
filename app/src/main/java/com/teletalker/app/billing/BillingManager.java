package com.teletalker.app.billing;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.teletalker.app.network.DeductMinutesResponse;
import com.teletalker.app.network.FirebaseFunctionsManager;
import com.teletalker.app.network.UserBalance;

/**
 * Enhanced BillingManager - Manages billing operations for call minutes
 * Features:
 * - Singleton pattern for app-wide consistency
 * - Global listener for UI updates
 * - Operation-specific callbacks for business logic
 * - Automatic rollback on errors
 * - Tracks free vs paid minutes separately
 * - Smart caching with staleness detection
 */
public class BillingManager {
    private static final String TAG = "BillingManager";

    public static final String PREFS_NAME = "billing_prefs";
    public static final String KEY_REMAINING_MINUTES = "remaining_minutes";
    public static final String KEY_FREE_MINUTES = "free_minutes";
    public static final String KEY_PAID_MINUTES = "paid_minutes";
    public static final String KEY_LAST_SYNC = "last_sync_time";
    private static final long CACHE_STALE_THRESHOLD = 5 * 60 * 1000; // 5 minutes

    private static BillingManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final FirebaseFunctionsManager functionsManager;

    private BalanceUpdateListener globalListener;

    // ============================================================================
    // CALLBACK INTERFACES
    // ============================================================================

    /**
     * Global listener for all balance updates (UI updates)
     */
    public interface BalanceUpdateListener {
        void onBalanceUpdated(double remainingMinutes);
        void onBalanceError(String error);
    }

    /**
     * Operation-specific callback for sync operations
     */
    public interface SyncCallback {
        void onSyncSuccess(double balance);
        void onSyncError(String error);
    }

    /**
     * Operation-specific callback for deduction operations
     */
    public interface DeductCallback {
        void onDeductSuccess(double newBalance, double cost);
        void onDeductError(String error);
    }

    // ============================================================================
    // SINGLETON INITIALIZATION
    // ============================================================================

    private BillingManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.functionsManager = FirebaseFunctionsManager.getInstance();
    }

    public static synchronized BillingManager getInstance(Context context) {
        if (instance == null) {
            instance = new BillingManager(context);
        }
        return instance;
    }

    /**
     * Set global listener for all balance updates
     */
    public void setBalanceUpdateListener(BalanceUpdateListener listener) {
        this.globalListener = listener;
    }

    /**
     * Remove global listener
     */
    public void removeBalanceUpdateListener() {
        this.globalListener = null;
    }

    // ============================================================================
    // CACHE ACCESS (INSTANT, OFFLINE-CAPABLE)
    // ============================================================================

    /**
     * Get cached total remaining minutes
     */
    public double getCachedRemainingMinutes() {
        return prefs.getFloat(KEY_REMAINING_MINUTES, 0f);
    }

    /**
     * Get cached free minutes
     */
    public double getCachedFreeMinutes() {
        return prefs.getFloat(KEY_FREE_MINUTES, 0f);
    }

    /**
     * Get cached paid minutes
     */
    public double getCachedPaidMinutes() {
        return prefs.getFloat(KEY_PAID_MINUTES, 0f);
    }

    /**
     * Get time since last sync (milliseconds)
     */
    public long getTimeSinceLastSync() {
        long lastSync = prefs.getLong(KEY_LAST_SYNC, 0);
        return System.currentTimeMillis() - lastSync;
    }

    /**
     * Check if cache is stale (older than 5 minutes)
     */
    public boolean isCacheStale() {
        return getTimeSinceLastSync() > CACHE_STALE_THRESHOLD;
    }

    // ============================================================================
    // BALANCE CHECKING
    // ============================================================================

    /**
     * Check if user has enough minutes
     */
    public boolean hasEnoughMinutes(double estimatedMinutes) {
        double remaining = getCachedRemainingMinutes();
        boolean hasEnough = remaining >= estimatedMinutes;

        Log.d(TAG, "Balance check - Remaining: " + remaining +
                ", Required: " + estimatedMinutes +
                ", Result: " + (hasEnough ? "✓ SUFFICIENT" : "✗ INSUFFICIENT"));

        return hasEnough;
    }

    /**
     * Check if user has sufficient balance (minimum 0.5 minutes)
     */
    public boolean hasSufficientBalance() {
        return hasEnoughMinutes(0.5);
    }

    // ============================================================================
    // SYNC BALANCE FROM FIREBASE
    // ============================================================================

    /**
     * Sync balance from Firebase (uses global listener)
     */
    public void syncBalance() {
        syncBalance(null);
    }

    /**
     * Sync balance from Firebase with optional callback
     */
    public void syncBalance(final SyncCallback callback) {
        Log.d(TAG, "🔄 Syncing balance from Firebase...");

        functionsManager.getBalance(new FirebaseFunctionsManager.OnBalanceCallback() {
            @Override
            public void onSuccess(UserBalance balance) {
                // Update cache
                updateCache(
                        balance.getMinutesBalance(),
                        balance.getFreeMinutesBalance(),
                        balance.getPaidMinutesBalance()
                );

                Log.d(TAG, "✅ Balance synced - Total: " + balance.getMinutesBalance() +
                        " (Free: " + balance.getFreeMinutesBalance() +
                        ", Paid: " + balance.getPaidMinutesBalance() + ")");

                // Notify global listener
                if (globalListener != null) {
                    globalListener.onBalanceUpdated(balance.getMinutesBalance());
                }

                // Notify specific callback
                if (callback != null) {
                    callback.onSyncSuccess(balance.getMinutesBalance());
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ Failed to sync balance: " + error);

                // Notify global listener
                if (globalListener != null) {
                    globalListener.onBalanceError(error);
                }

                // Notify specific callback
                if (callback != null) {
                    callback.onSyncError(error);
                }
            }
        });
    }

    // ============================================================================
    // DEDUCT MINUTES AFTER CALL
    // ============================================================================

    /**
     * Deduct minutes after a call (uses global listener)
     */
    public void deductMinutesForCall(long durationSeconds, String phoneNumber, String recordingUrl) {
        deductMinutesForCall(durationSeconds, phoneNumber, recordingUrl, null);
    }

    /**
     * Deduct minutes after a call with optional callback
     */
    public void deductMinutesForCall(long durationSeconds, String phoneNumber,
                                     String recordingUrl, final DeductCallback callback) {

        final double durationMinutes = Math.ceil(durationSeconds / 60.0);

        if (durationMinutes <= 0) {
            Log.d(TAG, "⏩ Call duration too short, no minutes to deduct");
            return;
        }

        Log.d(TAG, "💰 Deducting minutes - Duration: " + durationSeconds + "s (" +
                durationMinutes + " min), Phone: " + phoneNumber);

        // Store original balance for rollback
        final double originalBalance = getCachedRemainingMinutes();
        final double originalFree = getCachedFreeMinutes();
        final double originalPaid = getCachedPaidMinutes();

        // Optimistic update
        double newBalance = Math.max(0, originalBalance - durationMinutes);
        prefs.edit()
                .putFloat(KEY_REMAINING_MINUTES, (float) newBalance)
                .apply();

        Log.d(TAG, "📊 Optimistic update: " + originalBalance + " → " + newBalance);

        // Notify global listener immediately
        if (globalListener != null) {
            globalListener.onBalanceUpdated(newBalance);
        }

        // Deduct from Firebase
        functionsManager.deductMinutes(
                durationMinutes,
                phoneNumber,
                recordingUrl,
                new FirebaseFunctionsManager.OnDeductMinutesCallback() {
                    @Override
                    public void onSuccess(DeductMinutesResponse response) {
                        // Update with actual server balance
                        updateCache(
                                response.getNewBalance(),
                                response.getFreeBalance(),
                                response.getPaidBalance()
                        );

                        Log.d(TAG, "✅ Minutes deducted successfully");
                        Log.d(TAG, "   New balance: " + response.getNewBalance() +
                                " (Free: " + response.getFreeBalance() +
                                ", Paid: " + response.getPaidBalance() + ")");
                        Log.d(TAG, "   Cost: " + response.getCost() + " minutes");

                        // Notify global listener
                        if (globalListener != null) {
                            globalListener.onBalanceUpdated(response.getNewBalance());
                        }

                        // Notify specific callback
                        if (callback != null) {
                            callback.onDeductSuccess(response.getNewBalance(), response.getCost());
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "❌ Failed to deduct from Firebase: " + error);

                        // ROLLBACK
                        prefs.edit()
                                .putFloat(KEY_REMAINING_MINUTES, (float) originalBalance)
                                .putFloat(KEY_FREE_MINUTES, (float) originalFree)
                                .putFloat(KEY_PAID_MINUTES, (float) originalPaid)
                                .apply();

                        Log.w(TAG, "🔙 Rolled back to original: " + originalBalance);

                        // Notify global listener
                        if (globalListener != null) {
                            globalListener.onBalanceError("Failed to deduct: " + error);
                            globalListener.onBalanceUpdated(originalBalance);
                        }

                        // Notify specific callback
                        if (callback != null) {
                            callback.onDeductError(error);
                        }
                    }
                }
        );
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    /**
     * Update cache with new balance values
     */
    private void updateCache(double totalMinutes, double freeMinutes, double paidMinutes) {
        prefs.edit()
                .putFloat(KEY_REMAINING_MINUTES, (float) totalMinutes)
                .putFloat(KEY_FREE_MINUTES, (float) freeMinutes)
                .putFloat(KEY_PAID_MINUTES, (float) paidMinutes)
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .apply();
    }

    /**
     * Clear all cached billing data
     */
    public void clearCache() {
        prefs.edit().clear().apply();
        Log.d(TAG, "🗑️ Billing cache cleared");
    }

    /**
     * Get detailed status for debugging
     */
    public String getStatusString() {
        return String.format("Balance: %.1f min (Free: %.1f, Paid: %.1f), " +
                        "Last sync: %d sec ago, Cache: %s",
                getCachedRemainingMinutes(),
                getCachedFreeMinutes(),
                getCachedPaidMinutes(),
                getTimeSinceLastSync() / 1000,
                isCacheStale() ? "STALE" : "FRESH");
    }
}