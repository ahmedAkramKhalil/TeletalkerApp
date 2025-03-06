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
        navController = Navigation.findNavController(view);
        viewModel = new ViewModelProvider(this).get(RegisterViewModel.class);

        viewModel.events.observe(getViewLifecycleOwner(), state -> {

            if (state instanceof RegisterEvents.NavigateToLoginScreen) {
                navController.navigate(R.id.action_registerFragment_to_loginFragment);
                viewModel.clearNavigationState();
            }

            else if (state instanceof RegisterEvents.PopBackStack) {
                navController.popBackStack();
                viewModel.clearNavigationState();
            }
        });

        binding.signInButton.setOnClickListener(v -> viewModel.navigateToSignInScreen());
        binding.backButton.setOnClickListener(v -> viewModel.popBackStack());


    }
}