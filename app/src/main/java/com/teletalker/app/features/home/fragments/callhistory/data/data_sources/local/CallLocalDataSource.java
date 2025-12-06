package com.teletalker.app.features.home.fragments.callhistory.data.data_sources.local;

import android.content.Context;

import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;

import java.util.List;

public interface CallLocalDataSource {
    List<CallEntity> getCallHistory();

    void insertCall(CallEntity callEntity);

    void deleteCall(CallEntity callEntity);
    List<CallEntity> getLastTwoCallRecords();


}
