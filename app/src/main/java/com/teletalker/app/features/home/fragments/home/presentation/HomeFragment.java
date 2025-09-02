package com.teletalker.app.features.home.fragments.home.presentation;

import android.content.Intent;
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
import com.teletalker.app.databinding.FragmentHomeBinding;
import com.teletalker.app.features.agent_type.AgentTypeActivity;
import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;
import com.teletalker.app.features.home.fragments.settings.SettingsFragmentEvents;
import com.teletalker.app.features.subscription.presentation.SubscriptionActivity;
import com.teletalker.app.services.CallRecorderManager;
import com.teletalker.app.utils.PermissionUtils;
import com.teletalker.app.utils.PreferencesManager;
import com.teletalker.app.widgets.AIActivationButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class HomeFragment extends Fragment {


    private FragmentHomeBinding binding;
    private CallHistoryAdapter adapter;

    private NavController navController;

    private HomeViewModel homeViewModel;


    CallRecorderManager recorderManager;


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
        recorderManager.stopRecording();
        recorderManager.stopPreviousPlayback();


    }

    AtomicBoolean isBotActive = new AtomicBoolean(false);

    private void initListeners() {


        binding.subscribeButton.setOnClickListener(v -> homeViewModel.navigateToSubscriptionScreen());
        binding.seeAllHistoryTv.setOnClickListener(v -> homeViewModel.navigateToCallHistoryScreen());
        binding.changeAgentButton.setOnClickListener(v -> homeViewModel.navigateToChangeAgentScreen());

        binding.aiActivationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                binding.aiActivationButton.setImageResource(isBotActive.get() ? R.drawable.inactive_button : R.drawable.active_button);
                isBotActive.set(!isBotActive.get());
                PreferencesManager manager = PreferencesManager.getInstance(getContext());
                manager.setIsBotActive(isBotActive.get());
            }
        });

    }

    private void initializeVariables(@NonNull View view) {
        recorderManager = new CallRecorderManager(requireContext());
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        adapter = new CallHistoryAdapter(new ArrayList<>());
        binding.recyclerView.setAdapter(adapter);
        navController = Navigation.findNavController(view);
    }


    @Override
    public void onPause() {
        super.onPause();
        // Stop playback when fragment is paused
        homeViewModel.stopPlayback();
    }



    private void observes() {

        homeViewModel.getCallHistoryLiveData().observe(getViewLifecycleOwner(), callHistoryItems -> {
            adapter.updateData(callHistoryItems);
        });


        homeViewModel.events.observe(getViewLifecycleOwner(), event -> {
            if (event instanceof HomeFragmentEvents.NavigateToSubscriptionScreen) {
                Intent intent = new Intent(getActivity(), SubscriptionActivity.class);
                startActivity(intent);
                homeViewModel.clearNavigationState();
            } else if (event instanceof HomeFragmentEvents.NavigateToCallHistoryScreen) {
                navController.navigate(R.id.action_navigation_home_to_navigation_call_history);
                homeViewModel.clearNavigationState();
            } else if (event instanceof HomeFragmentEvents.NavigateToAgentTypeActivity) {
                Intent intent = new Intent(getActivity(), AgentTypeActivity.class);
                startActivity(intent);
                homeViewModel.clearNavigationState();
            }


        });
    }
}