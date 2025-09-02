package com.teletalker.app.features.home.fragments.callhistory.data.repository;

import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;

import java.util.List;

public interface CallRepository {
    void insertCall(CallEntity callData);
    List<CallEntity> getCallHistory();
    void deleteCall(CallEntity callEntity);
    List<CallEntity> getLastTwoCallRecords();


}
