package com.teletalker.app.features.authentication.presentation.fragments.login;

import com.teletalker.app.features.authentication.domain.repository.AuthRepository;

/**
 * Use case to check if user is logged in
 * Useful for splash screens and auto-login
 */
public class GetCurrentUserUseCase {

    private final AuthRepository repository;

    public GetCurrentUserUseCase(AuthRepository repository) {
        this.repository = repository;
    }

    /**
     * Check if user is logged in
     * @return true if user is authenticated, false otherwise
     */
    public boolean isUserLoggedIn() {
        return repository.isUserLoggedIn();
    }

    /**
     * Get current user ID
     * @return User ID if logged in, null otherwise
     */
    public String getCurrentUserId() {
        return repository.getCurrentUserId();
    }
}
