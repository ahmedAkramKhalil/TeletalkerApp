package com.teletalker.app.widgets;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.teletalker.app.R;

public class AIActivationButton extends ConstraintLayout {

    private boolean isActive = false;
    private boolean isAnimating = false;

    // UI Components
    private View backgroundLayer1, backgroundLayer2, backgroundLayer3;
    private ConstraintLayout mainButton;
    private TextView statusText;
    private ImageView botIcon;
    private OnActivationChangeListener listener;

    // Animation objects
    private AnimatorSet pulseAnimatorSet;
    private ObjectAnimator rotationAnimator1, rotationAnimator2, rotationAnimator3;
    private ObjectAnimator scaleAnimator1, scaleAnimator2, scaleAnimator3;
    private ValueAnimator alphaAnimator1, alphaAnimator2, alphaAnimator3;

    // Colors
    private int activeColor;
    private int inactiveColor;
    private int textActiveColor;
    private int textInactiveColor;

    public interface OnActivationChangeListener {
        void onActivationChanged(boolean isActive);
    }

    public AIActivationButton(Context context) {
        super(context);
        init(context, null);
    }

    public AIActivationButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public AIActivationButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        // Inflate the layout
        inflate(context, R.layout.widget_ai_activation_button, this);

        // Initialize colors
        activeColor = ContextCompat.getColor(context, R.color.colorPrimary);
        inactiveColor = ContextCompat.getColor(context, R.color.gray);
        textActiveColor = ContextCompat.getColor(context, R.color.white);
        textInactiveColor = ContextCompat.getColor(context, R.color.gray_dark);

        // Get custom attributes if any
//        if (attrs != null) {
//            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AIActivationButton);
//            isActive = a.getBoolean(R.styleable.AIActivationButton_isActive, false);
//            a.recycle();
//        }

        initViews();
        setupAnimations();
        updateButtonState(false); // No animation on init

