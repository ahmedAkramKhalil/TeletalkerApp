package com.teletalker.app.features.authentication.data.repository;

import com.teletalker.app.features.authentication.domain.repository.AuthRepository;

import com.teletalker.app.features.authentication.data.data_source.remote.RemoteDataSource;
import com.teletalker.app.features.authentication.domain.repository.AuthRepository;

public class AuthRepositoryImpl implements AuthRepository {

    private final RemoteDataSource remoteDataSource;

    public AuthRepositoryImpl(RemoteDataSource remoteDataSource) {
        this.remoteDataSource = remoteDataSource;
    }

    @Override
    public void login(String email, String password, AuthCallback callback) {
        remoteDataSource.login(email, password, new RemoteDataSource.AuthCallback() {
            @Override
            public void onSuccess(String userId, String email) {
                callback.onSuccess(userId, email);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    @Override
    public void register(String email, String password, AuthCallback callback) {
        remoteDataSource.register(email, password, new RemoteDataSource.AuthCallback() {
            @Override
            public void onSuccess(String userId, String email) {
                callback.onSuccess(userId, email);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    @Override
    public void logout(LogoutCallback callback) {
        remoteDataSource.logout(() -> callback.onSuccess());
    }

    @Override
    public void resetPassword(String email, ResetPasswordCallback callback) {
        remoteDataSource.resetPassword(email, new RemoteDataSource.ResetPasswordCallback() {
            @Override
            public void onSuccess() {
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
        return remoteDataSource.isUserLoggedIn();
    }

    @Override
    public String getCurrentUserId() {
        return remoteDataSource.getCurrentUserId();
    }
}
