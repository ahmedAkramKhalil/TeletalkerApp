package com.teletalker.app.features.authentication.presentation.fragments.login;

import com.teletalker.app.features.authentication.domain.repository.AuthRepository;

/**
 * Use case for user login
 * Handles the business logic for user authentication
 */
public class LoginUseCase {

    private final AuthRepository repository;

    public LoginUseCase(AuthRepository repository) {
        this.repository = repository;
    }

    /**
     * Execute login
     * @param email User's email address
     * @param password User's password
     * @param callback Callback to handle success/error
     */
    public void execute(String email, String password, AuthRepository.AuthCallback callback) {
        // Validate input before calling repository
        if (email == null || email.trim().isEmpty()) {
            callback.onError("Email is required");
            return;
        }

        if (password == null || password.trim().isEmpty()) {
            callback.onError("Password is required");
            return;
        }

        // Call repository to perform login
        repository.login(email, password, callback);
    }
}
