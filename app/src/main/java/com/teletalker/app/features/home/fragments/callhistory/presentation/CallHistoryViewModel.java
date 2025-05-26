package com.teletalker.app.features.home.fragments.callhistory.presentation;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.teletalker.app.features.home.fragments.callhistory.data.data_sources.local.CallLocalDataSourceImpl;
import com.teletalker.app.features.home.fragments.callhistory.data.data_sources.local.dao.CallDao;
import com.teletalker.app.features.home.fragments.callhistory.data.data_sources.local.database.CallDatabase;
import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;
import com.teletalker.app.features.home.fragments.callhistory.data.repository.CallRepository;
import com.teletalker.app.features.home.fragments.callhistory.data.repository.CallRepositoryImpl;

import java.util.List;
import java.util.concurrent.Executors;

public class CallHistoryViewModel extends AndroidViewModel {
    private final MutableLiveData<List<CallEntity>> callHistoryLiveData = new MutableLiveData<>();
    private  CallRepository callRepository;
    public CallHistoryViewModel(Application application) {
        super(application);
        CallDao callDao = CallDatabase.getInstance(application).callDao();
        callRepository = new CallRepositoryImpl(new CallLocalDataSourceImpl(callDao));
        loadCallHistory();
    }


    private void loadCallHistory() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<CallEntity> callHistory = callRepository.getCallHistory();
            callHistoryLiveData.postValue(callHistory);
        });
    }


    public LiveData<List<CallEntity>> getCallHistoryLiveData() {
        return callHistoryLiveData;
    }

}