package com.teletalker.app.features.authentication.domain.repository;

public interface AuthRepository {

    void login(String email, String password);

    void register(String email, String password);
}
