package com.teletalker.app.features.authentication.presentation.fragments.get_start;

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
import com.teletalker.app.databinding.FragmentGetStartBinding;

public class GetStartFragment extends Fragment {
    private GetStartViewModel viewModel;
    FragmentGetStartBinding binding;
    NavController navController;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentGetStartBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);
        viewModel = new ViewModelProvider(this).get(GetStartViewModel.class);

        viewModel.events.observe(getViewLifecycleOwner(), state -> {
            if (state instanceof GetStartEvents.NavigateToLoginScreen) {
                navController.navigate(R.id.action_getStartedFragment_to_loginFragment);
                viewModel.clearNavigationState();
            }
            else if (state instanceof GetStartEvents.NavigateToRegisterScreen) {
                navController.navigate(R.id.action_getStartedFragment_to_registerFragment);
                viewModel.clearNavigationState();
            }
        });

        binding.signInButton.setOnClickListener(v -> viewModel.navigateToLoginScreen());
        binding.signUpButton.setOnClickListener(v -> viewModel.navigateToRegisterScreen());

    }
}