// ============================================
// SettingsFragment.java - Using Your Existing Design
// ============================================

package com.teletalker.app.features.home.fragments.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.teletalker.app.databinding.FragmentSettingsBinding;
import com.teletalker.app.features.agent_type.AgentTypeActivity;
import com.teletalker.app.features.authentication.presentation.AuthActivity;
import com.teletalker.app.features.select_voice.presentation.SelectVoiceActivity;
import com.teletalker.app.network.FirebaseFunctionsManager;
import com.teletalker.app.network.UserBalance;
import com.teletalker.app.utils.PreferencesManager;

import java.util.Locale;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SettingsViewModel viewModel;
    private FirebaseFunctionsManager functionsManager;
    private FirebaseAuth auth;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
        binding = FragmentSettingsBinding.inflate(inflater, container, false);

        // Initialize Firebaser
        functionsManager = FirebaseFunctionsManager.getInstance();
        auth = FirebaseAuth.getInstance();

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        observes();
        initListeners();
        setAutoAnswerSwitch();

        // Load user profile
        loadUserProfile();
    }

    // ============================================
    // LOAD USER PROFILE & BALANCE
    // ============================================

    private void loadUserProfile() {
        // Get current user info
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            // Display email
            String email = currentUser.getEmail();
            if (email != null) {
                binding.textView14.setText(email);
            }

            // Set name from email (first part before @)
            if (email != null && email.contains("@")) {
                String username = email.split("@")[0];
                binding.nameTv.setText(username);
            }
        }

        // Load balance
        functionsManager.getBalance(new FirebaseFunctionsManager.OnBalanceCallback() {
            @Override
            public void onSuccess(UserBalance balance) {
                updateBalanceDisplay(balance);
            }

            @Override
            public void onError(String error) {
                Log.e("SettingsFragment", "Error loading balance: " + error);
            }
        });
    }

    private void openInvoicesActivity() {
        Intent intent = new Intent(getActivity(), InvoicesActivity.class);
        startActivity(intent);
    }



    // ============================================
    // UPDATE BALANCE DISPLAY
    // ============================================

    private void updateBalanceDisplay(UserBalance balance) {
        // Update the name to show balance info
        String displayText = String.format(Locale.US,
                "%.1f minutes | %s",
                balance.getMinutesBalance(),
                balance.getAppVersion().toUpperCase());

        binding.nameTv.setText(displayText);

        // Update Payment & Membership text to show spent
        String paymentText = String.format(Locale.US,
                "Payment & Membership ($%.2f spent)",
                balance.getTotalSpent());
        binding.textView16.setText(paymentText);
    }

    // ============================================
    // AUTO ANSWER SWITCH
    // ============================================

    private void setAutoAnswerSwitch() {
        binding.autoAnswer.setChecked(
                PreferencesManager.getInstance(getContext())
                        .getBoolean(PreferencesManager.PREF_AUTO_ANSWER_ENABLED, true)
        );

        binding.autoAnswer.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                configureAutoAnswer(getContext(), b);
            }
        });
    }

    public static void configureAutoAnswer(Context context, boolean enabled) {
        Log.d("configureAutoAnswer", "AutoAnswer==" + enabled);
        PreferencesManager.getInstance(context)
                .saveBoolean(PreferencesManager.PREF_AUTO_ANSWER_ENABLED, enabled);
    }

    // ============================================
    // CLICK LISTENERS
    // ============================================

    private void initListeners() {
        // Profile card - navigate to subscription
        binding.materialCardView3.setOnClickListener(v ->
                viewModel.navigateToSubscriptionActivity()
        );

        // Payment & Membership
        binding.materialCardView4.setOnClickListener(v ->
                viewModel.navigateToSubscriptionActivity()
        );

        // Agent type
        binding.agentTypeContainer.setOnClickListener(v ->
                viewModel.navigateToAgentTypeActivity()
        );

        // Agent voice (if you want to enable it)
        binding.arrowAgentTypeVoice.setOnClickListener(v ->
                viewModel.navigateToSelectVoiceActivity()
        );

        // Logout
        binding.logoutContainer.setOnClickListener(v ->
                showLogoutConfirmation()
        );

        binding.materialCardView7.setOnClickListener( v -> openInvoicesActivity());
    }

    // ============================================
    // LOGOUT CONFIRMATION
    // ============================================

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> viewModel.logout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ============================================
    // OBSERVE EVENTS
    // ============================================

    private void observes() {
        viewModel.events.observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;

            if (event instanceof SettingsFragmentEvents.NavigateToAgentTypeActivity) {
                Intent intent = new Intent(getActivity(), AgentTypeActivity.class);
                startActivity(intent);
                viewModel.events.setValue(null);

            } else if (event instanceof SettingsFragmentEvents.NavigateToSelectVoiceActivity) {
                Intent intent = new Intent(getActivity(), SelectVoiceActivity.class);
                startActivity(intent);
                viewModel.events.setValue(null);

            } else if (event instanceof SettingsFragmentEvents.NavigateToSubscriptionActivity) {
                Intent intent = new Intent(getActivity(), SubscriptionActivity.class);
                startActivity(intent);
                viewModel.events.setValue(null);

            } else if (event instanceof SettingsFragmentEvents.Logout) {
                handleLogout();
                viewModel.events.setValue(null);
            }
        });
    }

    // ============================================
    // HANDLE LOGOUT
    // ============================================

    private void handleLogout() {
        Toast.makeText(getContext(), "Logged out successfully", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(getActivity(), AuthActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh balance when returning to settings
        loadUserProfile();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}