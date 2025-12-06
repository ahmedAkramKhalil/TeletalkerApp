package com.teletalker.app.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.teletalker.app.network.DeductMinutesResponse;
import com.teletalker.app.network.FirebaseFunctionsManager;

public class MinutesTracker {
    private static final String TAG = "MinutesTracker";
    private final Context context;
    private final PreferencesManager prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private double remainingMinutes;
    private long callStartTime;
    private boolean isTracking = false;
    private Runnable minuteChecker;
    private OnMinutesUpdateListener listener;

    public interface OnMinutesUpdateListener {
        void onMinutesUpdated(double remaining);
        void onMinutesExhausted();
    }

    public MinutesTracker(Context context) {
        this.context = context;
        this.prefs = PreferencesManager.getInstance(context);
    }

    public void setListener(OnMinutesUpdateListener listener) {
        this.listener = listener;
    }

    public void startTracking(double initialMinutes) {
        this.remainingMinutes = initialMinutes;
        this.callStartTime = System.currentTimeMillis();
        this.isTracking = true;

        minuteChecker = new Runnable() {
            @Override
            public void run() {
                if (!isTracking) return;

                long elapsed = System.currentTimeMillis() - callStartTime;
                double usedMinutes = elapsed / 60000.0; // Convert to minutes
                double remaining = initialMinutes - usedMinutes;

                if (remaining <= 0) {
                    stopTracking();
                    if (listener != null) {
                        listener.onMinutesExhausted();
                        ScheduledCallHelper.cancelAllScheduledCalls(context, "Insufficient minutes");

                    }

                } else {
                    if (listener != null) listener.onMinutesUpdated(remaining);
                    handler.postDelayed(this, 10000); // Check every 10 seconds
                }
            }
        };
        handler.post(minuteChecker);
    }

    public void stopTracking() {
        isTracking = false;
        handler.removeCallbacks(minuteChecker);
    }

    public double getUsedMinutes() {
        if (!isTracking) return 0;
        long elapsed = System.currentTimeMillis() - callStartTime;
        return elapsed / 60000.0;
    }

    public void updateFirebase(String phoneNumber, String recordingUrl) {
        double used = getUsedMinutes();
        FirebaseFunctionsManager.getInstance().deductMinutes(used, phoneNumber, recordingUrl,
                new FirebaseFunctionsManager.OnDeductMinutesCallback() {

                    @Override
                    public void onSuccess(DeductMinutesResponse response) {
                        Log.d(TAG, "Minutes updated: " + response.getNewBalance());

                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Failed to update minutes: " + error);
                    }
                });
    }

    public static boolean hasMinutes(Context context) {
        PreferencesManager prefs = PreferencesManager.getInstance(context);
        // Check cached minutes or fetch from Firebase
        return true; // Implement your logic
    }
}