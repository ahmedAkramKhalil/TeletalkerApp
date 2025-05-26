package com.teletalker.app.features.home.fragments.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.teletalker.app.databinding.FragmentSettingsBinding;
import com.teletalker.app.features.select_voice.presentation.SelectVoiceActivity;
import com.teletalker.app.features.agent_type.AgentTypeActivity;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SettingsViewModel viewModel;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        observes();

        initListeners();

    }

    private void initListeners() {
        binding.arrowAgentType.setOnClickListener(l -> {
            viewModel.navigateToAgentTypeActivity();
        });
        binding.arrowAgentTypeVoice.setOnClickListener(l -> {
            viewModel.navigateToSelectVoiceActivity();
        });
    }

    private void observes() {
        viewModel.events.observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            if (event instanceof SettingsFragmentEvents.NavigateToAgentTypeActivity) {
                Intent intent = new Intent(getActivity(), AgentTypeActivity.class);
                startActivity(intent);
                viewModel.events.setValue(null);
            }
            else if (event instanceof SettingsFragmentEvents.NavigateToSelectVoiceActivity) {
                Intent intent = new Intent(getActivity(), SelectVoiceActivity.class);
                startActivity(intent);
                viewModel.events.setValue(null);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}