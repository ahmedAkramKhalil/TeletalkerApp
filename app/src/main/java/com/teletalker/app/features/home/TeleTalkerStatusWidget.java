package com.teletalker.app.features.home;


import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import com.teletalker.app.R;
import com.teletalker.app.services.ai.AICallRecorderRefactored;

public class TeleTalkerStatusWidget extends LinearLayout {
    private static final String TAG = "TeleTalkerStatusWidget";

    // Status change listener interface
    public interface StatusChangeListener {
        void onInternetStatusChanged(StatusModel.InternetStatus status);
        void onCallStatusChanged(StatusModel.CallState callState);
        void onAIStatusChanged(StatusModel.AIState aiState);
    }

    // UI Components
    private LinearLayout container;
    private LinearLayout internetLayout, callLayout, aiLayout;
    private ImageView internetIcon, callIcon, aiIcon;
    private TextView internetText, callText, callDuration, aiText, aiDetails;
    private View internetIndicator, callIndicator, aiIndicator;

    // Current states
    private StatusModel.InternetState internetState;
    private StatusModel.CallState callState;
    private StatusModel.AIState aiState;

    // Components
    private NetworkMonitor networkMonitor;
    private Handler uiHandler;
    private Runnable durationUpdater;
    private ValueAnimator currentAnimation;

    // Listener
    private StatusChangeListener statusChangeListener;

    public TeleTalkerStatusWidget(Context context) {
        super(context);
        init();
    }

    public TeleTalkerStatusWidget(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        LayoutInflater.from(getContext()).inflate(R.layout.widget_teletalker_status, this, true);

        initViews();
        initializeStates();
        setupNetworkMonitoring();

        uiHandler = new Handler(Looper.getMainLooper());
        updateUI();
    }

    private void initViews() {
        container = findViewById(R.id.status_container);

        // Internet status
        internetLayout = findViewById(R.id.internet_status_container);
        internetIcon = findViewById(R.id.internet_icon);
        internetText = findViewById(R.id.internet_status_text);
        internetIndicator = findViewById(R.id.internet_status_dot);

        // Call status
        callLayout = findViewById(R.id.call_status_container);
        callIcon = findViewById(R.id.call_icon);
        callText = findViewById(R.id.call_status_text);
        callDuration = findViewById(R.id.call_duration);
        callIndicator = findViewById(R.id.call_status_dot);

        // AI status
        aiLayout = findViewById(R.id.ai_status_container);
        aiIcon = findViewById(R.id.ai_icon);
        aiText = findViewById(R.id.ai_status_text);
        aiDetails = findViewById(R.id.ai_details);
        aiIndicator = findViewById(R.id.ai_status_dot);
    }

    private void initializeStates() {
        internetState = new StatusModel.InternetState(StatusModel.InternetStatus.DISCONNECTED);
        callState = new StatusModel.CallState(StatusModel.CallStatus.IDLE, "", "", 0);
        aiState = new StatusModel.AIState(StatusModel.AIStatus.HIDDEN, false, false, "");
    }

    private void setupNetworkMonitoring() {
        networkMonitor = new NetworkMonitor(getContext(), status -> {
            internetState = new StatusModel.InternetState(status);
            updateUI();
            notifyStatusChange();
        });
    }

    // Public API methods
    public void updateCallStatus(StatusModel.CallStatus status, String phoneNumber, String contactName) {
        long startTime = (status == StatusModel.CallStatus.ACTIVE && callState.status != StatusModel.CallStatus.ACTIVE)
                ? System.currentTimeMillis() : callState.startTime;

        callState = new StatusModel.CallState(status, phoneNumber, contactName, startTime);

        if (status == StatusModel.CallStatus.ACTIVE) {
            startDurationUpdater();
        } else if (status == StatusModel.CallStatus.IDLE) {
            stopDurationUpdater();
            // Hide AI status when call ends
            aiState = new StatusModel.AIState(StatusModel.AIStatus.HIDDEN, false, false, "");
        }

        updateUI();
        notifyStatusChange();
    }

    public void updateAIStatus(StatusModel.AIStatus status, boolean isRecording, boolean isInjecting, String lastResponse) {
        aiState = new StatusModel.AIState(status, isRecording, isInjecting, lastResponse);
        updateUI();
        notifyStatusChange();
    }

    public void setStatusChangeListener(StatusChangeListener listener) {
        this.statusChangeListener = listener;
    }

