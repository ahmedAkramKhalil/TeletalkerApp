package com.teletalker.app.features.authentication.presentation.fragments.verification;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class EmailVerificationViewModel extends ViewModel {

    final MutableLiveData<EmailVerificationEvents> events = new MutableLiveData<>();
    final MutableLiveData<EmailVerificationState> state = new MutableLiveData<>(EmailVerificationState.Idle.INSTANCE);

    private final FirebaseAuth firebaseAuth;
    private String userEmail;

    public EmailVerificationViewModel() {
        firebaseAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            userEmail = currentUser.getEmail();
        }
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void resendVerificationEmail() {
        FirebaseUser user = firebaseAuth.getCurrentUser();

        if (user == null) {
            state.setValue(new EmailVerificationState.Error("User session expired. Please register again."));
            return;
        }

        state.setValue(EmailVerificationState.Loading.INSTANCE);

        user.sendEmailVerification()
                .addOnSuccessListener(aVoid -> {
                    state.setValue(new EmailVerificationState.Success("Verification email sent successfully!"));
                })
                .addOnFailureListener(e -> {
                    state.setValue(new EmailVerificationState.Error("Failed to send email: " + e.getMessage()));
                });
    }

    public void navigateToLogin() {
        events.setValue(EmailVerificationEvents.NavigateToLoginScreen.INSTANCE);
    }

    public void popBackStack() {
        events.setValue(EmailVerificationEvents.PopBackStack.INSTANCE);
    }

    public void clearNavigationState() {
        events.setValue(null);
    }

    public void clearState() {
        state.setValue(EmailVerificationState.Idle.INSTANCE);
    }
}