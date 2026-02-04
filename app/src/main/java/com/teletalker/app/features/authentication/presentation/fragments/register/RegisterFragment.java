package com.teletalker.app.features.authentication.presentation.fragments.register;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.teletalker.app.R;
import com.teletalker.app.databinding.FragmentRegisterBinding;

public class RegisterFragment extends Fragment {
    FragmentRegisterBinding binding;
    private RegisterViewModel viewModel;
    NavController navController;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentRegisterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeComponents(view);
        observes();
        initButtonClicks();
    }

    void initializeComponents(View view) {
        navController = Navigation.findNavController(view);
        viewModel = new ViewModelProvider(this).get(RegisterViewModel.class);
    }

    void observes() {
        viewModel.state.observe(getViewLifecycleOwner(), state -> {
            if (state instanceof RegisterState.Loading) {
                // Disable buttons
                binding.signUpButton.setEnabled(false);
                binding.signInButton.setEnabled(false);
                // Show progress bar
                binding.progressBar.setVisibility(View.VISIBLE);
            } else {
                // Re-enable buttons
                binding.signUpButton.setEnabled(true);
                binding.signInButton.setEnabled(true);
                // Hide progress bar
                binding.progressBar.setVisibility(View.GONE);

                if (state instanceof RegisterState.Error) {
                    // Show error message to user
                    String errorMsg = ((RegisterState.Error) state).getMessage();
                    Toast.makeText(getContext(), errorMsg, Toast.LENGTH_SHORT).show();
                    binding.email.setError(errorMsg);
                    viewModel.clearErrorState();
                } else if (state instanceof RegisterState.VerificationSent) {
                    // Show success message
                    String email = ((RegisterState.VerificationSent) state).getEmail();
                    Toast.makeText(getContext(), "Verification email sent to " + email, Toast.LENGTH_LONG).show();
                }
            }
        });

        viewModel.events.observe(getViewLifecycleOwner(), state -> {
            if (state instanceof RegisterEvents.NavigateToLoginScreen) {
                navController.navigate(R.id.action_registerFragment_to_loginFragment);
                viewModel.clearNavigationState();
            } else if (state instanceof RegisterEvents.NavigateToVerificationScreen) {
                navController.navigate(R.id.action_registerFragment_to_emailVerificationFragment);
                viewModel.clearNavigationState();
            } else if (state instanceof RegisterEvents.PopBackStack) {
                navController.popBackStack();
                viewModel.clearNavigationState();
            }
        });
    }

    void initButtonClicks() {
        binding.signInButton.setOnClickListener(v -> viewModel.navigateToSignInScreen());
        binding.backButton.setOnClickListener(v -> viewModel.popBackStack());
        binding.signUpButton.setOnClickListener(v -> {
            viewModel.register(
                    binding.email.getText().toString(),
                    binding.password.getText().toString(),
                    binding.rePassword.getText().toString()
            );
        });
    }
}