package com.teletalker.app.features.home.data.data_source;

import com.teletalker.app.features.home.data.models.CallHistoryModel;

import java.util.List;

public interface RemoteDataSource {
    List<CallHistoryModel> getCallHistory();
}