    // UI Update methods
    private void updateUI() {
        updateInternetUI();
        updateCallUI();
        updateAIUI();
    }

    private void updateInternetUI() {
        StatusModel.InternetStatus status = internetState.status;

        internetIcon.setImageResource(status.iconRes);
        internetIcon.setColorFilter(ContextCompat.getColor(getContext(), status.colorRes));
        internetText.setText(status.displayText);
        updateIndicator(internetIndicator, status.colorRes);
    }

    private void updateCallUI() {
        StatusModel.CallStatus status = callState.status;

        callLayout.setVisibility(status.isVisible ? VISIBLE : GONE);

        if (status.isVisible) {
            callIcon.setImageResource(status.iconRes);
            callIcon.setColorFilter(ContextCompat.getColor(getContext(), status.colorRes));
            callText.setText(status.displayText);
            updateIndicator(callIndicator, status.colorRes);

            if (status.showDuration) {
                String displayText = callState.getDisplayName();
                if (callState.getDuration() > 0) {
                    displayText += " • " + callState.getFormattedDuration();
                }
                callDuration.setText(displayText);
                callDuration.setVisibility(VISIBLE);

                // Animate for ringing
                if (status == StatusModel.CallStatus.RINGING) {
                    startIndicatorAnimation(callIndicator);
                } else {
                    stopIndicatorAnimation();
                }
            } else {
                callDuration.setVisibility(GONE);
            }
        }
    }

    private void updateAIUI() {
        StatusModel.AIStatus status = aiState.status;

        aiLayout.setVisibility(status.isVisible ? VISIBLE : GONE);

        if (status.isVisible) {
            aiIcon.setImageResource(status.iconRes);
            aiIcon.setColorFilter(ContextCompat.getColor(getContext(), status.colorRes));
            aiText.setText(status.displayText);
            aiDetails.setText(aiState.getDetailsText());
            updateIndicator(aiIndicator, status.colorRes);

            if (status.shouldAnimate) {
                startIndicatorAnimation(aiIndicator);
            } else {
                stopIndicatorAnimation();
            }
        }
    }

    private void updateIndicator(View indicator, int colorRes) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(ContextCompat.getColor(getContext(), colorRes));
        indicator.setBackground(drawable);
    }

    private void startIndicatorAnimation(View indicator) {
        stopIndicatorAnimation();

        currentAnimation = ValueAnimator.ofFloat(0.3f, 1.0f);
        currentAnimation.setDuration(1000);
        currentAnimation.setRepeatMode(ValueAnimator.REVERSE);
        currentAnimation.setRepeatCount(ValueAnimator.INFINITE);
        currentAnimation.addUpdateListener(animation -> {
            float alpha = (Float) animation.getAnimatedValue();
            indicator.setAlpha(alpha);
        });
        currentAnimation.start();
    }

    private void stopIndicatorAnimation() {
        if (currentAnimation != null) {
            currentAnimation.cancel();
            currentAnimation = null;
        }
        // Reset alpha for all indicators
        internetIndicator.setAlpha(1.0f);
        callIndicator.setAlpha(1.0f);
        aiIndicator.setAlpha(1.0f);
    }

    private void startDurationUpdater() {
        stopDurationUpdater();
        durationUpdater = new Runnable() {
            @Override
            public void run() {
                if (callState.status == StatusModel.CallStatus.ACTIVE ||
                        callState.status == StatusModel.CallStatus.HOLDING) {
                    updateCallUI();
                    uiHandler.postDelayed(this, 1000);
                }
            }
        };
        uiHandler.post(durationUpdater);
    }

    private void stopDurationUpdater() {
        if (durationUpdater != null) {
            uiHandler.removeCallbacks(durationUpdater);
            durationUpdater = null;
        }
    }

    private void notifyStatusChange() {
        if (statusChangeListener != null) {
            statusChangeListener.onInternetStatusChanged(internetState.status);
            statusChangeListener.onCallStatusChanged(callState);
            statusChangeListener.onAIStatusChanged(aiState);
        }
    }

    // Getters for current state
    public StatusModel.InternetState getInternetState() { return internetState; }
    public StatusModel.CallState getCallState() { return callState; }
    public StatusModel.AIState getAIState() { return aiState; }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cleanup();
    }

    private void cleanup() {
        if (networkMonitor != null) {
            networkMonitor.cleanup();
        }
        stopDurationUpdater();
        stopIndicatorAnimation();
    }
}
