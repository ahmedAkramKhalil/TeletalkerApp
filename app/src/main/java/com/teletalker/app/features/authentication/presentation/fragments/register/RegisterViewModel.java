package com.teletalker.app.features.authentication.presentation.fragments.register;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.teletalker.app.features.authentication.data.data_source.remote.RemoteDataSourceImpl;
import com.teletalker.app.features.authentication.data.repository.AuthRepositoryImpl;
import com.teletalker.app.features.authentication.domain.repository.AuthRepository;

public class RegisterViewModel extends ViewModel {

    final MutableLiveData<RegisterEvents> events = new MutableLiveData<>();
    final MutableLiveData<RegisterState> state = new MutableLiveData<>(RegisterState.Idle.INSTANCE);

    private final RegisterUseCase registerUseCase;
    private final AuthRepository repository;

    public RegisterViewModel() {
        // Initialize dependencies
        RemoteDataSourceImpl remoteDataSource = new RemoteDataSourceImpl();
        repository = new AuthRepositoryImpl(remoteDataSource);
        registerUseCase = new RegisterUseCase(repository);
    }

    public void register(String email, String password, String passwordConfirmation) {
        // Validate password match
        if (!password.equals(passwordConfirmation)) {
            state.setValue(new RegisterState.Error("Passwords do not match"));
            return;
        }

        // Set loading state
        state.setValue(RegisterState.Loading.INSTANCE);

        registerUseCase.execute(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String userId, String email) {
                // Registration successful, now send verification email
                sendVerificationEmail(email);
            }

            @Override
            public void onError(String error) {
                state.setValue(new RegisterState.Error(error));
            }
        });
    }

    private void sendVerificationEmail(String email) {
        repository.sendEmailVerification(new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String userId, String userEmail) {
                // Verification email sent successfully
                state.setValue(new RegisterState.VerificationSent(email));
                // Navigate to verification screen
                events.setValue(RegisterEvents.NavigateToVerificationScreen.INSTANCE);
            }

            @Override
            public void onError(String error) {
                // Even if verification email fails, user is registered
                // Show error but still navigate to verification screen
                state.setValue(new RegisterState.Error("Account created but failed to send verification email: " + error));
                events.setValue(RegisterEvents.NavigateToVerificationScreen.INSTANCE);
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