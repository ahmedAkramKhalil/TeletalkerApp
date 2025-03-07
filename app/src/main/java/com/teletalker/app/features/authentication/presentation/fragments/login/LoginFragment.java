package com.teletalker.app.features.authentication.presentation.fragments.login;

import android.content.Intent;
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

import com.teletalker.app.R;
import com.teletalker.app.databinding.FragmentLoginBinding;
import com.teletalker.app.features.home.presentation.HomeActivity;


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
    }


    void initializeComponents(View view){
        navController = Navigation.findNavController(view);
        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);
    }
    void observes(){
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
        binding.subscribeButton.setOnClickListener(v -> viewModel.navigateToHomeScreen());
    }

}