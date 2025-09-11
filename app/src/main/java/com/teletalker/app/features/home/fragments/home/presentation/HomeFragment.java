package com.teletalker.app.features.home.fragments.home.presentation;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.teletalker.app.R;
import com.teletalker.app.databinding.FragmentHomeBinding;
import com.teletalker.app.features.agent_type.AgentTypeActivity;
import com.teletalker.app.features.home.StatusModel;
import com.teletalker.app.features.home.TeleTalkerStatusWidget;
import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;
import com.teletalker.app.features.subscription.presentation.SubscriptionActivity;
import com.teletalker.app.services.StatusBroadcastManager;
import com.teletalker.app.utils.PreferencesManager;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class HomeFragment extends Fragment implements CallHistoryAdapter.OnCallPlaybackListener {

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


    private TeleTalkerStatusWidget statusWidget;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        statusWidget = binding.statusIndicator;
        setupStatusListener();
        registerStatusReceiver();

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
            case "RINGING": callStatus = StatusModel.CallStatus.RINGING; break;
            case "ACTIVE": callStatus = StatusModel.CallStatus.ACTIVE; break;
            case "HOLDING": callStatus = StatusModel.CallStatus.HOLDING; break;
            case "ENDING": callStatus = StatusModel.CallStatus.ENDING; break;
            default: callStatus = StatusModel.CallStatus.IDLE; break;
        }

        statusWidget.updateCallStatus(callStatus, phoneNumber, contactName);
    }

    public void onAIStatusUpdate(String status, boolean isRecording, boolean isInjecting, String lastResponse) {
        StatusModel.AIStatus aiStatus;
        switch (status) {
            case "CONNECTING": aiStatus = StatusModel.AIStatus.CONNECTING; break;
            case "CONNECTED": aiStatus = StatusModel.AIStatus.CONNECTED; break;
            case "ACTIVE": aiStatus = StatusModel.AIStatus.ACTIVE; break;
            case "ERROR": aiStatus = StatusModel.AIStatus.ERROR; break;
            default: aiStatus = StatusModel.AIStatus.HIDDEN; break;
        }

        statusWidget.updateAIStatus(aiStatus, isRecording, isInjecting, lastResponse);
    }



    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefsManager = PreferencesManager.getInstance(getContext());

        initializeVariables(view);
        initListeners();
        observes();
        if (prefsManager.getSelectedAgentId() == null) {
            binding.AgentButtonText.setText(R.string.select_agent);
            prefsManager.setIsBotActive(false);
        } else {
            binding.AgentButtonText.setText(R.string.change_default_agent);
            binding.agentName.setText(prefsManager.getSelectedAgentName());
            binding.agentLanguage.setText(prefsManager.getSelectedAgentLanguage());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (statusReceiver != null) {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(statusReceiver);
        }

        releaseMediaPlayer();
        binding = null;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            pauseCurrentCall();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isAgentChanged) {
            isAgentChanged = !isAgentChanged;
            binding.agentName.setText(prefsManager.getSelectedAgentName());
            binding.agentLanguage.setText(prefsManager.getSelectedAgentLanguage());
        }

    }

    private void initListeners() {
        binding.subscribeButton.setOnClickListener(v -> homeViewModel.navigateToSubscriptionScreen());
        binding.seeAllHistoryTv.setOnClickListener(v -> homeViewModel.navigateToCallHistoryScreen());
        binding.changeAgentButton.setOnClickListener(v -> {
            homeViewModel.navigateToChangeAgentScreen();
            isAgentChanged = true;
        });
        binding.aiActivationButton.setImageResource(prefsManager.isBotActive() ? R.drawable.active_button : R.drawable.inactive_button);
        binding.aiActivationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (prefsManager.getSelectedAgentId() == null) {
                    Toast.makeText(getContext(), R.string.please_select_agent, LENGTH_LONG).show();
                    return;
                }
                boolean status = !prefsManager.isBotActive();
                binding.aiActivationButton.setImageResource(status ? R.drawable.active_button : R.drawable.inactive_button);
                prefsManager.setIsBotActive(status);

            }
        });

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
            }  else {
                binding.nodataIcon.setVisibility(GONE);
                adapter.updateCallHistory(callHistoryItems);

            }
            } );


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

        // CallHistoryAdapter.OnCallPlaybackListener implementations
        @Override
        public void onPlayCall (CallEntity callEntity){
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
        public void onPauseCall (CallEntity callEntity){
            pauseCurrentCall();
        }

        @Override
        public void onToggleSound (CallEntity callEntity){
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

        private void prepareAndPlayAudio (CallEntity callEntity) throws IOException {
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

        private void pauseCurrentCall () {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            }

            if (currentlyPlayingCallId > -1) {
                adapter.setCurrentlyPlaying(-1);
                currentlyPlayingCallId = -1;
            }
        }

        private void releaseMediaPlayer () {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
            }
        }
    }