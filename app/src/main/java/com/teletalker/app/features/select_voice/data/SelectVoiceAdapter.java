package com.teletalker.app.features.select_voice.data;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.teletalker.app.databinding.ItemVoicerBinding;
import com.teletalker.app.databinding.SeeAllHistoryItemBinding;
import com.teletalker.app.features.select_voice.data.models.TypeVoiceDM;

import java.util.List;

public class SelectVoiceAdapter extends RecyclerView.Adapter<SelectVoiceAdapter.ViewHolder> {

    private List<TypeVoiceDM> typeVoices;
    public SelectVoiceAdapter(List<TypeVoiceDM> typeVoices) {
        this.typeVoices = typeVoices;
    }
    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemVoicerBinding binding;

        public ViewHolder(@NonNull ItemVoicerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TypeVoiceDM person) {
            binding.nameTv.setText(person.getName());
            binding.lagTv.setText(person.getLang());
            binding.image.setImageResource(person.getImage());
        }
    }

    @NonNull
    @Override
    public SelectVoiceAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        ItemVoicerBinding binding = ItemVoicerBinding.inflate(layoutInflater, parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SelectVoiceAdapter.ViewHolder holder, int position) {
        holder.bind(typeVoices.get(position));
    }

    @Override
    public int getItemCount() {
        return typeVoices.size();
    }
}
