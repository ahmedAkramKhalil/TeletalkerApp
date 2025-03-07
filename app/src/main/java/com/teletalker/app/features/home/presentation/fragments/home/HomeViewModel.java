package com.teletalker.app.features.home.presentation.fragments.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class HomeViewModel extends ViewModel {
    final MutableLiveData<HomeFragmentEvents> events = new MutableLiveData<>();

    public void navigateToSubscriptionScreen() {
        events.setValue(HomeFragmentEvents.NavigateToSubscriptionScreen.INSTANCE);
    }

    public void clearNavigationState() {
        events.setValue(null);
    }
}