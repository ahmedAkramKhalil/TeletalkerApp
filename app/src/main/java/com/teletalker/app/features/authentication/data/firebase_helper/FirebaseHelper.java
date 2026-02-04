package com.teletalker.app.features.authentication.data.firebase_helper;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class FirebaseHelper {
    private static final String TAG = "FirebaseHelper";

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    public FirebaseHelper() {
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
    }

    // ============================================
    // AUTH CALLBACKS
    // ============================================

    public interface AuthCallback {
        void onSuccess(FirebaseUser user);

        void onError(String error);
    }

    public interface LogoutCallback {
        void onSuccess();
    }

    // ============================================
    // REGISTER
    // ============================================

    public void register(String email, String password, AuthCallback callback) {
        if (email == null || email.isEmpty()) {
            callback.onError("Email is required");
            return;
        }

        if (password == null || password.length() < 6) {
            callback.onError("Password must be at least 6 characters");
            return;
        }

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        // Create user document in Firestore
                        createUserDocument(user, callback);
                    } else {
                        callback.onError("User creation failed");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Registration failed", e);
                    callback.onError(getErrorMessage(e));
                });
    }

    // ============================================
    // LOGIN
    // ============================================

    public void login(String email, String password, AuthCallback callback) {
        if (email == null || email.isEmpty()) {
            callback.onError("Email is required");
            return;
        }

        if (password == null || password.isEmpty()) {
            callback.onError("Password is required");
            return;
        }

        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        if (user.isEmailVerified()) {
                            Log.d(TAG, "✅ Login successful - Email verified");
                            callback.onSuccess(user);
                        } else {
                            Log.d(TAG, "❌ Login blocked - Email NOT verified");
                            // Sign out immediately
                            FirebaseAuth.getInstance().signOut();
                            callback.onError("Please verify your email. Check your inbox or spam folder.");
                        }
                        callback.onSuccess(user);
                    } else {
                        callback.onError("Login failed");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Login failed", e);
                    callback.onError(getErrorMessage(e));
                });
    }

    // ============================================
    // LOGOUT
    // ============================================

    public void logout(LogoutCallback callback) {
        auth.signOut();
        callback.onSuccess();
    }

    // ============================================
    // GET CURRENT USER
    // ============================================

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public boolean isUserLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    // ============================================
    // SEND PASSWORD RESET EMAIL
    // ============================================

    public void sendPasswordResetEmail(String email, AuthCallback callback) {
        if (email == null || email.isEmpty()) {
            callback.onError("Email is required");
            return;
        }

        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> {
                    callback.onSuccess(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Password reset failed", e);
                    callback.onError(getErrorMessage(e));
                });
    }

    // ============================================
    // CREATE USER DOCUMENT IN FIRESTORE
    // ============================================

    private void createUserDocument(FirebaseUser user, AuthCallback callback) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("email", user.getEmail());
        userData.put("createdAt", System.currentTimeMillis());

        firestore.collection("users")
                .document(user.getUid())
                .set(userData)
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "User document created successfully");
                    // Cloud Function will automatically add free minutes via onusercreate trigger
                    callback.onSuccess(user);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create user document", e);
                    // Still return success since auth was successful
                    // User document can be created later
                    callback.onSuccess(user);
                });
    }

    // ============================================
    // ERROR MESSAGE HELPER
    // ============================================

    private String getErrorMessage(Exception e) {
        String message = e.getMessage();

        if (message == null) {
            return "An error occurred";
        }

        // Parse Firebase error messages
        if (message.contains("There is no user record")) {
            return "No account found with this email";
        } else if (message.contains("The password is invalid")) {
            return "Invalid password";
        } else if (message.contains("The email address is already in use")) {
            return "Email already registered";
        } else if (message.contains("The email address is badly formatted")) {
            return "Invalid email format";
        } else if (message.contains("network error")) {
            return "Network error. Please check your connection";
        } else if (message.contains("too many requests")) {
            return "Too many attempts. Please try again later";
        }

        return message;
    }
}
