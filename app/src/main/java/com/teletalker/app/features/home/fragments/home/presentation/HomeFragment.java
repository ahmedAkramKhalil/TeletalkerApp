package com.teletalker.app.features.home.fragments.home.presentation;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;

import static com.teletalker.app.billing.BillingManager.KEY_REMAINING_MINUTES;
import static com.teletalker.app.billing.BillingManager.PREFS_NAME;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.functions.FirebaseFunctions;
import com.teletalker.app.R;
import com.teletalker.app.billing.BillingManager;
import com.teletalker.app.databinding.FragmentHomeBinding;
import com.teletalker.app.databinding.FragmentSettingsBinding;
import com.teletalker.app.features.agent_type.AgentTypeActivity;
import com.teletalker.app.features.home.HomeActivity;
import com.teletalker.app.features.home.StatusModel;
import com.teletalker.app.features.home.TeleTalkerStatusWidget;
import com.teletalker.app.features.home.ThemeManager;
import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;
import com.teletalker.app.features.home.fragments.settings.SettingsViewModel;
import com.teletalker.app.features.home.fragments.settings.VersionSwitcherActivity;
import com.teletalker.app.features.subscription.presentation.SubscriptionActivity;
import com.teletalker.app.network.FirebaseFunctionsManager;
import com.teletalker.app.network.UserBalance;
import com.teletalker.app.services.StatusBroadcastManager;
import com.teletalker.app.subscription.CheckoutActivity;
import com.teletalker.app.subscription.PricingFragment;
import com.teletalker.app.utils.AuthStateManager;
import com.teletalker.app.utils.PreferencesManager;
import com.teletalker.app.utils.SubscriptionCacheManager;
import com.teletalker.app.utils.SubscriptionManager;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class HomeFragment extends Fragment implements CallHistoryAdapter.OnCallPlaybackListener , SubscriptionManager.SubscriptionListener {

    private FragmentHomeBinding binding;
    private CallHistoryAdapter adapter;
    private NavController navController;
    private HomeViewModel homeViewModel;
    private PreferencesManager prefsManager;

    // Media player for audio playback
    private MediaPlayer mediaPlayer;
    private int currentlyPlayingCallId = -1;
    private boolean isSoundEnabled = true;
    boolean isAgentChanged = false;
    private BroadcastReceiver statusReceiver;


    private FirebaseFunctionsManager functionsManager;
    private FirebaseAuth auth;

    private FirebaseFunctions functions;


    private TeleTalkerStatusWidget statusWidget;

    private SubscriptionCacheManager subscriptionCache;
    SubscriptionManager subscriptionManager;
    private AuthStateManager authStateManager;
    private BillingManager billing;
    private Handler balanceRefreshHandler = new Handler(Looper.getMainLooper());
    private static final long BALANCE_REFRESH_INTERVAL = 30000; // 30 seconds


    private BroadcastReceiver minutesReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            double remaining = intent.getDoubleExtra("remaining", 0);
            updateMinutesProgress(remaining, prefsManager.getString("appVersion", "lite"));
        }
    };


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        billing = BillingManager.getInstance(requireContext());
        subscriptionManager = SubscriptionManager.getInstance(getActivity());
        subscriptionManager.addListener(this);
        subscriptionManager.sync();


    }

    @Override
    public void onBalanceChanged(double newBalance) {
        if (isAdded() && binding != null) {
            Log.d("TAG", "checkAndAutoDisableAI03993939: " + newBalance);

            requireActivity().runOnUiThread(() -> updateBalanceUI(newBalance));
        }
        SharedPreferences   prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putFloat(KEY_REMAINING_MINUTES, (float) newBalance)
                .apply();

    }

    @Override
    public void onPlanChanged(String newPlan) {
        Log.d("TAG", "newPlanw3493403940: " + newPlan);

        // Theme change handled by ThemeManager, activity will recreate
    }


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        functionsManager = FirebaseFunctionsManager.getInstance();
        functions = FirebaseFunctions.getInstance();

        auth = FirebaseAuth.getInstance();


        statusWidget = binding.statusIndicator;
        setupStatusListener();
        registerStatusReceiver();
        binding.subscribeButton.setOnClickListener(v -> navigateToPricing(this.getView()));
        return binding.getRoot();
    }


    private void registerStatusReceiver() {
        statusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (StatusBroadcastManager.ACTION_CALL_STATUS_CHANGED.equals(action)) {
                    String callStatus = intent.getStringExtra(StatusBroadcastManager.EXTRA_CALL_STATUS);
                    String phoneNumber = intent.getStringExtra(StatusBroadcastManager.EXTRA_PHONE_NUMBER);
                    String contactName = intent.getStringExtra(StatusBroadcastManager.EXTRA_CONTACT_NAME);

                    onCallStatusUpdate(callStatus, phoneNumber, contactName);

                } else if (StatusBroadcastManager.ACTION_AI_STATUS_CHANGED.equals(action)) {
                    String aiStatus = intent.getStringExtra(StatusBroadcastManager.EXTRA_AI_STATUS);
                    boolean isRecording = intent.getBooleanExtra(StatusBroadcastManager.EXTRA_IS_RECORDING, false);
                    boolean isInjecting = intent.getBooleanExtra(StatusBroadcastManager.EXTRA_IS_INJECTING, false);
                    String response = intent.getStringExtra(StatusBroadcastManager.EXTRA_AI_RESPONSE);

                    onAIStatusUpdate(aiStatus, isRecording, isInjecting, response);
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(StatusBroadcastManager.ACTION_CALL_STATUS_CHANGED);
        filter.addAction(StatusBroadcastManager.ACTION_AI_STATUS_CHANGED);

        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(statusReceiver, filter);
    }


    private void setupStatusListener() {
        statusWidget.setStatusChangeListener(new TeleTalkerStatusWidget.StatusChangeListener() {
            @Override
            public void onInternetStatusChanged(StatusModel.InternetStatus status) {
                Log.d("HomeFragment", "Internet: " + status);
                // Handle internet status changes
                updateOtherUIElements(status);
            }

            @Override
            public void onCallStatusChanged(StatusModel.CallState callState) {
                Log.d("HomeFragment", "Call: " + callState.status + " - " + callState.getDisplayName());
                // Handle call status changes
                if (callState.status == StatusModel.CallStatus.ACTIVE) {
                    // Call started - maybe dim other UI elements
                } else if (callState.status == StatusModel.CallStatus.IDLE) {
                    // Call ended - restore normal UI
                }
            }

            @Override
            public void onAIStatusChanged(StatusModel.AIState aiState) {
                Log.d("HomeFragment", "AI: " + aiState.status + " - Recording: " + aiState.isRecording);
                // Handle AI status changes
                if (aiState.status == StatusModel.AIStatus.ACTIVE) {
                    // AI is speaking - maybe show visual feedback
                }
            }
        });
    }

    private void updateOtherUIElements(StatusModel.InternetStatus status) {
        // Update other parts of your home screen based on internet status
        // For example, enable/disable certain features when offline
    }

    // Methods to call from your CallDetector service
    public void onCallStatusUpdate(String status, String phoneNumber, String contactName) {
        StatusModel.CallStatus callStatus;
        switch (status) {
            case "RINGING":
                callStatus = StatusModel.CallStatus.RINGING;
                break;
            case "ACTIVE":
                callStatus = StatusModel.CallStatus.ACTIVE;
                break;
            case "HOLDING":
                callStatus = StatusModel.CallStatus.HOLDING;
                break;
            case "ENDING":
                callStatus = StatusModel.CallStatus.ENDING;
                break;
            default:
                callStatus = StatusModel.CallStatus.IDLE;
                break;
        }

        statusWidget.updateCallStatus(callStatus, phoneNumber, contactName);
    }

    public void onAIStatusUpdate(String status, boolean isRecording, boolean isInjecting, String lastResponse) {
        StatusModel.AIStatus aiStatus;
        switch (status) {
            case "CONNECTING":
                aiStatus = StatusModel.AIStatus.CONNECTING;
                break;
            case "CONNECTED":
                aiStatus = StatusModel.AIStatus.CONNECTED;
                break;
            case "ACTIVE":
                aiStatus = StatusModel.AIStatus.ACTIVE;
                break;
            case "ERROR":
                aiStatus = StatusModel.AIStatus.ERROR;
                break;
            default:
                aiStatus = StatusModel.AIStatus.HIDDEN;
                break;
        }

        statusWidget.updateAIStatus(aiStatus, isRecording, isInjecting, lastResponse);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefsManager = PreferencesManager.getInstance(getContext());

        subscriptionCache = new SubscriptionCacheManager(getContext());
        authStateManager = new AuthStateManager(getContext());


        initializeVariables(view);
        initListeners();
        observes();

        setupBillingListener();
        syncBalanceAndUpdateUI();

        if (prefsManager.getSelectedAgentId() == null) {
            binding.AgentButtonText.setText(R.string.select_agent);
            prefsManager.setIsBotActive(false);
        } else {
            binding.AgentButtonText.setText(R.string.change_default_agent);
            binding.agentName.setText(prefsManager.getSelectedAgentName());
            binding.agentLanguage.setText(prefsManager.getSelectedAgentLanguage());
        }
        setupAppBarFromCache();

    }

    private void setupBillingListener() {
        billing.setBalanceUpdateListener(new BillingManager.BalanceUpdateListener() {
            @Override
            public void onBalanceUpdated(double remainingMinutes) {
                Log.d("TAG", "onBalanceUpdated: " + remainingMinutes);
                updateBalanceUI(remainingMinutes);
                checkAndAutoDisableAI(remainingMinutes);
            }

            @Override
            public void onBalanceError(String error) {
                Log.e("HomeFragment", "Billing error: " + error);
            }
        });
    }


    private boolean isFirstLaunch() {
        boolean isFirst = !prefsManager.getBoolean("home_fragment_loaded", false);
        if (isFirst) {
            prefsManager.putBoolean("home_fragment_loaded", true);
        }
        return isFirst;
    }

    private void setupAppBarFromCache() {
        // Get cached user info
        AuthStateManager.CachedUserInfo userInfo = authStateManager.getCachedUserInfo();

        if (userInfo.isValid()) {
            // Setup profile avatar
            String name = userInfo.name != null ? userInfo.name :
                    (userInfo.email != null ? userInfo.email : "User");
            ProfileAvatarGenerator.setupProfileAvatar(binding.profileInitials, name);

            // Set welcome message
            String firstName = getFirstName(name);
            binding.nameTv.setText("Welcome back, " + firstName + "!");

            // Load cached subscription data
            SubscriptionCacheManager.CachedSubscription cached = subscriptionCache.getCachedSubscription();
            updateVersionDisplay(cached.appVersion);
            updateMinutesProgress(cached.getTotalMinutes(), cached.appVersion);

        } else {
            // No cached data - use defaults
            binding.profileInitials.setText("??");
            binding.nameTv.setText("Welcome!");
            binding.versionTv.setText("Lite Plan");
            binding.minutesProgressCard.setVisibility(android.view.View.GONE);
        }
    }

    // ADD THIS METHOD - Background sync
    private void syncSubscriptionData() {
        subscriptionCache.syncIfNeeded(new SubscriptionCacheManager.SubscriptionCallback() {
            @Override
            public void onSuccess(SubscriptionCacheManager.CachedSubscription subscription) {
                if (getActivity() == null || isDetached()) return;

                // Update UI with fresh data
                getActivity().runOnUiThread(() -> {
                    updateVersionDisplay(subscription.appVersion);
                    updateMinutesProgress(subscription.getTotalMinutes(), subscription.appVersion);

                    // Update theme if needed
                    String currentTheme = ThemeManager.getInstance(getActivity()).getAppVersion();
                    if (!subscription.appVersion.equals(currentTheme)) {
                        ThemeManager.getInstance(getActivity()).setAppVersion(subscription.appVersion);
                        // Activity will recreate with new theme
                    }
                });
            }

            @Override
            public void onError(String error, SubscriptionCacheManager.CachedSubscription cachedData) {
                Log.e("HomeFragment", "Sync error: " + error + ", using cached data");

                // Still update UI with cached data
                if (getActivity() != null && !isDetached()) {
                    getActivity().runOnUiThread(() -> {
                        updateVersionDisplay(cachedData.appVersion);
                        updateMinutesProgress(cachedData.getTotalMinutes(), cachedData.appVersion);
                    });
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (statusReceiver != null) {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(statusReceiver);
        }
        stopBalanceAutoRefresh();
        billing.removeBalanceUpdateListener();

        releaseMediaPlayer();
        binding = null;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            pauseCurrentCall();
        }
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(minutesReceiver);

        stopBalanceAutoRefresh();


    }
//
//    @Override
//    public void onResume() {
//        super.onResume();
//        LocalBroadcastManager.getInstance(requireContext())
//                .registerReceiver(minutesReceiver, new IntentFilter("com.teletalker.MINUTES_UPDATE"));
//        try {
//            setupAppBar();
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        if (isAgentChanged) {
//            isAgentChanged = !isAgentChanged;
//            binding.agentName.setText(prefsManager.getSelectedAgentName());
//            binding.agentLanguage.setText(prefsManager.getSelectedAgentLanguage());
//        }
//
//    }

    @Override
    public void onResume() {
        super.onResume();

        LocalBroadcastManager.getInstance(requireContext())
                .registerReceiver(minutesReceiver, new IntentFilter("com.teletalker.MINUTES_UPDATE"));

        // ONLY sync on first launch or if cache expired
        if (isFirstLaunch() || !subscriptionCache.isCacheValid()) {
            Log.d("HomeFragment", "Syncing subscription data...");
            syncSubscriptionData();
        } else {
            Log.d("HomeFragment", "Using cached subscription data");
        }

        // Update agent if changed
        if (isAgentChanged) {
            isAgentChanged = false;
            binding.agentName.setText(prefsManager.getSelectedAgentName());
            binding.agentLanguage.setText(prefsManager.getSelectedAgentLanguage());
        }
        startBalanceAutoRefresh();
        if (billing.isCacheStale()) {
            billing.syncBalance();
        }


    }


    private void initListeners() {
//        binding.subscribeButton.setOnClickListener(v -> homeViewModel.navigateToSubscriptionScreen());
        binding.seeAllHistoryTv.setOnClickListener(v -> homeViewModel.navigateToCallHistoryScreen());
        binding.changeAgentButton.setOnClickListener(v -> {
            homeViewModel.navigateToChangeAgentScreen();
            isAgentChanged = true;
        });
        binding.aiActivationButton.setImageResource(prefsManager.isBotActive() ? R.drawable.active_button : R.drawable.inactive_button);


        binding.aiActivationButton.setOnClickListener(v -> {
            if (prefsManager.getSelectedAgentId() == null) {
                Toast.makeText(getContext(), R.string.please_select_agent, LENGTH_LONG).show();
                return;
            }

//            boolean status = !prefsManager.isBotActive();
//            binding.aiActivationButton.setImageResource(status ? R.drawable.active_button : R.drawable.inactive_button);
//            prefsManager.setIsBotActive(status);


            boolean currentState = prefsManager.isBotActive();
            if (!currentState) {
                enableAIWithBalanceCheck();
            } else {
                disableAI();
            }


            //            TODO: remove this

//            prefsManager.setIsBotActive(true);
//            binding.aiActivationButton.setImageResource(R.drawable.active_button);


//            TODO:
            // Check minutes before enabling
//            if (!prefsManager.isBotActive()) {
//                FirebaseFunctionsManager.getInstance().getBalance(new FirebaseFunctionsManager.OnBalanceCallback() {
//                    @Override
//                    public void onSuccess(UserBalance balance) {
//                        double total = balance.getFreeMinutesBalance() + balance.getPaidMinutesBalance();
//                        if (total <= 0) {
//                            // Navigate to top-up
//                            startActivity(new Intent(getActivity(), VersionSwitcherActivity.class));
//                            Toast.makeText(getContext(), "No minutes remaining. Please top up.", LENGTH_LONG).show();
//                        } else {
//                            prefsManager.setIsBotActive(true);
//                            binding.aiActivationButton.setImageResource(R.drawable.active_button);
//                        }
//                    }
//
//                    @Override
//                    public void onError(String error) {
//                        Toast.makeText(getContext(), "Failed to check balance", LENGTH_SHORT).show();
//                    }
//                });
//            } else {
//                prefsManager.setIsBotActive(false);
//                binding.aiActivationButton.setImageResource(R.drawable.inactive_button);
//            }
        });


    }

    private void enableAIWithBalanceCheck() {
        binding.aiActivationButton.setEnabled(false);

        if (billing.hasSufficientBalance()) {
            enableAI();
        } else {
            billing.syncBalance(new BillingManager.SyncCallback() {
                @Override
                public void onSyncSuccess(double balance) {
                    binding.aiActivationButton.setEnabled(true);
                    if (balance >= 0.5) {
                        enableAI();
                    } else {
                        showBalanceDepletedDialog(balance);
                    }
                }

                @Override
                public void onSyncError(String error) {
                    binding.aiActivationButton.setEnabled(true);
                    showBalanceCheckFailedDialog();
                }
            });
        }
    }

    private void enableAI() {
        prefsManager.setIsBotActive(true);
        binding.aiActivationButton.setImageResource(R.drawable.active_button);
        binding.aiActivationButton.setEnabled(true);
        Toast.makeText(getContext(), "AI Assistant Enabled ✓", LENGTH_SHORT).show();
    }

    private void disableAI() {
        prefsManager.setIsBotActive(false);
        binding.aiActivationButton.setImageResource(R.drawable.inactive_button);
        Toast.makeText(getContext(), "AI Assistant Disabled", LENGTH_SHORT).show();
    }

    private void checkAndAutoDisableAI(double balance) {
        if (balance < 0.1 && prefsManager.isBotActive()) {
            Log.d("TAG", "checkAndAutoDisableAI: " + balance);
            disableAI();
            showAutoDisabledDialog();
        }
    }

    private void syncBalanceAndUpdateUI() {
        billing.syncBalance();
    }


    private void startBalanceAutoRefresh() {
        balanceRefreshHandler.post(new Runnable() {
            @Override
            public void run() {
                if (billing.isCacheStale()) {
                    billing.syncBalance();
                }
                balanceRefreshHandler.postDelayed(this, BALANCE_REFRESH_INTERVAL);
            }
        });
    }

    private void stopBalanceAutoRefresh() {
        balanceRefreshHandler.removeCallbacksAndMessages(null);
    }

//    private void updateBalanceUI(double minutes) {
//        if (binding == null || !isAdded()) {
//            return;
//        }
//
//        String appVersion = subscriptionManager.getCurrentPlan();
//        updateMinutesProgress(minutes, appVersion);
//        updateVersionDisplay(appVersion);
//    }

    private void updateBalanceUI(double minutes) {
        try {
//            int minutes = (int)ammount ;
            Log.d("updateBalanceUI" , "updateBalanceUI:::: "+ minutes);
            binding.minutesProgressCard.setVisibility(android.view.View.VISIBLE);

            int maxMinutes = 100;
            int percentage = (int) Math.min((minutes / maxMinutes) * 100, 100);

            binding.minutesProgress.setMax(100);
            binding.minutesProgress.setProgress(percentage);

            int minutesInt = (int) minutes;
            binding.minutesLeftText.setText(String.valueOf(minutesInt));

            if (minutes < 5) {
                binding.minutesProgress.setIndicatorColor(getResources().getColor(R.color.error));
                binding.minutesLeftText.setTextColor(getResources().getColor(R.color.error));
            } else if (minutes < 30) {
                binding.minutesProgress.setIndicatorColor(getResources().getColor(android.R.color.holo_orange_dark));
                binding.minutesLeftText.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            } else {
                binding.minutesProgress.setIndicatorColor(getResources().getColor(R.color.colorPrimary));
                binding.minutesLeftText.setTextColor(getResources().getColor(R.color.text_primary));
            }
        } catch (Exception e) {
            Log.e("HomeFragment", "Error updating balance UI: " + e.getMessage());
        }
    }

    private void showBalanceDepletedDialog(double balance) {
        if (getContext() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("⚠️ Insufficient Balance")
                .setMessage(String.format("You have %.1f minutes remaining.\n\nMinimum 0.5 minutes required.\n\nTop up now?", balance))
                .setPositiveButton("Top Up", (d, w) ->
                        startActivity(new Intent(getActivity(), SubscriptionActivity.class)))
                .setNegativeButton("Later", null)
                .show();
    }

    private void showBalanceCheckFailedDialog() {
        if (getContext() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Balance Check Failed")
                .setMessage("Unable to verify balance.\n\nEnable AI anyway?")
                .setPositiveButton("Enable Anyway", (d, w) -> enableAI())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showNoInternetDialog() {
        if (getContext() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("📡 No Internet")
                .setMessage("Internet required for AI features.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showAutoDisabledDialog() {
        if (getContext() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("AI Disabled - No Minutes")
                .setMessage("Balance depleted.\n\nAI Assistant auto-disabled.\n\nTop up to continue?")
                .setPositiveButton("Top Up", (d, w) ->
                        startActivity(new Intent(getActivity(), SubscriptionActivity.class)))
                .setNegativeButton("OK", null)
                .show();
    }



    private void initializeVariables(@NonNull View view) {
        adapter = new CallHistoryAdapter(List.of(), this);
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        binding.recyclerView.setAdapter(adapter);
        navController = Navigation.findNavController(view);

        // Initialize MediaPlayer
        mediaPlayer = new MediaPlayer();
        setupMediaPlayerListeners();
    }

    private void setupMediaPlayerListeners() {
        mediaPlayer.setOnCompletionListener(mp -> {
            // When audio completes, reset the play state
            currentlyPlayingCallId = -1;
            adapter.setCurrentlyPlaying(-1);
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(getContext(), "Error playing audio", LENGTH_SHORT).show();
            currentlyPlayingCallId = -1;
            adapter.setCurrentlyPlaying(-1);
            return true;
        });

        mediaPlayer.setOnPreparedListener(mp -> {
            // Start playing when prepared
            mp.start();
        });
    }

    private void loadCallHistory() {
        // This would typically come from your ViewModel or Repository
        // For now, using placeholder data
        // homeViewModel.loadCallHistory();

        // You would observe the call history data from ViewModel
        // homeViewModel.getCallHistory().observe(getViewLifecycleOwner(), callHistory -> {
        //     adapter.updateCallHistory(callHistory);
        // });
    }

    private void observes() {

        homeViewModel.getCallHistoryLiveData().observe(getViewLifecycleOwner(), callHistoryItems -> {


            if (callHistoryItems == null || callHistoryItems.isEmpty()) {

                binding.nodataIcon.setVisibility(VISIBLE);
            } else {
                binding.nodataIcon.setVisibility(GONE);
                adapter.updateCallHistory(callHistoryItems);

            }
        });


        homeViewModel.events.observe(getViewLifecycleOwner(), event -> {
            if (event instanceof HomeFragmentEvents.NavigateToSubscriptionScreen) {
//                    Intent intent = new Intent(getActivity(), SubscriptionActivity.class);
//                    startActivity(intent);
//                    homeViewModel.clearNavigationState();
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

    // CallHistoryAdapter.OnCallPlaybackListener implementations
    @Override
    public void onPlayCall(CallEntity callEntity) {
        try {
            // If another call is playing, stop it first
            if (currentlyPlayingCallId > -1 && !(currentlyPlayingCallId == (callEntity.id))) {
                pauseCurrentCall();
            }

            // Check if we're resuming the same call
            if (currentlyPlayingCallId > -1 && currentlyPlayingCallId == (callEntity.id)) {
                // Resume playback
                if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                    mediaPlayer.start();
                }
            } else {
                // Start new playback
                prepareAndPlayAudio(callEntity);
            }

            currentlyPlayingCallId = callEntity.id;
            adapter.setCurrentlyPlaying(callEntity.id);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Failed to play audio: " + e.getMessage(), LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPauseCall(CallEntity callEntity) {
        pauseCurrentCall();
    }

    @Override
    public void onToggleSound(CallEntity callEntity) {
        isSoundEnabled = !isSoundEnabled;

        if (mediaPlayer != null) {
            if (isSoundEnabled) {
                mediaPlayer.setVolume(1.0f, 1.0f);
                Toast.makeText(getContext(), "Sound enabled", LENGTH_SHORT).show();
            } else {
                mediaPlayer.setVolume(0.0f, 0.0f);
                Toast.makeText(getContext(), "Sound muted", LENGTH_SHORT).show();
            }
        }
    }

    private void prepareAndPlayAudio(CallEntity callEntity) throws IOException {
        if (mediaPlayer != null) {
            mediaPlayer.reset();

            // Set the audio file path
            // Adjust this based on how you store/access your call recording files
            String audioFilePath = callEntity.getRecordingFilePath();

            if (audioFilePath != null && !audioFilePath.isEmpty()) {
                mediaPlayer.setDataSource(audioFilePath);
                mediaPlayer.setVolume(isSoundEnabled ? 1.0f : 0.0f, isSoundEnabled ? 1.0f : 0.0f);
                mediaPlayer.prepareAsync(); // Use async preparation
            } else {
                Toast.makeText(getContext(), "No recording available for this call", LENGTH_SHORT).show();
            }
        }
    }

    private void pauseCurrentCall() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }

        if (currentlyPlayingCallId > -1) {
            adapter.setCurrentlyPlaying(-1);
            currentlyPlayingCallId = -1;
        }
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }


    private void navigateToPricing(View view) {
        // Get NavController

        startActivity(new Intent(getActivity(), VersionSwitcherActivity.class));
//        NavController navController = Navigation.findNavController(view);
//
//        Bundle args = new Bundle();
//        args.putString("user_id", getUserId());
//        args.putString("source", "home_screen");
//        navController.navigate(R.id.action_home_to_pricing, args);

    }

    private String getUserId() {
        return "user_123"; // Get from your user session
    }


    private void setupAppBar() {
        // Get current user
        FirebaseUser user = auth.getCurrentUser();

        if (user != null) {
            // Setup profile avatar with initials
            String displayName = user.getDisplayName();
            String email = user.getEmail();
            String name = displayName != null ? displayName : (email != null ? email : "User");
            ProfileAvatarGenerator.setupProfileAvatar(binding.profileInitials, name);
            // Set welcome message
            String firstName = getFirstName(name);
            binding.nameTv.setText("Welcome back, " + firstName + "!");
            // Load user balance and version
            loadUserData();
        } else {
            // No user logged in
            binding.profileInitials.setText("??");
            binding.nameTv.setText("Welcome!");
            binding.versionTv.setText("Guest");
            binding.minutesProgressCard.setVisibility(android.view.View.GONE);
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


//    private void loadUserData() {
//        syncThemeWithBackend();
//        // Call Firebase function to get user balance
//        functions.getHttpsCallable("getbalance")
//                .call()
//                .addOnSuccessListener(result -> {
//                    Map<String, Object> data = (Map<String, Object>) result.getData();
//
//                    // Get app version
//                    String appVersion = (String) data.get("appVersion");
//                    updateVersionDisplay(appVersion);
//
//                    // Get minutes balance
//                    Object freeMinutesObj = data.get("freeMinutesBalance");
//                    Object paidMinutesObj = data.get("paidMinutesBalance");
//
//                    double freeMinutes = freeMinutesObj != null ? ((Number) freeMinutesObj).doubleValue() : 0;
//                    double paidMinutes = paidMinutesObj != null ? ((Number) paidMinutesObj).doubleValue() : 0;
//                    double totalMinutes = freeMinutes + paidMinutes;
//
//                    // Update minutes progress
//                    updateMinutesProgress(totalMinutes, appVersion);
//                })
//                .addOnFailureListener(e -> {
//                    Log.e("AppBar", "Error loading user data: " + e.getMessage());
//                    binding.versionTv.setText("Lite Plan");
//                    binding.minutesProgressCard.setVisibility(android.view.View.GONE);
//                });
//    }

    private void loadUserData() {
//        syncThemeWithBackend();

        double cachedBalance = billing.getCachedRemainingMinutes();
//        String appVersion = prefsManager.getString("appVersion", "lite");

//        updateVersionDisplay(appVersion);
        updateBalanceUI(cachedBalance);

//        billing.syncBalance();
        subscriptionManager.sync();

    }



    private void updateVersionDisplay(String appVersion) {
        try {
            if ("standard".equalsIgnoreCase(appVersion)) {
                if (binding != null && binding.versionTv != null)
                    binding.versionTv.setText("Standard Plan ⭐");
                binding.versionTv.setTextColor(getResources().getColor(R.color.colorPrimary));
                // Hide minutes progress for standard users (unlimited)
                binding.minutesProgressCard.setVisibility(android.view.View.GONE);
                binding.notSubscribedLayout.setVisibility(GONE);
            } else {
                binding.versionTv.setText("Lite Plan");
                binding.versionTv.setTextColor(getResources().getColor(R.color.text_secondary));
                binding.minutesProgressCard.setVisibility(android.view.View.VISIBLE);
                binding.notSubscribedLayout.setVisibility(VISIBLE);

            }

        } catch (Exception e) {

        }
    }

    private void updateMinutesProgress(double totalMinutes, String appVersion) {
//        if ("standard".equalsIgnoreCase(appVersion)) {
//            // Standard users have unlimited minutes
//            binding.minutesProgressCard.setVisibility(android.view.View.GONE);
//            return;
//        }

        try {


            // Show progress for lite users
            if (binding != null)
                binding.minutesProgressCard.setVisibility(android.view.View.VISIBLE);

            // Calculate percentage (assuming max 100 minutes for free users)
            int maxMinutes = 100; // Default free minutes
            int percentage = (int) Math.min((totalMinutes / maxMinutes) * 100, 100);

            // Update progress indicator
            binding.minutesProgress.setMax(100);
            binding.minutesProgress.setProgress(percentage);

            // Update minutes text
            int minutesInt = (int) Math.ceil(totalMinutes);
            binding.minutesLeftText.setText(String.valueOf(minutesInt));

            // Change color based on remaining minutes
            if (totalMinutes < 10) {
                // Low minutes - red
                binding.minutesProgress.setIndicatorColor(getResources().getColor(R.color.error));
                binding.minutesLeftText.setTextColor(getResources().getColor(R.color.error));
            } else if (totalMinutes < 30) {
                // Medium minutes - orange
                binding.minutesProgress.setIndicatorColor(getResources().getColor(android.R.color.holo_orange_dark));
                binding.minutesLeftText.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            } else {
                // Good amount - primary color
                binding.minutesProgress.setIndicatorColor(getResources().getColor(R.color.colorPrimary));
                binding.minutesLeftText.setTextColor(getResources().getColor(R.color.text_primary));
            }

            // Add click listener to open top-up screen
            binding.minutesProgressCard.setOnClickListener(v -> {
                // TODO: Navigate to top-up or upgrade screen
                // Intent intent = new Intent(this, TopUpActivity.class);
                // startActivity(intent);
            });

        } catch (Exception e) {
            Log.d("Expetion minutesProgressCard", Objects.requireNonNull(e.getMessage()));
        }
    }

    private String getFirstName(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            return "User";
        }

        // Remove email domain if present
        if (fullName.contains("@")) {
            fullName = fullName.substring(0, fullName.indexOf("@"));
        }

        // Get first name
        String[] parts = fullName.split("\\s+");
        return parts[0];
    }


}