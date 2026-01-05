package com.teletalker.app.utils;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Manages authentication state using Firebase's built-in session
 * Firebase automatically handles encrypted token storage and persistence
 */
public class AuthStateManager {
    private static final String TAG = "AuthStateManager";

    private final FirebaseAuth auth;
    private final PreferencesManager prefs;
    private FirebaseAuth.AuthStateListener authStateListener;

    public AuthStateManager(Context context) {
        this.auth = FirebaseAuth.getInstance();
        this.prefs = PreferencesManager.getInstance(context);
    }

    /**
     * Check if user is logged in (Firebase session persists automatically)
     */
    public boolean isUserLoggedIn() {
        FirebaseUser user = auth.getCurrentUser();
        boolean isLoggedIn = user != null && !user.isAnonymous();

        if (isLoggedIn) {
            // Cache user info for offline display
            cacheUserInfo(user);
        }

        return isLoggedIn;
    }

    /**
     * Cache minimal user info (NO passwords - Firebase handles session)
     */
    private void cacheUserInfo(FirebaseUser user) {
        prefs.saveLastLoginInfo(user.getEmail(), user.getUid());
    }

    /**
     * Get cached user info (works offline)
     */
    public CachedUserInfo getCachedUserInfo() {
        // Try Firebase first
        FirebaseUser firebaseUser = auth.getCurrentUser();

        if (firebaseUser != null) {
            return new CachedUserInfo(
                    firebaseUser.getEmail(),
                    firebaseUser.getDisplayName(),
                    firebaseUser.getUid()
            );
        }

        // Fallback to cache (offline mode)
        return new CachedUserInfo(
                prefs.getLastLoginEmail(),
                prefs.getLastLoginEmail(), // Use email as name if no display name
                prefs.getLastLoginUid()
        );
    }

    /**
     * Setup auth state listener (detects login/logout)
     */
    public void setupAuthListener(AuthStateCallback callback) {
        authStateListener = firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();

            if (user != null && !user.isAnonymous()) {
                Log.d(TAG, "✅ User logged in: " + user.getEmail());
                cacheUserInfo(user);
                callback.onUserLoggedIn(user);
            } else {
                Log.d(TAG, "❌ User logged out");
                clearUserCache();
                callback.onUserLoggedOut();
            }
        };

        auth.addAuthStateListener(authStateListener);
    }

    /**
     * Remove auth listener (call in onDestroy)
     */
    public void removeAuthListener() {
        if (authStateListener != null) {
            auth.removeAuthStateListener(authStateListener);
        }
    }

    /**
     * Logout - Clear Firebase session and cache
     */
    public void logout() {
        Log.d(TAG, "🔐 Logging out user");
        auth.signOut();
        clearUserCache();
    }

    /**
     * Clear cached user data
     */
    private void clearUserCache() {
        prefs.clearLoginCache();
    }

    public interface AuthStateCallback {
        void onUserLoggedIn(FirebaseUser user);
        void onUserLoggedOut();
    }

    public static class CachedUserInfo {
        public final String email;
        public final String name;
        public final String uid;

        public CachedUserInfo(String email, String name, String uid) {
            this.email = email;
            this.name = name;
            this.uid = uid;
        }

        public boolean isValid() {
            return email != null && uid != null;
        }
    }
}