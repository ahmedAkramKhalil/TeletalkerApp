package com.teletalker.app.features.authentication.presentation.fragments.login;

import com.teletalker.app.features.authentication.domain.repository.AuthRepository;

public class LogoutUseCase {

    private final AuthRepository repository;

    public LogoutUseCase(AuthRepository repository) {
        this.repository = repository;
    }

    /**
     * Execute logout
     * @param callback Callback to handle success
     */
    public void execute(AuthRepository.LogoutCallback callback) {
        repository.logout(callback);
    }
}
