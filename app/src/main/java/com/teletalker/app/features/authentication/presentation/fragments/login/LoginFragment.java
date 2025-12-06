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
        PreferencesManager pm = PreferencesManager.getInstance(getContext()) ;
        if (pm.isUserLoggedIn())
        {
            if (!pm.isLoginCredentialExist()){
                viewModel.login(pm.getUsername(), pm.getPassword());
            }else {
                Intent intent = new Intent(getActivity(), HomeActivity.class);
                startActivity(intent);
                viewModel.clearNavigationState();
            }
        }

    }


    void initializeComponents(View view){
        navController = Navigation.findNavController(view);
        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);
    }
    void observes(){

        viewModel.state.observe(getViewLifecycleOwner(), state -> {
            if (state instanceof LoginState.Loading) {
                // Disable buttons
                binding.loginButton.setEnabled(false);
                binding.loginButton.setEnabled(false);
                // Show progress bar (add ProgressBar to your layout)
                binding.progressBar.setVisibility(View.VISIBLE);
            }  else  if (state instanceof LoginState.Success) {

                PreferencesManager preferencesManager = PreferencesManager.getInstance(getContext());
                preferencesManager.setIsLoggedIn(true);
                preferencesManager.setUsername(((LoginState.Success) state).getEmail());
                preferencesManager.setUserID(((LoginState.Success) state).getUserId());
                preferencesManager.setPassword(((LoginState.Success) state).getPassword());

            } else {
                // Re-enable buttons
                binding.loginButton.setEnabled(true);
                binding.loginButton.setEnabled(true);
                // Hide progress bar
                binding.progressBar.setVisibility(View.GONE);
                if (state instanceof LoginState.Error) {
                    // Show error message to user
                    String errorMsg = ((LoginState.Error) state).getMessage();
                    if (errorMsg.contains("password")  ){
                        binding.password.setError(errorMsg); // or passwordLayout
                    }else {
                        binding.username.setError(errorMsg); // or passwordLayout
                    }
                    // Option 1: Toast
                    Toast.makeText(getContext(), errorMsg, Toast.LENGTH_SHORT).show();
                    // OR Option 2: Set error on TextInputLayout
                    viewModel.clearErrorState();
                }
            }
        });


        viewModel.events.observe(getViewLifecycleOwner(), state -> {
            if (state instanceof LoginEvents.NavigateToRegisterScreen) {
                navController.navigate(R.id.action_loginFragment_to_registerFragment);
                viewModel.clearNavigationState();
            }
            else if (state instanceof LoginEvents.PopBackStack) {
                navController.popBackStack();
                viewModel.clearNavigationState();
            }
            else if (state instanceof LoginEvents.NavigateToHomeScreen) {

                Intent intent = new Intent(getActivity(), HomeActivity.class);
                startActivity(intent);
                viewModel.clearNavigationState();
            }
        });
    }
    void  initButtonClicks(){
        binding.signUpButton.setOnClickListener(v -> viewModel.navigateToRegisterScreen());
        binding.backButton.setOnClickListener(v -> viewModel.popBackStack());
        binding.loginButton.setOnClickListener(v -> viewModel.login(binding.username.getText().toString(),binding.password.getText().toString()));
    }

}