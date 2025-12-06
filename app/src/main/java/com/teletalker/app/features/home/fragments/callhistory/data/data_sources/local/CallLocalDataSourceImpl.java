package com.teletalker.app.features.home.fragments.callhistory.data.data_sources.local;

import android.content.Context;
import android.os.AsyncTask;

import com.teletalker.app.features.home.fragments.callhistory.data.data_sources.local.dao.CallDao;
import com.teletalker.app.features.home.fragments.callhistory.data.data_sources.local.database.CallDatabase;
import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CallLocalDataSourceImpl implements CallLocalDataSource {
    CallDao callDao;
    private ExecutorService executorService;
    public CallLocalDataSourceImpl(CallDao callDao) {
        this.callDao = callDao;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public List<CallEntity> getCallHistory() {
        return  callDao.getAllCalls();
    }

    @Override
    public void insertCall(CallEntity callEntity) {
        executorService.execute(() -> callDao.insertCall(callEntity));
    }

    public void deleteCall(CallEntity callEntity) {
        executorService.execute(() -> callDao.deleteCall(callEntity));
    }

    @Override
    public List<CallEntity> getLastTwoCallRecords() {
        return  callDao.getLastTwoCallRecords();
    }
}
