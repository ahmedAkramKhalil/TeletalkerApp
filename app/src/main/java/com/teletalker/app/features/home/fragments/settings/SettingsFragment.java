package com.teletalker.app.features.home.fragments.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.teletalker.app.databinding.FragmentSettingsBinding;
import com.teletalker.app.features.select_voice.presentation.SelectVoiceActivity;
import com.teletalker.app.features.agent_type.AgentTypeActivity;
import com.teletalker.app.utils.PreferencesManager;

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

        setAutoAnswerSwitch();

    }

    private void setAutoAnswerSwitch() {

        binding.autoAnswer.setChecked(PreferencesManager.getInstance(getContext()).getBoolean(PreferencesManager.PREF_AUTO_ANSWER_ENABLED,true));
        binding.autoAnswer.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                configureAutoAnswer(getContext(),b);
            }
        });


    }

    public static void configureAutoAnswer(Context context, boolean enabled) {
        Log.d("configureAutoAnswer","AutoAnswer=="+enabled);
        PreferencesManager.getInstance(context).saveBoolean(PreferencesManager.PREF_AUTO_ANSWER_ENABLED , enabled);
    }


    private void initListeners() {
        binding.agentTypeContainer.setOnClickListener(l -> {
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