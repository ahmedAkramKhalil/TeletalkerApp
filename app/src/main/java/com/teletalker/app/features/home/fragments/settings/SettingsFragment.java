// ============================================
// SettingsFragment.java - Using Your Existing Design
// ============================================

package com.teletalker.app.features.home.fragments.settings;

import static android.app.Activity.RESULT_OK;

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
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.functions.FirebaseFunctions;
import com.teletalker.app.R;
import com.teletalker.app.billing.BillingManager;
import com.teletalker.app.databinding.FragmentSettingsBinding;
import com.teletalker.app.features.agent_type.AgentTypeActivity;
import com.teletalker.app.features.booking.BookingSetupActivity;
import com.teletalker.app.features.authentication.presentation.AuthActivity;
import com.teletalker.app.features.home.ThemeManager;
import com.teletalker.app.features.select_voice.presentation.SelectVoiceActivity;
import com.teletalker.app.network.ElevenLabsApiClient;
import com.teletalker.app.network.FirebaseFunctionsManager;
import com.teletalker.app.network.UserBalance;
import com.teletalker.app.utils.PreferencesManager;
import com.teletalker.app.utils.SubscriptionManager;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class SettingsFragment extends Fragment implements com.teletalker.app.utils.SubscriptionManager.SubscriptionListener {

    private FragmentSettingsBinding binding;
    private SettingsViewModel viewModel;
    private FirebaseFunctionsManager functionsManager;
    private FirebaseAuth auth;
    private static final int REQUEST_SUBSCRIPTION = 1001;
    private com.teletalker.app.utils.SubscriptionManager subscriptionManager;
    private ElevenLabsApiClient elevenLabsApiClient;

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
        BillingManager billing = BillingManager.getInstance(requireContext());

        observes();
        initListeners();
        setAutoAnswerSwitch();
        setupApiKeyCard();

        // Load user profile
        loadUserProfile();

        billing.setBalanceUpdateListener(new BillingManager.BalanceUpdateListener() {
            @Override
            public void onBalanceUpdated(double remainingMinutes) {
                updateBalanceUI(remainingMinutes);
            }

            @Override
            public void onBalanceError(String error) {
                Log.e("TAG", "Balance error: " + error);
            }
        });

        subscriptionManager = SubscriptionManager.getInstance(requireContext());
        subscriptionManager.addListener(this);

        // Show cached immediately
        updateBalanceUI(billing.getCachedRemainingMinutes());

        // Sync if stale
        if (billing.isCacheStale()) {
            billing.syncBalance();
        }


    }

    @Override
    public void onBalanceChanged(double newBalance) {
        if (isAdded() && binding != null) {
            requireActivity().runOnUiThread(() -> updateBalanceUI(newBalance));
        }
    }

    @Override
    public void onPlanChanged(String newPlan) {
        // Theme change handled by ThemeManager, activity will recreate
    }

    private void updateBalanceUI(double minutes) {
        if (binding == null || !isAdded()) {
            Log.w("TAG", "Fragment not ready, skipping UI update");
            return;
        }

        try {
            binding.balanceTv.setText(String.format("%.1f minutes", minutes));

            if (minutes < 5) {
                binding.balanceTv.setTextColor(getResources().getColor(R.color.error));
            } else if (minutes < 30) {
                binding.balanceTv.setTextColor(getResources().getColor(R.color.avatar_orange));
            } else {
                binding.balanceTv.setTextColor(getResources().getColor(R.color.colorPrimary));
            }
        } catch (Exception e) {
            Log.e("TAG", "Error updating UI: " + e.getMessage());
        }
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
//        functionsManager.getBalance(new FirebaseFunctionsManager.OnBalanceCallback() {
//            @Override
//            public void onSuccess(UserBalance balance) {
//                updateBalanceDisplay(balance);
//            }
//
//            @Override
//            public void onError(String error) {
//                Log.e("SettingsFragment", "Error loading balance: " + error);
//            }
//        });
    }

    private void openInvoicesActivity() {
        Intent intent = new Intent(getActivity(), InvoicesActivity.class);
        startActivity(intent);
    }

    // ============================================
    // UPDATE BALANCE DISPLAY
    // ============================================

    private void updateBalanceDisplay(UserBalance balance) {
        // ADD THIS NULL CHECK
        if (binding == null || !isAdded()) {
            Log.w("TAG", "Fragment not ready, skipping UI update");
            return;
        }

        // Now safe to update UI
        try {
            double total = balance.getFreeMinutesBalance() + balance.getPaidMinutesBalance();

            binding.nameTv.setText(String.format("%.1f minutes remaining", total));

            // Color coding
            if (total < 5) {
                binding.nameTv.setTextColor(getResources().getColor(R.color.error));
            } else if (total < 30) {
                binding.nameTv.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            } else {
                binding.nameTv.setTextColor(getResources().getColor(R.color.colorPrimary));
            }
        } catch (Exception e) {
            Log.e("TAG", "Error updating balance display: " + e.getMessage());
        }
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

    // ============================================
    // ELEVENLABS API KEY CARD
    // ============================================

    private void setupApiKeyCard() {
        elevenLabsApiClient = new ElevenLabsApiClient();

        // Show masked existing key if present
        String existingKey = PreferencesManager.getInstance(requireContext()).getApiKey();
        if (existingKey != null && existingKey.length() > 4) {
            String masked = "••••••••" + existingKey.substring(existingKey.length() - 4);
            binding.apiKeyInput.setHint(masked);
            binding.apiKeyStatus.setText(R.string.elevenlabs_api_key_valid);
            binding.apiKeyStatus.setTextColor(getResources().getColor(R.color.colorPrimary));
            binding.apiKeyStatus.setVisibility(View.VISIBLE);
        }

        binding.apiKeySaveBtn.setOnClickListener(v -> {
            String key = binding.apiKeyInput.getText().toString().trim();
            if (key.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter an API key", Toast.LENGTH_SHORT).show();
                return;
            }

            // Show validating state
            binding.apiKeySaveBtn.setEnabled(false);
            binding.apiKeyStatus.setText(R.string.elevenlabs_api_key_validating);
            binding.apiKeyStatus.setTextColor(getResources().getColor(R.color.gunmetalTextGray));
            binding.apiKeyStatus.setVisibility(View.VISIBLE);

            elevenLabsApiClient.validateApiKey(key, (valid, message) -> {
                if (!isAdded() || binding == null) return;

                binding.apiKeySaveBtn.setEnabled(true);

                if (valid) {
                    // Save the key
                    PreferencesManager.getInstance(requireContext()).saveApiKey(key);

                    binding.apiKeyStatus.setText(R.string.elevenlabs_api_key_valid);
                    binding.apiKeyStatus.setTextColor(getResources().getColor(R.color.colorPrimary));

                    // Clear input and show masked hint
                    binding.apiKeyInput.setText("");
                    String masked = "••••••••" + key.substring(key.length() - 4);
                    binding.apiKeyInput.setHint(masked);

                    Toast.makeText(requireContext(), "API key saved", Toast.LENGTH_SHORT).show();

                    // Sync to Firestore (best-effort)
                    try {
                        FirebaseFunctionsManager.getInstance()
                                .syncElevenLabsApiKey(key, new FirebaseFunctionsManager.OnSyncApiKeyCallback() {
                                    @Override public void onSuccess() {
                                        Log.d("SettingsFragment", "API key synced to cloud");
                                    }
                                    @Override public void onError(String error) {
                                        Log.w("SettingsFragment", "Failed to sync API key: " + error);
                                    }
                                });
                    } catch (Exception e) {
                        Log.w("SettingsFragment", "Failed to sync API key to cloud: " + e.getMessage());
                    }
                } else {
                    binding.apiKeyStatus.setText(R.string.elevenlabs_api_key_invalid);
                    binding.apiKeyStatus.setTextColor(getResources().getColor(R.color.error));
                }
            });
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

        binding.subscriptionCardView.setOnClickListener(v -> openSubscriptionPlan());

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

        binding.complianceCardView.setOnClickListener(v -> showComplianceDialog());

        binding.backgroundCardView.setOnClickListener(v -> showBackgroundDialog());

        binding.dialerCardView.setOnClickListener(v -> showDialerRoleDialog());

        // Booking page + inbound AI (merchant booking feature).
        boolean isLite = com.teletalker.app.utils.SubscriptionManager.getInstance(requireContext()).isLite();
        binding.bookingPageBadge.setVisibility(isLite ? View.VISIBLE : View.GONE);
        binding.bookingPageCard.setOnClickListener(v ->
                startActivity(new android.content.Intent(requireContext(),
                        com.teletalker.app.features.booking.BookingSetupActivity.class)));
        binding.inboundAiCard.setOnClickListener(v ->
                startActivity(new android.content.Intent(requireContext(),
                        com.teletalker.app.features.booking.InboundAIActivity.class)));
    }

    // ============================================
    // DEFAULT PHONE APP (ROLE_DIALER)
    // ============================================

    private void showDialerRoleDialog() {
        boolean isDefault = com.teletalker.app.utils.DialerRoleHelper.isDefaultDialer(requireContext());
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialer_dialog_title)
                .setMessage(isDefault
                        ? "TeleTalker is already the default phone app."
                        : getString(R.string.dialer_dialog_message))
                .setPositiveButton(isDefault ? "OK" : "Set default", (d, w) -> {
                    if (!isDefault) {
                        // Pass the Fragment so the result routes to onActivityResult below.
                        com.teletalker.app.utils.DialerRoleHelper.requestDefaultDialer(SettingsFragment.this);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ============================================
    // BACKGROUND / OEM AUTOSTART & BATTERY
    // ============================================

    private void showBackgroundDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.background_dialog_title)
                .setMessage(R.string.background_dialog_message)
                .setPositiveButton("Battery setting", (d, w) ->
                        com.teletalker.app.utils.OemLifecycleHelper
                                .requestIgnoreBatteryOptimizations(requireContext()))
                .setNeutralButton("Autostart", (d, w) ->
                        com.teletalker.app.utils.OemLifecycleHelper
                                .openAutoStartSettings(requireContext()))
                .setNegativeButton("Close", null)
                .show();
    }

    // ============================================
    // COMPLIANCE / AI DISCLOSURE ONE-PAGER
    // ============================================

    private void showComplianceDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheet = getLayoutInflater().inflate(R.layout.dialog_compliance, null);
        dialog.setContentView(sheet);
        View closeBtn = sheet.findViewById(R.id.complianceCloseBtn);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> dialog.dismiss());
        }
        dialog.show();
    }

    private void openSubscriptionPlan() {
        Intent intent = new Intent(getActivity(), SubscriptionPlanActivity.class);
        startActivityForResult(intent, REQUEST_SUBSCRIPTION);
    }


    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SUBSCRIPTION && resultCode == RESULT_OK) {
            // Subscription changed - update theme
            syncThemeWithBackend();
//            updateAppTheme();
        }

        if (requestCode == com.teletalker.app.utils.DialerRoleHelper.REQ_DIALER_ROLE) {
            boolean nowDefault = com.teletalker.app.utils.DialerRoleHelper.isDefaultDialer(requireContext());
            Toast.makeText(requireContext(),
                    nowDefault ? "TeleTalker is now the default phone app"
                               : "Default phone app was not changed",
                    Toast.LENGTH_SHORT).show();
        }
    }


    private void syncThemeWithBackend() {
        FirebaseFunctions.getInstance()
                .getHttpsCallable("getSubscriptionStatus")
                .call()
                .addOnSuccessListener(result -> {

                    Map<String, Object> data = (Map<String, Object>) result.getData();
                    String plan = (String) data.get("plan"); // "lite" or "standard"

                    ThemeManager themeManager = ThemeManager.getInstance(getActivity());
                    String currentTheme = themeManager.getAppVersion();

                    Log.d("Error", "getSubscriptionStatus " + currentTheme);

                    // Update theme if it doesn't match backend
//                    if (!plan.equals(currentTheme)) {
                    themeManager.setAppVersion(plan);
                    // Activity recreates with correct theme
//                    }
                })
                .addOnFailureListener(e -> {
                    // Default to lite on error
                    Log.d("Error", "getSubscriptionStatus " + e.getMessage());
                    ThemeManager.getInstance(getActivity()).setAppVersion("lite");
                });
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
        PreferencesManager.getInstance(getContext()).clearLoginData();

        // Sign out from Firebase
        FirebaseAuth.getInstance().signOut();
        // Redirect to auth screen
        Intent intent = new Intent(requireActivity(), com.teletalker.app.features.authentication.presentation.AuthActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();

        Toast.makeText(getContext(), "Logged out successfully", Toast.LENGTH_SHORT).show();
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
        if (subscriptionManager != null) {
            subscriptionManager.removeListener(this);
        }

        binding = null;
    }
}