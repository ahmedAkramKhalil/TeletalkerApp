package com.teletalker.app.features.home.fragments.settings;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.teletalker.app.features.authentication.data.data_source.remote.RemoteDataSourceImpl;
import com.teletalker.app.features.authentication.data.repository.AuthRepositoryImpl;
import com.teletalker.app.features.authentication.domain.repository.AuthRepository;
import com.teletalker.app.features.authentication.domain.usecase.LoginUseCase;
import com.teletalker.app.features.authentication.presentation.fragments.login.LogoutUseCase;

public class SettingsViewModel extends ViewModel {

    public final MutableLiveData<SettingsFragmentEvents> events = new MutableLiveData<>();
    private final LogoutUseCase logoutUseCase;

    public SettingsViewModel() {
        RemoteDataSourceImpl remoteDataSource = new RemoteDataSourceImpl();
        AuthRepository repository = new AuthRepositoryImpl(remoteDataSource);
        logoutUseCase = new LogoutUseCase(repository);
    }

    public void navigateToAgentTypeActivity() {
        events.setValue(SettingsFragmentEvents.NavigateToAgentTypeActivity.INSTANCE);
    }

    public void navigateToSelectVoiceActivity() {
        events.setValue(SettingsFragmentEvents.NavigateToSelectVoiceActivity.INSTANCE);
    }

    // NEW METHODS
    public void navigateToSubscriptionActivity() {
        events.setValue(SettingsFragmentEvents.NavigateToSubscriptionActivity.INSTANCE);
    }

    public void logout() {
        logoutUseCase.execute(() -> {
            events.setValue(SettingsFragmentEvents.Logout.INSTANCE);
        });
    }
}

