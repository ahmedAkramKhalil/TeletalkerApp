package com.teletalker.app.features.select_voice.presentation;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.teletalker.app.R;
import com.teletalker.app.features.select_voice.data.models.TypeVoiceDM;

import java.util.ArrayList;
import java.util.List;

public class SelectVoiceViewModel extends ViewModel {
    public MutableLiveData<List<TypeVoiceDM>> typeVoicesList = new MutableLiveData<>();

    void getVoices() {
        typeVoicesList.setValue(getFakeData());
    }

    List<TypeVoiceDM> getFakeData() {
        List<TypeVoiceDM> fakeList = new ArrayList<>();
        fakeList.add(
                new TypeVoiceDM(
                        "Kathryn",
                        "Arabic",
                        R.drawable.kathryn_avatar
                )
        );

        fakeList.add(
                new TypeVoiceDM(
                        "Jacob",
                        "Chinese",
                        R.drawable.jacob_avatar
                )
        );

        fakeList.add(
                new TypeVoiceDM(
                        "Jane",
                        "Portuguese",
                        R.drawable.jane_avater
                )
        );

        fakeList.add(
                new TypeVoiceDM(
                        "Calvin",
                        "Spanish",
                        R.drawable.calvin_avatar
                )
        );

        fakeList.add(
                new TypeVoiceDM(
                        "Bruce",
                        "Arabic",
                        R.drawable.bruce_avatar
                )
        );

        fakeList.add(
                new TypeVoiceDM(
                        "Nathan",
                        "Spanish",
                        R.drawable.nathan_avatar
                )
        );


        return fakeList;
    }
}
