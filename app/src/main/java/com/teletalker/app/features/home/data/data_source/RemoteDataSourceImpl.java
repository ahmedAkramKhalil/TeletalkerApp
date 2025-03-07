package com.teletalker.app.features.home.data.data_source;

import com.teletalker.app.features.home.data.models.CallHistoryModel;

import java.util.Collections;
import java.util.List;

public class RemoteDataSourceImpl implements RemoteDataSource{

    @Override
    public List<CallHistoryModel> getCallHistory() {
        return Collections.emptyList();
    }
}
