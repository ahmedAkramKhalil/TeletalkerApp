package com.teletalker.app.features.home.fragments.callhistory.presentation.adapters;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.teletalker.app.R;
import com.teletalker.app.databinding.SeeAllHistoryItemBinding;
import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;

import java.util.List;

public class SeeAllHistoryAdapter extends RecyclerView.Adapter<SeeAllHistoryAdapter.ViewHolder> {
    private List<CallEntity> callHistoryItems;
    private int currentlyPlayingId = -1;

    public interface OnItemClickListener {
        void onItemClick(CallEntity item);
    }

    private OnItemClickListener clickListener;

    public SeeAllHistoryAdapter(List<CallEntity> callHistoryItems, OnItemClickListener listener) {
        this.callHistoryItems = callHistoryItems;
        this.clickListener = listener;
    }

    /**
     * Reset all play icons to "play" state (idle)
     */
    @SuppressLint("NotifyDataSetChanged")
    public void resetAllPlayIcons() {
        currentlyPlayingId = -1;
        notifyDataSetChanged();
    }

    /**
     * Set a specific item as playing
     * @param itemId The ID of the call item
     * @param isPlaying Whether the item is currently playing
     */
    public void setItemPlaying(int itemId, boolean isPlaying) {
        int previousPlayingId = currentlyPlayingId;

        if (isPlaying) {
            currentlyPlayingId = itemId;
        } else {
            currentlyPlayingId = -1;
        }

        // Update the previous playing item (if any)
        if (previousPlayingId != -1) {
            int previousPosition = getPositionForCallId(previousPlayingId);
            if (previousPosition >= 0) {
                notifyItemChanged(previousPosition);
            }
        }

        // Update the current item
        int currentPosition = getPositionForCallId(itemId);
        if (currentPosition >= 0) {
            notifyItemChanged(currentPosition);
        }
    }

    private int getPositionForCallId(int callId) {
        for (int i = 0; i < callHistoryItems.size(); i++) {
            if (callHistoryItems.get(i).id == callId) {
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
            boolean isCurrentlyPlaying = callEntity.id == currentlyPlayingId;

            // Set play/pause icon based on playing state
            if (isCurrentlyPlaying) {
                binding.icPlayRecorde.setImageResource(R.drawable.ic_call_running); // pause/playing icon
            } else {
                binding.icPlayRecorde.setImageResource(R.drawable.ic_big_play); // play icon (idle)
            }

            // Set click listener for play/pause button
            binding.materialCardView6.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onItemClick(callEntity);
                }
            });

            // Optional: Sound toggle button (you can implement this later if needed)
            binding.materialCardView7.setOnClickListener(v -> {
                // Sound toggle functionality can be added here
            });
        }

        private int getCallStateIcon(String callType) {
            switch (callType) {
                case "INCOMING":
                    return R.drawable.ic_call_incoming;
                case "OUTGOING":
                    return R.drawable.ic_call_outgoing;
                case "MISSED":
                    return R.drawable.ic_call_outgoing; // Use outgoing as fallback, add missed icon later
                default:
                    return R.drawable.ic_call_outgoing;
            }
        }
    }

    @NonNull
    @Override
    public SeeAllHistoryAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        SeeAllHistoryItemBinding binding = SeeAllHistoryItemBinding.inflate(layoutInflater, parent, false);
        return new SeeAllHistoryAdapter.ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SeeAllHistoryAdapter.ViewHolder holder, int position) {
        holder.bind(callHistoryItems.get(position));
    }

    @Override
    public int getItemCount() {
        return callHistoryItems.size();
    }

    /**
     * Method to update the adapter data
     */
    @SuppressLint("NotifyDataSetChanged")
    public void updateData(List<CallEntity> newCallHistory) {
        this.callHistoryItems = newCallHistory;
        // Reset playing state when data updates
        currentlyPlayingId = -1;
        notifyDataSetChanged();
    }
}