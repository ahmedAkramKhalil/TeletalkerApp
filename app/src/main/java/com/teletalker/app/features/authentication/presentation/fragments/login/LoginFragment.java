package com.teletalker.app.features.authentication.presentation.fragments.login;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.api.LogDescriptor;
import com.teletalker.app.R;
import com.teletalker.app.databinding.FragmentLoginBinding;
import com.teletalker.app.features.home.HomeActivity;
import com.teletalker.app.utils.PreferencesManager;


public class LoginFragment extends Fragment {
    private LoginViewModel viewModel;
    FragmentLoginBinding binding;
    NavController navController;
    private PreferencesManager prefsManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeComponents(view);

        observes();

        initButtonClicks();

    }


    void initializeComponents(View view){
        navController = Navigation.findNavController(view);
        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);
        prefsManager = PreferencesManager.getInstance(requireContext());

    }
    void observes() {
        viewModel.state.observe(getViewLifecycleOwner(), state -> {
            if (state instanceof LoginState.Loading) {
                // Show loading state
                binding.loginButton.setEnabled(false);
                binding.signUpButton.setEnabled(false);
                binding.progressBar.setVisibility(View.VISIBLE);

            } else if (state instanceof LoginState.Success) {
                // Hide loading
                binding.progressBar.setVisibility(View.GONE);

                // ✅ CACHE LOGIN CREDENTIALS
                LoginState.Success success = (LoginState.Success) state;
//                prefsManager.saveLoginCredentials(
//                        success.getEmail(),
//                        success.getUserId(),
//                        success.getPassword()
//                );

                Log.d("TAG", "✅ Login successful, credentials cached");
                Toast.makeText(getContext(), "Login successful!", Toast.LENGTH_SHORT).show();

                // Navigate to home (this is handled by the event observer below)

            } else {
                // Re-enable buttons
                binding.loginButton.setEnabled(true);
                binding.signUpButton.setEnabled(true);
                binding.progressBar.setVisibility(View.GONE);

                if (state instanceof LoginState.Error) {
                    String errorMsg = ((LoginState.Error) state).getMessage();

                    // Show error
                    if (errorMsg.contains("password")) {
                        binding.password.setError(errorMsg);
                    } else {
                        binding.username.setError(errorMsg);
                    }

                    Toast.makeText(getContext(), errorMsg, Toast.LENGTH_SHORT).show();
                    viewModel.clearErrorState();
                }
            }
        });

        viewModel.events.observe(getViewLifecycleOwner(), event -> {
            if (event instanceof LoginEvents.NavigateToRegisterScreen) {
                navController.navigate(R.id.action_loginFragment_to_registerFragment);
                viewModel.clearNavigationState();

            } else if (event instanceof LoginEvents.PopBackStack) {
                navController.popBackStack();
                viewModel.clearNavigationState();

            } else if (event instanceof LoginEvents.NavigateToHomeScreen) {
                // Navigate to home
                Intent intent = new Intent(getActivity(), HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                requireActivity().finish(); // Close AuthActivity
                viewModel.clearNavigationState();
            }
        });
    }




    void initButtonClicks() {
        binding.signUpButton.setOnClickListener(v ->
                viewModel.navigateToRegisterScreen());

        binding.backButton.setOnClickListener(v ->
                viewModel.popBackStack());

        binding.loginButton.setOnClickListener(v ->
                viewModel.login(
                        binding.username.getText().toString(),
                        binding.password.getText().toString()
                ));
    }

}