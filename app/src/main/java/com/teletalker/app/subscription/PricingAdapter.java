package com.teletalker.app.subscription;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.teletalker.app.R;

import java.util.List;

// PricingAdapter.java
public class PricingAdapter extends RecyclerView.Adapter<PricingAdapter.PriceViewHolder> {

    private List<Price> prices;
    private int selectedPosition = -1;
    private OnPriceSelectedListener listener;

    public interface OnPriceSelectedListener {
        void onPriceSelected(Price price, int position);
    }

    public PricingAdapter(List<Price> prices, OnPriceSelectedListener listener) {
        this.prices = prices;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PriceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pricing, parent, false);
        return new PriceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PriceViewHolder holder, int position) {
        Price price = prices.get(position);
        holder.bind(price, position == selectedPosition);

        holder.itemView.setOnClickListener(v -> {
            int oldPosition = selectedPosition;
            selectedPosition = holder.getAdapterPosition();

            notifyItemChanged(oldPosition);
            notifyItemChanged(selectedPosition);

            if (listener != null) {
                listener.onPriceSelected(price, selectedPosition);
            }
        });
    }

    @Override
    public int getItemCount() {
        return prices.size();
    }

    public Price getSelectedPrice() {
        if (selectedPosition >= 0 && selectedPosition < prices.size()) {
            return prices.get(selectedPosition);
        }
        return null;
    }

    class PriceViewHolder extends RecyclerView.ViewHolder {
        private CardView cardView;
        private TextView tvPriceName;
        private TextView tvPrice;
        private TextView tvBillingCycle;
        private TextView tvDescription;
        private TextView tvSavings;
        private TextView tvTrial;
        private View popularBadge;
        private ImageView ivCheckmark;

        public PriceViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardView);
            tvPriceName = itemView.findViewById(R.id.tvPriceName);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvBillingCycle = itemView.findViewById(R.id.tvBillingCycle);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvSavings = itemView.findViewById(R.id.tvSavings);
            tvTrial = itemView.findViewById(R.id.tvTrial);
            popularBadge = itemView.findViewById(R.id.popularBadge);
            ivCheckmark = itemView.findViewById(R.id.ivCheckmark);
        }

        public void bind(Price price, boolean isSelected) {
            // Set basic info
            tvPriceName.setText(price.getDescription());
            tvPrice.setText(price.getFormattedPrice());
            tvBillingCycle.setText(price.getBillingCycleText());

            // Show/hide popular badge
            popularBadge.setVisibility(price.isPopular() ? View.VISIBLE : View.GONE);

            // Show trial if available
            if (price.getTrialDays() > 0) {
                tvTrial.setVisibility(View.VISIBLE);
                tvTrial.setText(price.getTrialDays() + " days free trial");
            } else {
                tvTrial.setVisibility(View.GONE);
            }

            // Calculate and show savings for annual plans
            if ("year".equals(price.getBillingCycle())) {
                Price monthlyPrice = findMonthlyPrice();
//                String savings = price.get(monthlyPrice);
//                if (savings != null) {
//                    tvSavings.setVisibility(View.VISIBLE);
//                    tvSavings.setText(savings);
//                } else {
//                    tvSavings.setVisibility(View.GONE);
//                }
            } else {
                tvSavings.setVisibility(View.GONE);
            }

            // Update selection state
            if (isSelected) {
                cardView.setCardElevation(12f);
//                cardView.setStrokeWidth(4);
//                cardView.setStrokeColor(itemView.getContext()
//                        .getResources().getColor(R.color.colorPrimary));
                ivCheckmark.setVisibility(View.VISIBLE);
            } else {
                cardView.setCardElevation(4f);
//                cardView.setStrokeWidth(1);
//                cardView.setStrokeColor(itemView.getContext()
//                        .getResources().getColor(R.color.colorLightGray));
                ivCheckmark.setVisibility(View.GONE);
            }
        }

        private Price findMonthlyPrice() {
            for (Price p : prices) {
                if ("month".equals(p.getBillingCycle())) {
                    return p;
                }
            }
            return null;
        }
    }
}