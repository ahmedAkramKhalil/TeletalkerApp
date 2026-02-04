package com.teletalker.app.features.authentication.presentation.fragments.verification;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.teletalker.app.R;
import com.teletalker.app.databinding.FragmentEmailVerificationBinding;

public class EmailVerificationFragment extends Fragment {

    private FragmentEmailVerificationBinding binding;
    private EmailVerificationViewModel viewModel;
    private NavController navController;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentEmailVerificationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeComponents(view);
        setupUI();
        observeState();
        initButtonClicks();
    }

    private void initializeComponents(View view) {
        navController = Navigation.findNavController(view);
        viewModel = new ViewModelProvider(this).get(EmailVerificationViewModel.class);
    }

    private void setupUI() {
        // Display user email
        String email = viewModel.getUserEmail();
        if (email != null) {
            binding.emailText.setText(email);
        }
    }

    private void observeState() {
        viewModel.state.observe(getViewLifecycleOwner(), state -> {
            if (state instanceof EmailVerificationState.Loading) {
                // Show loading
                binding.resendButton.setEnabled(false);
                binding.continueButton.setEnabled(false);
                binding.progressBar.setVisibility(View.VISIBLE);
            } else {
                // Hide loading
                binding.resendButton.setEnabled(true);
                binding.continueButton.setEnabled(true);
                binding.progressBar.setVisibility(View.GONE);

                if (state instanceof EmailVerificationState.Success) {
                    String message = ((EmailVerificationState.Success) state).getMessage();
                    Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                    viewModel.clearState();
                } else if (state instanceof EmailVerificationState.Error) {
                    String errorMsg = ((EmailVerificationState.Error) state).getMessage();
                    Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
                    viewModel.clearState();
                }
            }
        });

        viewModel.events.observe(getViewLifecycleOwner(), event -> {
            if (event instanceof EmailVerificationEvents.NavigateToLoginScreen) {
                navController.navigate(R.id.action_emailVerificationFragment_to_loginFragment);
                viewModel.clearNavigationState();
            } else if (event instanceof EmailVerificationEvents.PopBackStack) {
                navController.popBackStack();
                viewModel.clearNavigationState();
            }
        });
    }

    private void initButtonClicks() {
        binding.backButton.setOnClickListener(v -> viewModel.popBackStack());
        binding.resendButton.setOnClickListener(v -> viewModel.resendVerificationEmail());
        binding.continueButton.setOnClickListener(v -> viewModel.navigateToLogin());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}