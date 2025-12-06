package com.teletalker.app.features.authentication.presentation.fragments.login;

import com.teletalker.app.features.authentication.domain.repository.AuthRepository;

/**
 * Use case for password reset
 * Handles the business logic for sending password reset emails
 */
public class ResetPasswordUseCase {

    private final AuthRepository repository;

    public ResetPasswordUseCase(AuthRepository repository) {
        this.repository = repository;
    }

    /**
     * Execute password reset
     * @param email User's email address
     * @param callback Callback to handle success/error
     */
    public void execute(String email, AuthRepository.ResetPasswordCallback callback) {
        // Validate email
        if (email == null || email.trim().isEmpty()) {
            callback.onError("Email is required");
            return;
        }

        if (!isValidEmail(email)) {
            callback.onError("Invalid email format");
            return;
        }

        // Call repository to send reset email
        repository.resetPassword(email, callback);
    }

    /**
     * Validate email format
     */
    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }
}
