package com.teletalker.app.features.home.data.repository;


import com.teletalker.app.features.home.data.models.CallHistoryModel;
import com.teletalker.app.features.home.domain.repository.CallHistoryRepository;

import java.util.Collections;
import java.util.List;

public class CallHistoryRepositoryImpl implements CallHistoryRepository {

    @Override
    public List<CallHistoryModel> getCallHistory() {
        return Collections.emptyList();
    }
}
