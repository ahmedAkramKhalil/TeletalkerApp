package com.teletalker.app.features.home.fragments.callhistory.presentation;

import android.annotation.SuppressLint;
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

import com.teletalker.app.databinding.FragmentCallHistoryBinding;
import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;
import com.teletalker.app.features.home.fragments.callhistory.presentation.adapters.SeeAllHistoryAdapter;

import java.io.IOException;
import java.util.ArrayList;

public class CallHistoryFragment extends Fragment {

    private FragmentCallHistoryBinding binding;
    SeeAllHistoryAdapter adapter;
    CallHistoryViewModel viewModel;

    // MediaPlayer management
    private MediaPlayer mediaPlayer;
    private int currentlyPlayingId = -1;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentCallHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeVariables();
        observers();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopAndReset();
        binding = null;
    }

    @Override
    public void onPause() {
        super.onPause();
        stopAndReset();
    }

    @Override
    public void onStop() {
        super.onStop();
        stopAndReset();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            // Fragment is hidden, stop playback
            stopAndReset();
        }
    }

    void initializeVariables(){
        viewModel = new ViewModelProvider(this).get(CallHistoryViewModel.class);

        // Initialize MediaPlayer
        mediaPlayer = new MediaPlayer();
        setupMediaPlayerListeners();

        adapter = new SeeAllHistoryAdapter(new ArrayList<>(),
                new SeeAllHistoryAdapter.OnItemClickListener() {
                    @Override
                    public void onItemClick(CallEntity item) {
                        Log.d("CallHistoryFragment", "Item clicked: " + item.getRecordingFilePath());
                        togglePlayback(item);
                    }
                });

        binding.recyclerView.setAdapter(adapter);
    }

    private void setupMediaPlayerListeners() {
        mediaPlayer.setOnCompletionListener(mp -> {
            // When audio completes, reset everything
            Log.d("CallHistoryFragment", "Playback completed");
            resetPlaybackState();
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Log.e("CallHistoryFragment", "MediaPlayer error: " + what);
            Toast.makeText(getContext(), "Error playing audio", Toast.LENGTH_SHORT).show();
            resetPlaybackState();
            return true;
        });
    }

    private void togglePlayback(CallEntity item) {
        try {
            if (currentlyPlayingId == item.id && mediaPlayer.isPlaying()) {
                // Currently playing this item, so pause it
                pausePlayback();
            } else {
                // Start playing this item
                startPlayback(item);
            }
        } catch (Exception e) {
            Log.e("CallHistoryFragment", "Error in togglePlayback: " + e.getMessage());
            Toast.makeText(getContext(), "Failed to play audio", Toast.LENGTH_SHORT).show();
            resetPlaybackState();
        }
    }

    private void startPlayback(CallEntity item) throws IOException {
        // Stop any current playback first
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }

        // Reset all icons first
        adapter.resetAllPlayIcons();

        // Prepare new audio
        mediaPlayer.reset();
        mediaPlayer.setDataSource(item.getRecordingFilePath());
        mediaPlayer.prepare();
        mediaPlayer.start();

        // Update state
        currentlyPlayingId = item.id;
        adapter.setItemPlaying(item.id, true);

        Log.d("CallHistoryFragment", "Started playing: " + item.callerName);
    }

    private void pausePlayback() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }

        // Reset icons and state
        resetPlaybackState();
        Log.d("CallHistoryFragment", "Paused playback");
    }

    private void resetPlaybackState() {
        currentlyPlayingId = -1;
        if (adapter != null) {
            adapter.resetAllPlayIcons();
        }
    }

    private void stopAndReset() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
            }
            resetPlaybackState();
            Log.d("CallHistoryFragment", "Stopped and reset playback");
        } catch (Exception e) {
            Log.e("CallHistoryFragment", "Error stopping playback: " + e.getMessage());
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    void observers(){
        viewModel.getCallHistoryLiveData().observe(getViewLifecycleOwner(), callHistoryItems -> {
            adapter.updateData(callHistoryItems);
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Release MediaPlayer resources
        if (mediaPlayer != null) {
            try {
                mediaPlayer.release();
            } catch (Exception e) {
                Log.e("CallHistoryFragment", "Error releasing MediaPlayer: " + e.getMessage());
            }
            mediaPlayer = null;
        }
    }
}