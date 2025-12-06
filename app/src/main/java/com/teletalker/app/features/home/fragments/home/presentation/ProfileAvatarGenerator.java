package com.teletalker.app.features.home.fragments.home.presentation;


import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.teletalker.app.R;

import java.util.Random;

/**
 * Utility class to generate profile avatars with initials
 */
public class ProfileAvatarGenerator {

    // Material Design colors for avatars
    private static final int[] AVATAR_COLORS = {
            0xFF1976D2, // Blue
            0xFFD32F2F, // Red
            0xFF388E3C, // Green
            0xFFF57C00, // Orange
            0xFF7B1FA2, // Purple
            0xFF0097A7, // Cyan
            0xFFC2185B, // Pink
            0xFF5D4037, // Brown
            0xFF455A64, // Blue Grey
            0xFF00796B  // Teal
    };

    /**
     * Generate initials from a name
     * @param name Full name or email
     * @return Initials (max 2 characters)
     */
    public static String getInitials(String name) {
        if (name == null || name.isEmpty()) {
            return "??";
        }

        // Remove email domain if it's an email
        if (name.contains("@")) {
            name = name.substring(0, name.indexOf("@"));
        }

        // Split name into parts
        String[] parts = name.trim().split("\\s+");

        if (parts.length == 0) {
            return "??";
        } else if (parts.length == 1) {
            // Single name - take first 2 characters
            String single = parts[0].toUpperCase();
            return single.length() >= 2 ? single.substring(0, 2) : single;
        } else {
            // Multiple names - take first letter of first and last name
            String first = parts[0];
            String last = parts[parts.length - 1];
            return (first.charAt(0) + "" + last.charAt(0)).toUpperCase();
        }
    }

    /**
     * Get a consistent color for a given name
     * @param name User's name
     * @return Color integer
     */
    public static int getColorForName(String name) {
        if (name == null || name.isEmpty()) {
            return AVATAR_COLORS[0];
        }

        // Use hash code to get consistent color for same name
        int hash = Math.abs(name.hashCode());
        int index = hash % AVATAR_COLORS.length;
        return AVATAR_COLORS[index];
    }

    /**
     * Setup profile avatar TextView with initials and color
     * @param textView TextView to setup
     * @param name User's name
     */
    public static void setupProfileAvatar(TextView textView, String name) {
        String initials = getInitials(name);
        int color = getColorForName(name);

        textView.setText(initials);
        textView.setBackgroundColor(color);
        textView.setTextColor(Color.WHITE);
    }

    /**
     * Setup profile avatar with custom color
     * @param textView TextView to setup
     * @param name User's name
     * @param backgroundColor Background color
     */
    public static void setupProfileAvatar(TextView textView, String name, int backgroundColor) {
        String initials = getInitials(name);

        textView.setText(initials);
        textView.setBackgroundColor(backgroundColor);
        textView.setTextColor(Color.WHITE);
    }

    /**
     * Create a circular gradient drawable for avatar
     * @param context Context
     * @param name User's name
     * @return GradientDrawable
     */
    public static GradientDrawable createAvatarDrawable(Context context, String name) {
        int color = getColorForName(name);

        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);

        return drawable;
    }

    /**
     * Get a lighter version of a color for gradient
     * @param color Base color
     * @return Lighter color
     */
    public static int getLighterColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.min(hsv[2] * 1.2f, 1.0f); // Increase brightness by 20%
        return Color.HSVToColor(hsv);
    }
}