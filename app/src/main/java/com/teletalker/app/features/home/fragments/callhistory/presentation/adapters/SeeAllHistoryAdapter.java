package com.teletalker.app.features.home.fragments.callhistory.presentation.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaPlayer;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.AdapterView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.teletalker.app.R;
import com.teletalker.app.databinding.SeeAllHistoryItemBinding;
import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;
import com.teletalker.app.services.CallRecorderManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SeeAllHistoryAdapter extends RecyclerView.Adapter<SeeAllHistoryAdapter.ViewHolder> {
    private List<CallEntity> callHistoryItems;

    private OnItemClickListener listener;
    private MediaPlayer mediaPlayer;
    private int currentPlayingPosition = -1;
    public SeeAllHistoryAdapter(List<CallEntity> callHistoryItems, OnItemClickListener listener
    ) {
        this.callHistoryItems = callHistoryItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SeeAllHistoryAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        SeeAllHistoryItemBinding binding = SeeAllHistoryItemBinding.inflate(layoutInflater, parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SeeAllHistoryAdapter.ViewHolder holder, int position) {
        holder.bind(callHistoryItems.get(position), position);
    }

    @Override
    public int getItemCount() {
        return callHistoryItems.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final SeeAllHistoryItemBinding binding;

        public ViewHolder(@NonNull SeeAllHistoryItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(CallEntity call, int position) {
            binding.numberTv.setText(call.phoneNumber != null ? call.phoneNumber : "Unknown");
            binding.nameTv.setText(call.callerName != null ? call.callerName : "Unknown");
            binding.durationTv.setText(call.duration != null ? call.duration : "0 sec");

            String status = call.callStatus != null ? call.callStatus : "";
            switch (status) {
                case "IncomingAnswered":
                    binding.callStateImg.setImageResource(R.drawable.ic_call_incoming);
                    break;
                case "Outgoing":
                    binding.callStateImg.setImageResource(R.drawable.ic_call_outgoing);
                    break;
                case "Missed":
                    binding.callStateImg.setImageResource(R.drawable.ic_call_slash);
                    break;
            }

            if (call.isCallRecordingPlay() && call.isCallRecorded()) {
                binding.icPlayRecorde.setImageResource(R.drawable.ic_call_running);
            } else {
                binding.icPlayRecorde.setImageResource(R.drawable.ic_big_play);
            }

            binding.icPlayRecorde.setOnClickListener(v -> {
                if (call.isCallRecordingPlay()) {

                    call.toggleCallRecordingPlay();
                    mediaPlayer.pause();
                    binding.icPlayRecorde.setImageResource(R.drawable.ic_big_play);
                    currentPlayingPosition = -1;
                } else {
                    stopAllOtherRecordings(position);

                    call.setCallRecordingPlay(true);
                    mediaPlayer = new MediaPlayer();
                    try {
                        mediaPlayer.setDataSource(call.recordingFilePath);
                        mediaPlayer.prepare();
                        mediaPlayer.start();
                        binding.icPlayRecorde.setImageResource(R.drawable.ic_call_running);
                        currentPlayingPosition = position;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                notifyDataSetChanged();
            });
        }

    }

    public void updateData(List<CallEntity> newItems) {
        callHistoryItems = newItems;
        notifyDataSetChanged();
    }

    public interface OnItemClickListener {
        void onItemClick(CallEntity item);
    }

    private void stopAllOtherRecordings(int position) {
        for (int i = 0; i < callHistoryItems.size(); i++) {
            if (i != position) {
                CallEntity callEntity = callHistoryItems.get(i);
                if (callEntity.isCallRecordingPlay()) {
                    callEntity.setCallRecordingPlay(false);
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                }
            }
        }
    }
    }
