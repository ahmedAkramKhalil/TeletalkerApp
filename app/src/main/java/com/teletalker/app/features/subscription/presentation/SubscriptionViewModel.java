package com.teletalker.app.features.subscription.presentation;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SubscriptionViewModel extends ViewModel{
    final MutableLiveData<SubscriptionEvents> events = new MutableLiveData<>();
    public void popBackStack() {
        events.setValue(SubscriptionEvents.PopBackStack.INSTANCE);
    }

}
