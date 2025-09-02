package com.teletalker.app.features.home.fragments.home.presentation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaPlayer;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.AdapterView;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import com.teletalker.app.R;
import com.teletalker.app.databinding.SeeAllHistoryItemBinding;
import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;

import java.util.ArrayList;
import java.util.List;

public class CallHistoryAdapter extends RecyclerView.Adapter<CallHistoryAdapter.ViewHolder> {
    private List<CallEntity> callHistoryItems;

    public CallHistoryAdapter(List<CallEntity> callHistoryItems) {
        this.callHistoryItems = callHistoryItems != null ? callHistoryItems : new ArrayList<>();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        SeeAllHistoryItemBinding binding = SeeAllHistoryItemBinding.inflate(layoutInflater, parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(callHistoryItems.get(position));
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

        public void bind(CallEntity call) {
            binding.setCall(call);
            binding.executePendingBindings();

            // Set other data if needed
            binding.numberTv.setText(call.phoneNumber != null ? call.phoneNumber : "Unknown");
            binding.nameTv.setText(call.callerName != null ? call.callerName : "Unknown");
            binding.durationTv.setText(call.duration != null ? call.duration : "0 sec");

            // Set call status icon
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
        }
    }

    public void updateData(List<CallEntity> newItems) {
        callHistoryItems = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }
}