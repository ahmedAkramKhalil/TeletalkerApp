package com.teletalker.app.features.home.domain.repository;

import com.teletalker.app.features.home.data.models.CallHistoryModel;

import java.util.List;

public interface CallHistoryRepository {
    List<CallHistoryModel> getCallHistory();

}
