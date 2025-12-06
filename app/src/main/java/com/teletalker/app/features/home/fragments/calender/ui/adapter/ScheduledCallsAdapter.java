package com.teletalker.app.features.home.fragments.calender.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.teletalker.app.R;
import com.teletalker.app.features.home.fragments.calender.data.models.ScheduledCall;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScheduledCallsAdapter extends ListAdapter<ScheduledCall, ScheduledCallsAdapter.ScheduledCallViewHolder> {

    private OnScheduledCallClickListener listener;

    public interface OnScheduledCallClickListener {
        void onCallClick(ScheduledCall call);
        void onEditClick(ScheduledCall call);
        void onDeleteClick(ScheduledCall call);
    }

    public ScheduledCallsAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<ScheduledCall> DIFF_CALLBACK = new DiffUtil.ItemCallback<ScheduledCall>() {
        @Override
        public boolean areItemsTheSame(@NonNull ScheduledCall oldItem, @NonNull ScheduledCall newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull ScheduledCall oldItem, @NonNull ScheduledCall newItem) {
            return oldItem.getScheduledDateTime() == newItem.getScheduledDateTime() &&
                    oldItem.getStatus().equals(newItem.getStatus()) &&
                    oldItem.getPhoneNumber().equals(newItem.getPhoneNumber());
        }
    };

    public void setOnScheduledCallClickListener(OnScheduledCallClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ScheduledCallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_scheduled_call, parent, false);
        return new ScheduledCallViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScheduledCallViewHolder holder, int position) {
        ScheduledCall call = getItem(position);
        holder.bind(call, listener);
    }

    static class ScheduledCallViewHolder extends RecyclerView.ViewHolder {
        private TextView tvScheduledTime;
        private TextView tvStatus;
        private TextView tvContactName;
        private TextView tvPhoneNumber;
        private TextView tvDuration;
        private TextView tvConversationNotes;
        private MaterialButton btnEdit;
        private MaterialButton btnDelete;

        public ScheduledCallViewHolder(@NonNull View itemView) {
            super(itemView);
            tvScheduledTime = itemView.findViewById(R.id.tvScheduledTime);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvContactName = itemView.findViewById(R.id.tvContactName);
            tvPhoneNumber = itemView.findViewById(R.id.tvPhoneNumber);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            tvConversationNotes = itemView.findViewById(R.id.tvConversationNotes);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        public void bind(ScheduledCall call, OnScheduledCallClickListener listener) {
            // Format scheduled time
            tvScheduledTime.setText(formatScheduledTime(call.getScheduledDateTime()));

            // Set status with color
            tvStatus.setText(call.getStatus().toUpperCase());
            setStatusColor(call.getStatus());

            // Set contact info
            if (call.getContactName() != null && !call.getContactName().isEmpty()) {
                tvContactName.setText(call.getContactName());
            } else {
                tvContactName.setText("Unknown Contact");
            }
            tvPhoneNumber.setText(call.getPhoneNumber());

            // Set duration
            tvDuration.setText(call.getDurationMinutes() + " min");

            // Set AI notes
            if (call.getConversationNotes() != null && !call.getConversationNotes().isEmpty()) {
                tvConversationNotes.setText("AI: " + call.getConversationNotes());
            } else {
                tvConversationNotes.setText("No AI instructions provided");
            }

            // Click listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onCallClick(call);
            });

            btnEdit.setOnClickListener(v -> {
                if (listener != null) listener.onEditClick(call);
            });

            btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDeleteClick(call);
            });
        }

        private String formatScheduledTime(long timestamp) {
            Date date = new Date(timestamp);
            Date now = new Date();

            SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

            // Check if today
            SimpleDateFormat dayFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
            if (dayFormat.format(date).equals(dayFormat.format(now))) {
                return "Today, " + timeFormat.format(date);
            }

            // Check if tomorrow
            long tomorrow = now.getTime() + (24 * 60 * 60 * 1000);
            if (dayFormat.format(date).equals(dayFormat.format(new Date(tomorrow)))) {
                return "Tomorrow, " + timeFormat.format(date);
            }

            // Otherwise show full date
            return dateFormat.format(date) + ", " + timeFormat.format(date);
        }

        private void setStatusColor(String status) {
            int color;
            switch (status.toLowerCase()) {
                case "pending":
                    color = 0xFF2196F3; // Blue
                    break;
                case "completed":
                    color = 0xFF4CAF50; // Green
                    break;
                case "failed":
                    color = 0xFFF44336; // Red
                    break;
                case "cancelled":
                    color = 0xFF9E9E9E; // Gray
                    break;
                default:
                    color = 0xFF607D8B; // Blue Gray
                    break;
            }
            tvStatus.setBackgroundColor(color);
        }
    }
}