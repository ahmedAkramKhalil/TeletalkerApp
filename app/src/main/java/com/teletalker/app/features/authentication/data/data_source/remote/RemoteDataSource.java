package com.teletalker.app.features.authentication.data.data_source.remote;

public interface RemoteDataSource {

    void login(String email, String password, AuthCallback callback);

    void register(String email, String password, AuthCallback callback);

    void logout(LogoutCallback callback);

    void resetPassword(String email, ResetPasswordCallback callback);

    boolean isUserLoggedIn();

    String getCurrentUserId();

    // Callbacks
    interface AuthCallback {
        void onSuccess(String userId, String email);
        void onError(String error);
    }

    interface LogoutCallback {
        void onSuccess();
    }

    interface ResetPasswordCallback {
        void onSuccess();
        void onError(String error);
    }
}
