package com.teletalker.app.features.authentication.presentation.fragments.register;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.teletalker.app.features.authentication.data.data_source.remote.RemoteDataSourceImpl;
import com.teletalker.app.features.authentication.data.repository.AuthRepositoryImpl;
import com.teletalker.app.features.authentication.domain.repository.AuthRepository;

public class RegisterViewModel extends ViewModel {

    final MutableLiveData<RegisterEvents> events = new MutableLiveData<>();
    final MutableLiveData<RegisterState> state = new MutableLiveData<>(RegisterState.Idle.INSTANCE);

    private final RegisterUseCase registerUseCase;

    public RegisterViewModel() {
        // Initialize dependencies
        RemoteDataSourceImpl remoteDataSource = new RemoteDataSourceImpl();
        AuthRepository repository = new AuthRepositoryImpl(remoteDataSource);
        registerUseCase = new RegisterUseCase(repository);
    }

    public void register(String email, String password, String passwordConfirmation) {
        // Set loading state

        if (!password.equals(passwordConfirmation)) {
            state.setValue(new RegisterState.Error("Passwords do not match"));
            return;
        }


        state.setValue(RegisterState.Loading.INSTANCE);

        registerUseCase.execute(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String userId, String email) {
                state.setValue(new RegisterState.Success(userId, email));
                // Navigate to login after successful registration
                events.setValue(RegisterEvents.NavigateToLoginScreen.INSTANCE);
            }

            @Override
            public void onError(String error) {
                state.setValue(new RegisterState.Error(error));
            }
        });
    }

    public void navigateToSignInScreen() {
        events.setValue(RegisterEvents.NavigateToLoginScreen.INSTANCE);
    }

    public void popBackStack() {
        events.setValue(RegisterEvents.PopBackStack.INSTANCE);
    }

    public void clearNavigationState() {
        events.setValue(null);
    }

    public void clearErrorState() {
        state.setValue(RegisterState.Idle.INSTANCE);
    }
}
