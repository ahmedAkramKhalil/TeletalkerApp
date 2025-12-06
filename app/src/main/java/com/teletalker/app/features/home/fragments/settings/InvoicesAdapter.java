package com.teletalker.app.features.home.fragments.settings;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.teletalker.app.R;
import com.teletalker.app.databinding.ItemInvoiceBinding;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// ============================================
// INVOICE MODEL
// ============================================

// ============================================
// INVOICES ADAPTER
// ============================================
public class InvoicesAdapter extends RecyclerView.Adapter<InvoicesAdapter.InvoiceViewHolder> {

    private Context context;
    private List<Invoice> invoices = new ArrayList<>();
    private SimpleDateFormat inputDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
    private SimpleDateFormat outputDateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

    public InvoicesAdapter(Context context) {
        this.context = context;
    }

    public void setInvoices(List<Invoice> invoices) {
        this.invoices = invoices;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public InvoiceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemInvoiceBinding binding = ItemInvoiceBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new InvoiceViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull InvoiceViewHolder holder, int position) {
        Invoice invoice = invoices.get(position);
        holder.bind(invoice);
    }

    @Override
    public int getItemCount() {
        return invoices.size();
    }

    class InvoiceViewHolder extends RecyclerView.ViewHolder {
        private ItemInvoiceBinding binding;

        public InvoiceViewHolder(ItemInvoiceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Invoice invoice) {
            // Description
            binding.tvInvoiceDescription.setText(invoice.description != null
                    ? invoice.description
                    : "Standard Plan Subscription");

            // Amount
            binding.tvInvoiceAmount.setText(String.format(Locale.US,
                    "$%.2f %s", invoice.amount, invoice.currency));

            // Status badge
            setupStatusBadge(invoice.status);

            // Date
            String dateLabel;
            String dateValue;

            if ("paid".equals(invoice.status)) {
                dateLabel = "Paid on";
                dateValue = formatDate(invoice.paidAt != null ? invoice.paidAt : invoice.createdAt);
            } else if ("upcoming".equals(invoice.status)) {
                dateLabel = "Due date";
                dateValue = formatDate(invoice.dueDate);
            } else {
                dateLabel = "Created";
                dateValue = formatDate(invoice.createdAt);
            }

            binding.tvDateLabel.setText(dateLabel);
            binding.tvInvoiceDate.setText(dateValue);

            // Invoice ID (last 8 characters)
            if (invoice.id != null && invoice.id.length() > 8) {
                binding.tvInvoiceId.setText("#" + invoice.id.substring(invoice.id.length() - 8));
            } else {
                binding.tvInvoiceId.setText("#" + invoice.id);
            }
        }

        private void setupStatusBadge(String status) {
            int backgroundColor;
            int textColor;
            String statusText;

            switch (status) {
                case "paid":
                    backgroundColor = ContextCompat.getColor(context, R.color.status_paid_bg);
                    textColor = ContextCompat.getColor(context, R.color.status_paid_text);
                    statusText = "PAID";
                    break;
                case "upcoming":
                    backgroundColor = ContextCompat.getColor(context, R.color.status_upcoming_bg);
                    textColor = ContextCompat.getColor(context, R.color.status_upcoming_text);
                    statusText = "UPCOMING";
                    break;
                case "cancelled":
                    backgroundColor = ContextCompat.getColor(context, R.color.status_cancelled_bg);
                    textColor = ContextCompat.getColor(context, R.color.status_cancelled_text);
                    statusText = "CANCELLED";
                    break;
                default:
                    backgroundColor = ContextCompat.getColor(context, R.color.status_default_bg);
                    textColor = ContextCompat.getColor(context, R.color.status_default_text);
                    statusText = status.toUpperCase();
            }

            binding.tvStatus.setText(statusText);
            binding.tvStatus.setBackgroundColor(backgroundColor);
            binding.tvStatus.setTextColor(textColor);
        }

        private String formatDate(String dateString) {
            if (dateString == null || dateString.isEmpty()) {
                return "N/A";
            }

            try {
                Date date = inputDateFormat.parse(dateString);
                return outputDateFormat.format(date);
            } catch (ParseException e) {
                // If parsing fails, try to display as is
                return dateString.substring(0, Math.min(10, dateString.length()));
            }
        }
    }
}
