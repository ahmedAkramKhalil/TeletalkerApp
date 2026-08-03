package com.teletalker.app.features.home.fragments.callhistory.data.repository;

import androidx.lifecycle.LiveData;

import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;

import java.util.List;

public interface CallRepository {
    void insertCall(CallEntity callData);
    LiveData<List<CallEntity>> getCallHistory();
    void deleteCall(CallEntity callEntity);
    List<CallEntity> getRecentCallRecords();
    LiveData<List<CallEntity>> getLastTwoCallRecords();


}
