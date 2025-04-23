package com.teletalker.app.features.home.fragments.home.presentation;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.teletalker.app.databinding.CallHistoryItemBinding;
import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;

import java.util.List;

public class CallHistoryAdapter extends RecyclerView.Adapter<CallHistoryAdapter.ViewHolder> {
    private List<CallEntity> callHistoryItems;
    public CallHistoryAdapter(List<CallEntity> callHistoryItems) {
        this.callHistoryItems = callHistoryItems;
    }
    public class ViewHolder extends RecyclerView.ViewHolder {
        private final CallHistoryItemBinding binding;
        public ViewHolder(@NonNull CallHistoryItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

     /*   public void bind(CallEntity person) {
            binding.nameTv.setText(person.getContactName());
            binding.languageTv.setText(person.getLanguage());
            binding.personImg.setImageResource(person.getProfileImage());
            binding.timeTv.setText(person.getTimestamp());
        }*/
    }
    @NonNull
    @Override
    public CallHistoryAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        CallHistoryItemBinding binding = CallHistoryItemBinding.inflate(layoutInflater, parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CallHistoryAdapter.ViewHolder holder, int position) {
        /*holder.bind(callHistoryItems.get(position));*/
    }

    @Override
    public int getItemCount() {
        return callHistoryItems.size();
    }
}
