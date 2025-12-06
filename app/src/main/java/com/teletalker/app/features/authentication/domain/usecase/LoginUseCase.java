package com.teletalker.app.features.authentication.domain.usecase;

import com.teletalker.app.features.authentication.domain.repository.AuthRepository;

public class LoginUseCase {

    private final AuthRepository repository;

    public LoginUseCase(AuthRepository repository) {
        this.repository = repository;
    }

    public void execute(String email, String password, AuthRepository.AuthCallback callback) {
        repository.login(email, password, callback);
    }
}
