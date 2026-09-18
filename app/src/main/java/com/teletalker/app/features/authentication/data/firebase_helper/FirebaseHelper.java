package com.teletalker.app.features.authentication.data.firebase_helper;

import android.util.Log;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.EmailAuthProvider;
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
                .addOnSuccessListener(authResult -> handleSignInResult(authResult, callback))
                .addOnFailureListener(e -> {
                    // On rooted / custom-ROM devices the reCAPTCHA component bundled with
                    // firebase-auth cannot initialize. The SDK reports that failure as a
                    // FirebaseNetworkException ("A network error ... has occurred") even
                    // though connectivity is fine — it fails within a few milliseconds,
                    // far too fast to be a real timeout. Retry through the credential
                    // path, which does not go through the reCAPTCHA call wrapper.
                    if (e instanceof FirebaseNetworkException) {
                        Log.w(TAG, "Primary sign-in reported a network error; "
                                + "retrying via credential path (custom-ROM workaround)", e);
                        loginWithCredential(email, password, e, callback);
                    } else {
                        Log.e(TAG, "Login failed", e);
                        callback.onError(getErrorMessage(e));
                    }
                });
    }

    /**
     * Fallback sign-in used when {@link #login} hits a FirebaseNetworkException.
     * Uses an explicit {@link EmailAuthProvider} credential, which follows a
     * different code path inside firebase-auth than signInWithPassword and so is
     * not wrapped by the reCAPTCHA verifier that fails on non-certified devices.
     *
     * @param originalError the failure from the primary attempt, reported to the
     *                      user if this fallback also fails (so we never mask the
     *                      real cause with a less specific message).
     */
    private void loginWithCredential(String email, String password,
                                     Exception originalError, AuthCallback callback) {
        AuthCredential credential = EmailAuthProvider.getCredential(email, password);
        auth.signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    Log.d(TAG, "✅ Credential fallback sign-in succeeded");
                    handleSignInResult(authResult, callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Credential fallback sign-in also failed", e);
                    // Prefer the more specific of the two errors. If the fallback
                    // produced a real auth error (wrong password, no such user),
                    // that is more useful than the original network-ish error.
                    Exception reported = (e instanceof FirebaseNetworkException) ? originalError : e;
                    callback.onError(getErrorMessage(reported));
                });
    }

    /** Shared post-sign-in handling: enforce email verification, then report. */
    private void handleSignInResult(AuthResult authResult, AuthCallback callback) {
        FirebaseUser user = authResult != null ? authResult.getUser() : null;
        if (user == null) {
            callback.onError("Login failed");
            return;
        }

        if (user.isEmailVerified()) {
            Log.d(TAG, "✅ Login successful - Email verified");
            callback.onSuccess(user);
        } else {
            Log.d(TAG, "❌ Login blocked - Email NOT verified");
            // Sign out immediately
            FirebaseAuth.getInstance().signOut();
            callback.onError("Please verify your email. Check your inbox or spam folder.");
        }
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
            // Seen on rooted / custom-ROM devices where the bundled reCAPTCHA
            // verifier cannot initialize; connectivity itself is usually fine, so
            // do not tell the user their internet is broken.
            return "Couldn't reach the sign-in service. If you're on a custom ROM, "
                    + "turn off Private DNS/VPN and try again";
        } else if (message.contains("too many requests")) {
            return "Too many attempts. Please try again later";
        }

        return message;
    }
}
