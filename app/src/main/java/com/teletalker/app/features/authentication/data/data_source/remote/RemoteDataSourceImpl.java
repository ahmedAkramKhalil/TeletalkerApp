package com.teletalker.app.features.authentication.data.data_source.remote;

import com.google.firebase.auth.FirebaseUser;
import com.teletalker.app.features.authentication.data.firebase_helper.FirebaseHelper;

public class RemoteDataSourceImpl implements RemoteDataSource {

    private final FirebaseHelper firebaseHelper;

    public RemoteDataSourceImpl() {
        this.firebaseHelper = new FirebaseHelper();
    }

    @Override
    public void login(String email, String password, AuthCallback callback) {
        firebaseHelper.login(email, password, new FirebaseHelper.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {

                callback.onSuccess(user.getUid(), user.getEmail());
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    @Override
    public void register(String email, String password, AuthCallback callback) {
        firebaseHelper.register(email, password, new FirebaseHelper.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                callback.onSuccess(user.getUid(), user.getEmail());
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    @Override
    public void logout(LogoutCallback callback) {
        firebaseHelper.logout(() -> callback.onSuccess());
    }

    @Override
    public void resetPassword(String email, ResetPasswordCallback callback) {
        firebaseHelper.sendPasswordResetEmail(email, new FirebaseHelper.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                callback.onSuccess();
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    @Override
    public boolean isUserLoggedIn() {
        return firebaseHelper.isUserLoggedIn();
    }

    @Override
    public String getCurrentUserId() {
        return firebaseHelper.getCurrentUserId();
    }
}
