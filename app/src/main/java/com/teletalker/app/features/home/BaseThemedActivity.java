package com.teletalker.app.features.home;



import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;


/**
 * Base Activity that automatically uses the correct theme colors
 *
 * All your activities should extend this class
 * Colors automatically switch between:
 * - values/colors.xml (Lite/Light theme)
 * - values-night/colors.xml (Standard/Dark theme)
 */
public class BaseThemedActivity extends AppCompatActivity {

    protected ThemeManager themeManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Get theme manager instance
        themeManager = ThemeManager.getInstance(this);
        // Theme is applied globally via Application class
    }

    /**
     * Get current theme type
     * @return "lite" or "standard"
     */
    protected String getCurrentTheme() {
        return themeManager.getAppVersion();
    }

    /**
     * Check if using lite theme (light mode)
     */
    protected boolean isLiteTheme() {
        return themeManager.isLiteVersion();
    }

    /**
     * Check if using standard theme (dark mode)
     */
    protected boolean isStandardTheme() {
        return themeManager.isStandardVersion();
    }

    /**
     * Get theme name for display
     */
    protected String getThemeName() {
        return themeManager.getThemeDisplayName();
    }
}