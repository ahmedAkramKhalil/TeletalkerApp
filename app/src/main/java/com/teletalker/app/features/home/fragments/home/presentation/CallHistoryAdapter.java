package com.teletalker.app.features.home.fragments.home.presentation;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.teletalker.app.R;
import com.teletalker.app.databinding.SeeAllHistoryItemBinding;
import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;

import java.util.List;

public class CallHistoryAdapter extends RecyclerView.Adapter<CallHistoryAdapter.ViewHolder> {
    private List<CallEntity> callHistoryItems;
    private int currentlyPlayingId  = -1 ;
    private OnCallPlaybackListener playbackListener;

    public interface OnCallPlaybackListener {
        void onPlayCall(CallEntity callEntity);
        void onPauseCall(CallEntity callEntity);
        void onToggleSound(CallEntity callEntity);
    }

    public CallHistoryAdapter(List<CallEntity> callHistoryItems, OnCallPlaybackListener listener) {
        this.callHistoryItems = callHistoryItems;
        this.playbackListener = listener;
    }

    public void setCurrentlyPlaying(int callId) {
        int previousPlaying = this.currentlyPlayingId;
        this.currentlyPlayingId = callId;

        // Notify changes for both previous and current playing items
        if (previousPlaying > -1) {
            notifyItemChanged(getPositionForCallId(previousPlaying));
        }
        if (callId > -1) {
            notifyItemChanged(getPositionForCallId(callId));
        }
    }

    private int getPositionForCallId(int callId) {
        for (int i = 0; i < callHistoryItems.size(); i++) {
            if (callHistoryItems.get(i).id == (callId)) {
                return i;
            }
        }
        return -1;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final SeeAllHistoryItemBinding binding;

        public ViewHolder(@NonNull SeeAllHistoryItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(CallEntity callEntity) {
            // Basic info binding
            binding.nameTv.setText(callEntity.callerName);
            binding.numberTv.setText(callEntity.phoneNumber);
            binding.durationTv.setText(callEntity.duration);

            // Set call state icon (incoming/outgoing/missed)
            binding.callStateImg.setImageResource(getCallStateIcon(callEntity.callType));

            // Determine if this call is currently playing
            boolean isCurrentlyPlaying = callEntity.id == (currentlyPlayingId);

            // Set play/pause icon based on playing state
            if (isCurrentlyPlaying) {
                binding.icPlayRecorde.setImageResource(R.drawable.ic_call_running);
            } else {
                binding.icPlayRecorde.setImageResource(R.drawable.ic_big_play);
            }

            // Set click listener for play/pause button
            binding.materialCardView6.setOnClickListener(v -> {
                if (playbackListener != null) {
                    if (isCurrentlyPlaying) {
                        playbackListener.onPauseCall(callEntity);
                    } else {
                        playbackListener.onPlayCall(callEntity);
                    }
                }
            });

            // Set click listener for sound toggle button
            binding.materialCardView7.setOnClickListener(v -> {
                if (playbackListener != null) {
                    playbackListener.onToggleSound(callEntity);
                }
            });
        }

        private int getCallStateIcon(String callType) {
            switch (callType) {
                case "INCOMING":
                    return R.drawable.ic_call_incoming;
                case "OUTGOING":
                    return R.drawable.ic_call_outgoing;
//                case "MISSED":
//                    return R.drawable.mis;
                default:
                    return R.drawable.ic_call_outgoing;
            }
        }
    }

    @NonNull
    @Override
    public CallHistoryAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        SeeAllHistoryItemBinding binding = SeeAllHistoryItemBinding.inflate(layoutInflater, parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CallHistoryAdapter.ViewHolder holder, int position) {
        holder.bind(callHistoryItems.get(position));
    }

    @Override
    public int getItemCount() {
        return callHistoryItems.size();
    }

    // Method to update the adapter data
    public void updateCallHistory(List<CallEntity> newCallHistory) {
        this.callHistoryItems = newCallHistory;
        notifyDataSetChanged();
    }
}