package com.teletalker.app.features.authentication.presentation.fragments.login;


import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class LoginViewModel extends ViewModel {
    final MutableLiveData<LoginEvents> events = new MutableLiveData<>();


    public void navigateToRegisterScreen() {
        events.setValue(LoginEvents.NavigateToRegisterScreen.INSTANCE);
    }

    public void navigateToHomeScreen() {
        events.setValue(LoginEvents.NavigateToHomeScreen.INSTANCE);
    }
    public void popBackStack() {
        events.setValue(LoginEvents.PopBackStack.INSTANCE);
    }


    public void clearNavigationState() {
        events.setValue(null);
    }

}
