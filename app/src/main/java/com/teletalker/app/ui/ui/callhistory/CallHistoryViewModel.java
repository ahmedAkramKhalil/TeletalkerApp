package com.teletalker.app.ui.ui.callhistory;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class CallHistoryViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public CallHistoryViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is dashboard fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}