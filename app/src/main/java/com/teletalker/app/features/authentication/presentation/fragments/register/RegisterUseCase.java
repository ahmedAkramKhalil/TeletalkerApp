package com.teletalker.app.features.authentication.presentation.fragments.register;

import com.teletalker.app.features.authentication.domain.repository.AuthRepository;

/**
 * Use case for user registration
 * Handles the business logic for registering a new user
 */
public class RegisterUseCase {

    private final AuthRepository repository;

    public RegisterUseCase(AuthRepository repository) {
        this.repository = repository;
    }

    /**
     * Execute registration
     * @param email User's email address
     * @param password User's password (min 6 characters)
     * @param callback Callback to handle success/error
     */
    public void execute(String email, String password, AuthRepository.AuthCallback callback) {
        // Validate input before calling repository
        if (email == null || email.trim().isEmpty()) {
            callback.onError("Email is required");
            return;
        }

        if (!isValidEmail(email)) {
            callback.onError("Invalid email format");
            return;
        }

        if (password == null || password.length() < 6) {
            callback.onError("Password must be at least 6 characters");
            return;
        }

        // Call repository to perform registration
        repository.register(email, password, callback);
    }

    /**
     * Validate email format
     */
    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }
}
