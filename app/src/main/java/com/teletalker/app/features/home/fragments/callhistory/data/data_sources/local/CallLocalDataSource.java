package com.teletalker.app.features.home.fragments.callhistory.data.data_sources.local;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;

import java.util.List;

public interface CallLocalDataSource {
    LiveData<List<CallEntity>> getCallHistory();

    void insertCall(CallEntity callEntity);

    void deleteCall(CallEntity callEntity);
    List<CallEntity> getRecentCallRecords();
    LiveData<List<CallEntity>> getLastTwoCallRecords();


}
