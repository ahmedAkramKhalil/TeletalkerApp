package com.teletalker.app.features.home.presentation.fragments.callhistory;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.teletalker.app.R;
import com.teletalker.app.features.home.data.models.CallHistoryModel;

import java.util.ArrayList;
import java.util.List;

public class CallHistoryViewModel extends ViewModel {
    final MutableLiveData<List<CallHistoryModel>> callHistoryList = new MutableLiveData<>();


    public void getCallHistoryList() {
        callHistoryList.setValue(getFakeData());
    }


    List<CallHistoryModel> getFakeData(){
        List<CallHistoryModel> fakeList = new ArrayList<>();
        fakeList.add(
                new CallHistoryModel(
                        1,
                        "John",
                        "2233234",
                        R.drawable.glays_image,
                        "English",
                        1,
                        R.drawable.ic_call_outgoing,
                        1,
                        "1:21"
                )

        );
        fakeList.add(
                new CallHistoryModel(
                        2,
                        "John",
                        "122333",
                        R.drawable.ic_call_incoming,
                        "Indin",
                        1,
                        R.drawable.ic_call_incoming,
                        1,
                        "1:21"
                )

        );

        fakeList.add(
                new CallHistoryModel(
                        3,
                        "John",
                        "084884",
                        R.drawable.glays_image,
                        "Indin",
                        1,
                        R.drawable.ic_call_slash,
                        1,
                        "1:21"
                )

        );
        fakeList.add(
                new CallHistoryModel(
                        1,
                        "John",
                        "1233434",
                        R.drawable.glays_image,
                        "Indin",
                        1,
                        R.drawable.ic_call_outgoing,
                        1,
                        "1:21"
                )

        );
        fakeList.add(
                new CallHistoryModel(
                        2,
                        "John",
                        "1233434",
                        R.drawable.ic_call_incoming,
                        "Indin",
                        1,
                        R.drawable.ic_call_incoming,
                        1,
                        "1:21"
                )

        );

        fakeList.add(
                new CallHistoryModel(
                        3,
                        "John",
                        "1233434",
                        R.drawable.glays_image,
                        "Indin",
                        1,
                        R.drawable.ic_call_slash,
                        1,
                        "1:21"
                )

        );

        fakeList.add(
                new CallHistoryModel(
                        1,
                        "John",
                        "1233434",
                        R.drawable.glays_image,
                        "Indin",
                        1,
                        R.drawable.ic_call_outgoing,
                        1,
                        "1:21"
                )

        );
        fakeList.add(
                new CallHistoryModel(
                        2,
                        "John",
                        "1233434",
                        R.drawable.ic_call_incoming,
                        "Indin",
                        1,
                        R.drawable.ic_call_incoming,
                        1,
                        "1:21"
                )

        );

        fakeList.add(
                new CallHistoryModel(
                        3,
                        "John",
                        "1233434",
                        R.drawable.glays_image,
                        "Indin",
                        1,
                        R.drawable.ic_call_slash,
                        1,
                        "1:21"
                )

        );

        return fakeList;
    }

}