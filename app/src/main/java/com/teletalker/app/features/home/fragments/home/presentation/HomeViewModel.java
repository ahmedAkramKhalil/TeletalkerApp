package com.teletalker.app.features.home.fragments.home.presentation;

import android.app.Application;
import android.content.Context;
import android.media.MediaPlayer;
import android.widget.Toast;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.teletalker.app.features.home.fragments.callhistory.data.data_sources.local.CallLocalDataSourceImpl;
import com.teletalker.app.features.home.fragments.callhistory.data.data_sources.local.dao.CallDao;
import com.teletalker.app.features.home.fragments.callhistory.data.data_sources.local.database.CallDatabase;
import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;
import com.teletalker.app.features.home.fragments.callhistory.data.repository.CallRepository;
import com.teletalker.app.features.home.fragments.callhistory.data.repository.CallRepositoryImpl;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;

public class HomeViewModel extends AndroidViewModel {
    final MutableLiveData<HomeFragmentEvents> events = new MutableLiveData<>();
    private final MutableLiveData<List<CallEntity>> callHistoryLiveData = new MutableLiveData<>();
    private CallRepository callRepository;

    public HomeViewModel(Application application) {
        super(application);
        CallDao callDao = CallDatabase.getInstance(application).callDao();
        callRepository = new CallRepositoryImpl(new CallLocalDataSourceImpl(callDao));
        loadCallHistory();
    }


    public void navigateToSubscriptionScreen() {
        events.setValue(HomeFragmentEvents.NavigateToSubscriptionScreen.INSTANCE);
    }

    public void navigateToCallHistoryScreen() {
        events.setValue(HomeFragmentEvents.NavigateToCallHistoryScreen.INSTANCE);
    }

    public void clearNavigationState() {
        events.setValue(null);
    }

    public void navigateToChangeAgentScreen() {
        events.setValue(HomeFragmentEvents.NavigateToAgentTypeActivity.INSTANCE);
    }

    public LiveData<List<CallEntity>> getCallHistoryLiveData() {
        return callHistoryLiveData;
    }

    private void loadCallHistory() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<CallEntity> callHistory = callRepository.getLastTwoCallRecords();
            callHistoryLiveData.postValue(callHistory);
        });
    }



    private MediaPlayer mediaPlayer;
    private MutableLiveData<CallEntity> currentlyPlaying = new MutableLiveData<>();

    public void togglePlayback(Context context, CallEntity call) {
        if (call == null || call.recordingFilePath == null) return;

        MediaPlayer player = getMediaPlayer();
        CallEntity current = currentlyPlaying.getValue();

        // If same call is playing, pause it
        if (current != null && current.recordingFilePath.equals(call.recordingFilePath) && player.isPlaying()) {
            player.pause();
            currentlyPlaying.setValue(null);
            return;
        }

        // Stop current playback if different call
        if (player.isPlaying()) {
            player.stop();
            player.reset();
        }

        // Start new playback
        try {
            player.setDataSource(call.recordingFilePath);
            player.prepare();
            player.start();
            currentlyPlaying.setValue(call);

            // Auto-reset when playback completes
            player.setOnCompletionListener(mp -> {
                currentlyPlaying.postValue(null);
            });

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Failed to play recording", Toast.LENGTH_SHORT).show();
            currentlyPlaying.setValue(null);
        }
    }

    public void stopPlayback() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset();
            currentlyPlaying.setValue(null);
        }
    }



    private MediaPlayer getMediaPlayer() {
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        }
        return mediaPlayer;
    }

    public LiveData<CallEntity> getCurrentlyPlaying() {
        return currentlyPlaying;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }



}