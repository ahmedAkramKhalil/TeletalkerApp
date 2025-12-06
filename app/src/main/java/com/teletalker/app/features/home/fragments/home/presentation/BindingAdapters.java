package com.teletalker.app.features.home.fragments.home.presentation;

import android.content.Context;
import android.widget.ImageView;

import androidx.databinding.BindingAdapter;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import com.teletalker.app.R;
import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;

// Create a new class BindingAdapters.java
// BindingAdapters.java
public class BindingAdapters {

    @BindingAdapter("onPlaybackClick")
    public static void setOnPlaybackClickListener(ImageView imageView, CallEntity call) {
        imageView.setOnClickListener(v -> {
            if (call != null && call.isCallRecorded() && call.getRecordingFilePath() != null) {
                Context context = imageView.getContext();
                if (context instanceof FragmentActivity) {
                    FragmentActivity activity = (FragmentActivity) context;
                    HomeViewModel viewModel = new ViewModelProvider(activity)
                            .get(HomeViewModel.class);
                    viewModel.togglePlayback(context, call);
                }
            }
        });
    }

    @BindingAdapter("playbackState")
    public static void setPlaybackState(ImageView imageView, CallEntity call) {
        if (call == null) return;

        Context context = imageView.getContext();
        if (context instanceof FragmentActivity) {
            FragmentActivity activity = (FragmentActivity) context;
            HomeViewModel viewModel = new ViewModelProvider(activity)
                    .get(HomeViewModel.class);

            // Use the view's lifecycle to avoid memory leaks
            LifecycleOwner lifecycleOwner = null;
            if (context instanceof LifecycleOwner) {
                lifecycleOwner = (LifecycleOwner) context;
            }

            if (lifecycleOwner != null) {
                viewModel.getCurrentlyPlaying().observe(lifecycleOwner, playingCall -> {
                    boolean isPlaying = playingCall != null &&
                            playingCall.getRecordingFilePath() != null &&
                            playingCall.getRecordingFilePath().equals(call.getRecordingFilePath());

                    imageView.setImageResource(
                            isPlaying ? R.drawable.ic_call_running : R.drawable.ic_big_play
                    );
                });
            }
        }
    }
}