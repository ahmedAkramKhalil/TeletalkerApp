package com.teletalker.app.features.authentication.presentation.fragments.register;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class RegisterViewModel extends ViewModel {
    final MutableLiveData<RegisterEvents> events = new MutableLiveData<>();

    public void navigateToSignInScreen() {
        events.setValue(RegisterEvents.NavigateToLoginScreen.INSTANCE);
    }

    public void popBackStack() {
        events.setValue(RegisterEvents.PopBackStack.INSTANCE);
    }

    public void clearNavigationState() {
        events.setValue(null);
    }
}
