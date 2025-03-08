package com.teletalker.app.features.home.presentation.fragments.callhistory;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.teletalker.app.databinding.CallHistoryItemBinding;
import com.teletalker.app.databinding.SeeAllHistoryItemBinding;
import com.teletalker.app.features.home.data.models.CallHistoryModel;

import java.util.List;

public class SeeAllHistoryAdapter extends RecyclerView.Adapter<SeeAllHistoryAdapter.ViewHolder> {
    private List<CallHistoryModel> callHistoryItems;
    public SeeAllHistoryAdapter(List<CallHistoryModel> callHistoryItems) {
        this.callHistoryItems = callHistoryItems;
    }
    public class ViewHolder extends RecyclerView.ViewHolder {
        private final SeeAllHistoryItemBinding binding;
        public ViewHolder(@NonNull SeeAllHistoryItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(CallHistoryModel person) {
            binding.nameTv.setText(person.getContactName());
            binding.numberTv.setText(person.getPhoneNumber());
            binding.callTypeIm.setImageResource(person.getCallTypeImage());
            binding.timeTv.setText(person.getTimestamp());
        }
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
        holder.bind(callHistoryItems.get(position));
    }

    @Override
    public int getItemCount() {
        return callHistoryItems.size();
    }
}
