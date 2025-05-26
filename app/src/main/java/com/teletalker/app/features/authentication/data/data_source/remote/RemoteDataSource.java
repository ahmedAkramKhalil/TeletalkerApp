package com.teletalker.app.features.authentication.data.data_source.remote;

public interface RemoteDataSource {

    void login(String email, String password);

    void register(String email, String password);

}
