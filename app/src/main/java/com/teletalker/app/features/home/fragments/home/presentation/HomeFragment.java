package com.teletalker.app.features.home.fragments.home.presentation;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.teletalker.app.R;
import com.teletalker.app.databinding.FragmentHomeBinding;
import com.teletalker.app.features.subscription.presentation.SubscriptionActivity;

import java.util.List;

public class HomeFragment extends Fragment {


    private FragmentHomeBinding binding;
    private CallHistoryAdapter adapter;

    private NavController navController;

    private HomeViewModel homeViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeVariables(view);

        initListeners();

        observes();

    }



    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void initListeners() {
        binding.subscribeButton.setOnClickListener(v -> homeViewModel.navigateToSubscriptionScreen());
        binding.seeAllHistoryTv.setOnClickListener(v -> homeViewModel.navigateToCallHistoryScreen());
    }

    private void initializeVariables(@NonNull View view) {
        adapter = new CallHistoryAdapter(List.of());
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        binding.recyclerView.setAdapter(adapter);
        navController = Navigation.findNavController(view);
    }

    private void observes() {
        homeViewModel.events.observe(getViewLifecycleOwner(), event -> {
            if (event instanceof HomeFragmentEvents.NavigateToSubscriptionScreen) {
                Intent intent = new Intent(getActivity(), SubscriptionActivity.class);
                startActivity(intent);
                homeViewModel.clearNavigationState();
            }else if (event instanceof HomeFragmentEvents.NavigateToCallHistoryScreen) {
                navController.navigate(R.id.action_navigation_home_to_navigation_call_history);
                homeViewModel.clearNavigationState();
            }
        });
    }
}