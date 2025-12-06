package com.teletalker.app.features.home;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * ThemeManager - Switches between Lite and Standard themes
 *
 * HOW IT WORKS:
 * - Lite Version → Forces Light Mode → Uses values/colors.xml
 * - Standard Version → Forces Dark Mode → Uses values-night/colors.xml
 *
 * This automatically loads the correct color file!
 */
public class ThemeManager {

    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_APP_VERSION = "app_version";
    private static final String VERSION_LITE = "lite";
    private static final String VERSION_STANDARD = "standard";

    private static ThemeManager instance;
    private SharedPreferences prefs;
    private String currentVersion;

    // Private constructor for singleton
    private ThemeManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        currentVersion = prefs.getString(KEY_APP_VERSION, VERSION_LITE);
    }

    /**
     * Get ThemeManager instance (Singleton)
     */
    public static ThemeManager getInstance(Context context) {
        if (instance == null) {
            synchronized (ThemeManager.class) {
                if (instance == null) {
                    instance = new ThemeManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * Set app version and apply theme
     * @param version "lite" or "standard"
     */
    public void setAppVersion(String version) {
        if (version == null || (!version.equals(VERSION_LITE) && !version.equals(VERSION_STANDARD))) {
            version = VERSION_LITE; // Default to lite
        }

        currentVersion = version;
        prefs.edit().putString(KEY_APP_VERSION, version).apply();
        applyTheme();
    }

    /**
     * Get current app version
     * @return "lite" or "standard"
     */
    public String getAppVersion() {
        return currentVersion;
    }

    /**
     * Check if user has Standard version
     */
    public boolean isStandardVersion() {
        return VERSION_STANDARD.equals(currentVersion);
    }

    /**
     * Check if user has Lite version
     */
    public boolean isLiteVersion() {
        return VERSION_LITE.equals(currentVersion);
    }

    /**
     * Apply theme based on current version
     * This forces the app to use the correct color file
     */
    public void applyTheme() {
        if (isLiteVersion()) {
            // Lite → Light mode → values/colors.xml
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            // Standard → Dark mode → values-night/colors.xml
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
    }

    /**
     * Upgrade from Lite to Standard
     */
    public void upgradeToStandard() {
        setAppVersion(VERSION_STANDARD);
    }

    /**
     * Downgrade from Standard to Lite
     */
    public void downgradeToLite() {
        setAppVersion(VERSION_LITE);
    }

    /**
     * Get theme mode for display
     */
    public String getThemeDisplayName() {
        return isStandardVersion() ? "Dark Theme (Standard)" : "Light Theme (Lite)";
    }
}