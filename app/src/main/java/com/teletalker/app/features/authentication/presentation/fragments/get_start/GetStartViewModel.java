package com.teletalker.app.features.authentication.presentation.fragments.get_start;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class GetStartViewModel extends ViewModel {
    final MutableLiveData<GetStartEvents> events = new MutableLiveData<>();

    public void navigateToLoginScreen() {
        events.setValue(GetStartEvents.NavigateToLoginScreen.INSTANCE);
    }

    public void navigateToRegisterScreen() {
        events.setValue(GetStartEvents.NavigateToRegisterScreen.INSTANCE);
    }

    public void clearNavigationState() {
        events.setValue(null);
    }
}
