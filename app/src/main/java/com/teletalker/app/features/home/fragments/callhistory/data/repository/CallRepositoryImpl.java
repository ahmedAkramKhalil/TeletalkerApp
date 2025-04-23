package com.teletalker.app.features.home.fragments.callhistory.data.repository;

import android.app.Application;

import com.teletalker.app.features.home.fragments.callhistory.data.data_sources.local.CallLocalDataSource;
import com.teletalker.app.features.home.fragments.callhistory.data.data_sources.local.CallLocalDataSourceImpl;
import com.teletalker.app.features.home.fragments.callhistory.data.data_sources.local.database.CallDatabase;
import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;

import java.util.List;
import java.util.concurrent.Executors;

public class CallRepositoryImpl implements CallRepository {
    private final CallLocalDataSource callLocalDataSource;

    public CallRepositoryImpl(CallLocalDataSource callLocalDataSource) {
        this.callLocalDataSource = callLocalDataSource;

    }

    @Override
    public void insertCall(CallEntity callData) {
        Executors.newSingleThreadExecutor().execute(()
                -> callLocalDataSource.insertCall(callData));
    }

    @Override
    public List<CallEntity> getCallHistory() {
        return callLocalDataSource.getCallHistory();
    }

    public void deleteCall(CallEntity callEntity) {
        Executors.newSingleThreadExecutor().execute(()
                -> callLocalDataSource.deleteCall(callEntity));
    }
}
