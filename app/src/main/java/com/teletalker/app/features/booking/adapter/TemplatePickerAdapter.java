package com.teletalker.app.features.booking.adapter;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.teletalker.app.R;
import com.teletalker.app.features.booking.data.BookingTemplate;

import java.util.List;

/**
 * Horizontal RecyclerView adapter that renders the 19 booking-page templates
 * as picker cards. Single-selection — tapping a card sets it as the merchant's
 * chosen template. The "Preview" button opens the live template URL in the
 * system browser so the merchant can see it before saving.
 */
public class TemplatePickerAdapter extends RecyclerView.Adapter<TemplatePickerAdapter.VH> {

    public interface OnTemplateSelected {
        void onSelected(@NonNull BookingTemplate template);
    }

    private final List<BookingTemplate> items = BookingTemplate.ALL;
    private final OnTemplateSelected listener;
    private String selectedId;

    public TemplatePickerAdapter(String selectedId, OnTemplateSelected listener) {
        this.selectedId = (selectedId == null || selectedId.isEmpty()) ? "dental-clinic" : selectedId;
        this.listener = listener;
    }

    public void setSelectedId(@NonNull String id) {
        if (id.equalsIgnoreCase(selectedId)) return;
        String previous = selectedId;
        selectedId = id;
        // Refresh only the two affected positions to avoid full rebind.
        notifyDataSetChanged();
        // (Catalog is short and rebind is cheap; explicit positions add complexity
        //  for no real perf win on 19 items.)
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_booking_template_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        BookingTemplate t = items.get(position);
        Context ctx = h.itemView.getContext();

        h.name.setText(t.name);
        h.tagline.setText(t.tagline);

        boolean selected = t.id.equalsIgnoreCase(selectedId);
        h.tick.setVisibility(selected ? View.VISIBLE : View.GONE);
        h.card.setStrokeWidth(selected
                ? (int) (2 * ctx.getResources().getDisplayMetrics().density) : 0);

        Glide.with(h.thumb.getContext())
                .load(t.thumbnailUrl())
                .placeholder(android.R.color.darker_gray)
                .error(android.R.color.darker_gray)
                .into(h.thumb);

        h.card.setOnClickListener(v -> {
            if (!t.id.equalsIgnoreCase(selectedId)) {
                setSelectedId(t.id);
                if (listener != null) listener.onSelected(t);
            }
        });

        h.previewBtn.setOnClickListener(v -> {
            try {
                ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(t.previewUrl())));
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView thumb;
        final ImageView tick;
        final TextView name;
        final TextView tagline;
        final MaterialButton previewBtn;

        VH(@NonNull View v) {
            super(v);
            card       = (MaterialCardView) v;
            thumb      = v.findViewById(R.id.templateThumb);
            tick       = v.findViewById(R.id.templateSelectedTick);
            name       = v.findViewById(R.id.templateName);
            tagline    = v.findViewById(R.id.templateTagline);
            previewBtn = v.findViewById(R.id.templatePreviewBtn);
        }
    }
}