        // Set click listener
        setOnClickListener(v -> toggleActivation());
    }

    private void initViews() {
        backgroundLayer1 = findViewById(R.id.backgroundLayer1);
        backgroundLayer2 = findViewById(R.id.backgroundLayer2);
        backgroundLayer3 = findViewById(R.id.backgroundLayer3);
        mainButton = findViewById(R.id.mainButton);
        statusText = findViewById(R.id.statusText);
        botIcon = findViewById(R.id.botIcon);
    }

    private void setupAnimations() {
        // Pulse animation for main button
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(mainButton, "scaleX", 1.0f, 1.05f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(mainButton, "scaleY", 1.0f, 1.05f, 1.0f);
        scaleX.setDuration(2000);
        scaleY.setDuration(2000);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleY.setInterpolator(new AccelerateDecelerateInterpolator());

        pulseAnimatorSet = new AnimatorSet();
        pulseAnimatorSet.playTogether(scaleX, scaleY);

        // Background layers rotation animations
        rotationAnimator1 = ObjectAnimator.ofFloat(backgroundLayer1, "rotation", 0f, 360f);
        rotationAnimator1.setDuration(8000);
        rotationAnimator1.setRepeatCount(ValueAnimator.INFINITE);
        rotationAnimator1.setInterpolator(new AccelerateDecelerateInterpolator());

        rotationAnimator2 = ObjectAnimator.ofFloat(backgroundLayer2, "rotation", 0f, -360f);
        rotationAnimator2.setDuration(6000);
        rotationAnimator2.setRepeatCount(ValueAnimator.INFINITE);
        rotationAnimator2.setInterpolator(new AccelerateDecelerateInterpolator());

        rotationAnimator3 = ObjectAnimator.ofFloat(backgroundLayer3, "rotation", 0f, 360f);
        rotationAnimator3.setDuration(10000);
        rotationAnimator3.setRepeatCount(ValueAnimator.INFINITE);
        rotationAnimator3.setInterpolator(new AccelerateDecelerateInterpolator());

        // Scale animations for background layers
        scaleAnimator1 = ObjectAnimator.ofFloat(backgroundLayer1, "scaleX", 1.0f, 1.2f, 1.0f);
        scaleAnimator1.setDuration(4000);
        scaleAnimator1.setRepeatCount(ValueAnimator.INFINITE);

        ObjectAnimator scaleY1 = ObjectAnimator.ofFloat(backgroundLayer1, "scaleY", 1.0f, 1.2f, 1.0f);
        scaleY1.setDuration(4000);
        scaleY1.setRepeatCount(ValueAnimator.INFINITE);

        scaleAnimator2 = ObjectAnimator.ofFloat(backgroundLayer2, "scaleX", 1.0f, 1.15f, 1.0f);
        scaleAnimator2.setDuration(3500);
        scaleAnimator2.setRepeatCount(ValueAnimator.INFINITE);

        ObjectAnimator scaleY2 = ObjectAnimator.ofFloat(backgroundLayer2, "scaleY", 1.0f, 1.15f, 1.0f);
        scaleY2.setDuration(3500);
        scaleY2.setRepeatCount(ValueAnimator.INFINITE);

        scaleAnimator3 = ObjectAnimator.ofFloat(backgroundLayer3, "scaleX", 1.0f, 1.1f, 1.0f);
        scaleAnimator3.setDuration(5000);
        scaleAnimator3.setRepeatCount(ValueAnimator.INFINITE);

        ObjectAnimator scaleY3 = ObjectAnimator.ofFloat(backgroundLayer3, "scaleY", 1.0f, 1.1f, 1.0f);
        scaleY3.setDuration(5000);
        scaleY3.setRepeatCount(ValueAnimator.INFINITE);

        // Alpha animations for breathing effect
        alphaAnimator1 = ValueAnimator.ofFloat(0.3f, 0.6f, 0.3f);
        alphaAnimator1.setDuration(3000);
        alphaAnimator1.setRepeatCount(ValueAnimator.INFINITE);
        alphaAnimator1.addUpdateListener(animation ->
                backgroundLayer1.setAlpha((Float) animation.getAnimatedValue()));

        alphaAnimator2 = ValueAnimator.ofFloat(0.2f, 0.5f, 0.2f);
        alphaAnimator2.setDuration(2500);
        alphaAnimator2.setRepeatCount(ValueAnimator.INFINITE);
        alphaAnimator2.addUpdateListener(animation ->
                backgroundLayer2.setAlpha((Float) animation.getAnimatedValue()));

        alphaAnimator3 = ValueAnimator.ofFloat(0.1f, 0.4f, 0.1f);
        alphaAnimator3.setDuration(4000);
        alphaAnimator3.setRepeatCount(ValueAnimator.INFINITE);
        alphaAnimator3.addUpdateListener(animation ->
                backgroundLayer3.setAlpha((Float) animation.getAnimatedValue()));
    }

    public void toggleActivation() {
        setActive(!isActive);
    }

    public void setActive(boolean active) {
        if (this.isActive != active) {
            this.isActive = active;
            updateButtonState(true);

            if (listener != null) {
                listener.onActivationChanged(active);
            }
        }
    }

    public boolean isActive() {
        return isActive;
    }

    private void updateButtonState(boolean animate) {
        if (isActive) {
            activateButton(animate);
        } else {
            deactivateButton(animate);
        }
    }

    private void activateButton(boolean animate) {
        if (animate && !isAnimating) {
            isAnimating = true;

            // Show and animate background layers
            backgroundLayer1.setVisibility(VISIBLE);
            backgroundLayer2.setVisibility(VISIBLE);
            backgroundLayer3.setVisibility(VISIBLE);

            // Start animations
            pulseAnimatorSet.start();
            rotationAnimator1.start();
            rotationAnimator2.start();
            rotationAnimator3.start();
            scaleAnimator1.start();
            scaleAnimator2.start();
            scaleAnimator3.start();
            alphaAnimator1.start();
            alphaAnimator2.start();
            alphaAnimator3.start();

            // Update button appearance
            animateButtonAppearance(true);

        } else if (!animate) {
            // Set active state without animation
            backgroundLayer1.setVisibility(VISIBLE);
            backgroundLayer2.setVisibility(VISIBLE);
            backgroundLayer3.setVisibility(VISIBLE);
            setButtonAppearance(true);
        }

        // Update text and icon
        statusText.setText("Bot Active");
        botIcon.setImageResource(R.drawable.ic_bot_active);
    }

    private void deactivateButton(boolean animate) {
        if (animate && isAnimating) {
            // Stop animations
            pulseAnimatorSet.cancel();
            rotationAnimator1.cancel();
            rotationAnimator2.cancel();
            rotationAnimator3.cancel();
            scaleAnimator1.cancel();
            scaleAnimator2.cancel();
            scaleAnimator3.cancel();
            alphaAnimator1.cancel();
            alphaAnimator2.cancel();
            alphaAnimator3.cancel();

            // Animate button appearance
            animateButtonAppearance(false);

            // Hide background layers with fade out
            backgroundLayer1.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                backgroundLayer1.setVisibility(GONE);
                backgroundLayer1.setAlpha(1f);
            });

            backgroundLayer2.animate().alpha(0f).setDuration(400).withEndAction(() -> {
                backgroundLayer2.setVisibility(GONE);
                backgroundLayer2.setAlpha(1f);
            });

            backgroundLayer3.animate().alpha(0f).setDuration(500).withEndAction(() -> {
                backgroundLayer3.setVisibility(GONE);
                backgroundLayer3.setAlpha(1f);
                isAnimating = false;
            });

        } else if (!animate) {
            // Set inactive state without animation
            backgroundLayer1.setVisibility(GONE);
            backgroundLayer2.setVisibility(GONE);
            backgroundLayer3.setVisibility(GONE);
            setButtonAppearance(false);
            isAnimating = false;
        }

        // Update text and icon
        statusText.setText("Activate AI");
        botIcon.setImageResource(R.drawable.ic_bot_inactive);
    }

    private void animateButtonAppearance(boolean toActive) {
        int targetColor = toActive ? activeColor : inactiveColor;
        int targetTextColor = toActive ? textActiveColor : textInactiveColor;

        // Animate background color
        ValueAnimator colorAnimator = ValueAnimator.ofArgb(
                toActive ? inactiveColor : activeColor,
                targetColor
        );
        colorAnimator.setDuration(300);
        colorAnimator.addUpdateListener(animation -> {
            int color = (Integer) animation.getAnimatedValue();
            mainButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        });
        colorAnimator.start();

        // Animate text color
        ValueAnimator textColorAnimator = ValueAnimator.ofArgb(
                toActive ? textInactiveColor : textActiveColor,
                targetTextColor
        );
        textColorAnimator.setDuration(300);
        textColorAnimator.addUpdateListener(animation -> {
            int color = (Integer) animation.getAnimatedValue();
            statusText.setTextColor(color);
        });
        textColorAnimator.start();
    }

    private void setButtonAppearance(boolean active) {
        int color = active ? activeColor : inactiveColor;
        int textColor = active ? textActiveColor : textInactiveColor;

        mainButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        statusText.setTextColor(textColor);
    }

    public void setOnActivationChangeListener(OnActivationChangeListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // Clean up animations
        if (pulseAnimatorSet != null) {
            pulseAnimatorSet.cancel();
        }
        if (rotationAnimator1 != null) rotationAnimator1.cancel();
        if (rotationAnimator2 != null) rotationAnimator2.cancel();
        if (rotationAnimator3 != null) rotationAnimator3.cancel();
        if (scaleAnimator1 != null) scaleAnimator1.cancel();
        if (scaleAnimator2 != null) scaleAnimator2.cancel();
        if (scaleAnimator3 != null) scaleAnimator3.cancel();
        if (alphaAnimator1 != null) alphaAnimator1.cancel();
        if (alphaAnimator2 != null) alphaAnimator2.cancel();
        if (alphaAnimator3 != null) alphaAnimator3.cancel();
    }

    // Public methods for external control
    public void startAnimations() {
        if (isActive && !isAnimating) {
            activateButton(true);
        }
    }

    public void stopAnimations() {
        if (isAnimating) {
            deactivateButton(true);
        }
    }

    public void pauseAnimations() {
        if (pulseAnimatorSet != null) pulseAnimatorSet.pause();
        if (rotationAnimator1 != null) rotationAnimator1.pause();
        if (rotationAnimator2 != null) rotationAnimator2.pause();
        if (rotationAnimator3 != null) rotationAnimator3.pause();
        if (scaleAnimator1 != null) scaleAnimator1.pause();
        if (scaleAnimator2 != null) scaleAnimator2.pause();
        if (scaleAnimator3 != null) scaleAnimator3.pause();
        if (alphaAnimator1 != null) alphaAnimator1.pause();
        if (alphaAnimator2 != null) alphaAnimator2.pause();
        if (alphaAnimator3 != null) alphaAnimator3.pause();
    }

    public void resumeAnimations() {
        if (isActive) {
            if (pulseAnimatorSet != null) pulseAnimatorSet.resume();
            if (rotationAnimator1 != null) rotationAnimator1.resume();
            if (rotationAnimator2 != null) rotationAnimator2.resume();
            if (rotationAnimator3 != null) rotationAnimator3.resume();
            if (scaleAnimator1 != null) scaleAnimator1.resume();
            if (scaleAnimator2 != null) scaleAnimator2.resume();
            if (scaleAnimator3 != null) scaleAnimator3.resume();
            if (alphaAnimator1 != null) alphaAnimator1.resume();
            if (alphaAnimator2 != null) alphaAnimator2.resume();
            if (alphaAnimator3 != null) alphaAnimator3.resume();
        }
    }
}