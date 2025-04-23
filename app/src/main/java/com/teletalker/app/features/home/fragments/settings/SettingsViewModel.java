package com.teletalker.app.features.home.fragments.settings;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SettingsViewModel extends ViewModel {

    final MutableLiveData<SettingsFragmentEvents> events = new MutableLiveData<>();


    public void navigateToAgentTypeActivity() {
        events.setValue(SettingsFragmentEvents.NavigateToAgentTypeActivity.INSTANCE);
    }

    public void navigateToSelectVoiceActivity() {
        events.setValue(SettingsFragmentEvents.NavigateToSelectVoiceActivity.INSTANCE);

    }

}