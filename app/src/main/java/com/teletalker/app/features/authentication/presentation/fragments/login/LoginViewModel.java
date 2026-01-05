package com.teletalker.app.features.authentication.presentation.fragments.login;


import android.util.Log;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.teletalker.app.features.authentication.data.data_source.remote.RemoteDataSourceImpl;
import com.teletalker.app.features.authentication.data.repository.AuthRepositoryImpl;
import com.teletalker.app.features.authentication.domain.repository.AuthRepository;
import com.teletalker.app.features.authentication.domain.usecase.LoginUseCase;
import com.teletalker.app.utils.PreferencesManager;

public class LoginViewModel extends ViewModel {

    final MutableLiveData<LoginEvents> events = new MutableLiveData<>();
    final MutableLiveData<LoginState> state = new MutableLiveData<>(LoginState.Idle.INSTANCE);

    private final LoginUseCase loginUseCase;

    public LoginViewModel() {
        // Initialize dependencies
        RemoteDataSourceImpl remoteDataSource = new RemoteDataSourceImpl();
        AuthRepository repository = new AuthRepositoryImpl(remoteDataSource);
        loginUseCase = new LoginUseCase(repository);
    }

    public void login(String email, String password) {
        // Set loading state
        state.setValue(LoginState.Loading.INSTANCE);

        loginUseCase.execute(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String userId, String email) {
                //TODO: remove log
                state.setValue(new LoginState.Success(userId, email));
                // Navigate to home after successful login
                events.setValue(LoginEvents.NavigateToHomeScreen.INSTANCE);
            }

            @Override
            public void onError(String error) {
                state.setValue(new LoginState.Error(error));
            }
        });
    }

    public void navigateToRegisterScreen() {
        events.setValue(LoginEvents.NavigateToRegisterScreen.INSTANCE);
    }

    public void popBackStack() {
        events.setValue(LoginEvents.PopBackStack.INSTANCE);
    }

    public void clearNavigationState() {
        events.setValue(null);
    }

    public void clearErrorState() {
        state.setValue(LoginState.Idle.INSTANCE);
    }
}

