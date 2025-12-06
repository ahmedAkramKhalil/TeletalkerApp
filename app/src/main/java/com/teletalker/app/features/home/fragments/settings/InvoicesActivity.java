package com.teletalker.app.features.home.fragments.settings;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.functions.FirebaseFunctions;
import com.teletalker.app.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InvoicesActivity extends AppCompatActivity {

    private TabLayout tabLayout;
    private RecyclerView recyclerViewInvoices;
    private TextView tvInvoiceCount;
    private TextView tvTotalAmount;
    private View emptyStateLayout;
    private TextView tvEmptyState;
    private CircularProgressIndicator progressBar;
    private View contentLayout;

    private FirebaseFunctions functions;
    private InvoicesAdapter adapter;
    private List<Invoice> allInvoices = new ArrayList<>();
    private String currentFilter = "all";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invoices);

        // Setup toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Invoices");
        }

        functions = FirebaseFunctions.getInstance();

        initViews();
        setupRecyclerView();
        setupTabs();
        loadInvoices();
    }

    private void initViews() {
        tabLayout = findViewById(R.id.tabLayout);
        recyclerViewInvoices = findViewById(R.id.recyclerViewInvoices);
        tvInvoiceCount = findViewById(R.id.tvInvoiceCount);
        tvTotalAmount = findViewById(R.id.tvTotalAmount);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        progressBar = findViewById(R.id.progressBar);
        contentLayout = findViewById(R.id.contentLayout);
    }

    private void setupRecyclerView() {
        adapter = new InvoicesAdapter(this);
        recyclerViewInvoices.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewInvoices.setAdapter(adapter);
    }

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("All"));
        tabLayout.addTab(tabLayout.newTab().setText("Paid"));
        tabLayout.addTab(tabLayout.newTab().setText("Upcoming"));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0:
                        currentFilter = "all";
                        break;
                    case 1:
                        currentFilter = "paid";
                        break;
                    case 2:
                        currentFilter = "upcoming";
                        break;
                }
                filterInvoices();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void loadInvoices() {
        showLoading(true);

        Map<String, Object> data = new HashMap<>();
        // Don't pass status to get all invoices

        functions.getHttpsCallable("getUserInvoices")
                .call(data)
                .addOnSuccessListener(result -> {
                    try {
                        Map<String, Object> responseData = (Map<String, Object>) result.getData();
                        List<Map<String, Object>> invoicesData =
                                (List<Map<String, Object>>) responseData.get("invoices");

                        allInvoices.clear();

                        if (invoicesData != null) {
                            for (Map<String, Object> invoiceMap : invoicesData) {
                                Invoice invoice = new Invoice();
                                invoice.id = (String) invoiceMap.get("id");
                                invoice.amount = ((Number) invoiceMap.get("amount")) != null ?  ((Number) invoiceMap.get("amount")).doubleValue() : 0.0;
                                invoice.currency = (String) invoiceMap.get("currency");
                                invoice.status = (String) invoiceMap.get("status");
                                invoice.type = (String) invoiceMap.get("type");
                                invoice.description = (String) invoiceMap.get("description");
                                invoice.dueDate = (String) invoiceMap.get("dueDate");
                                invoice.createdAt = (String) invoiceMap.get("createdAt");
                                invoice.paidAt = (String) invoiceMap.get("paidAt");
                                allInvoices.add(invoice);
                            }
                        }

                        filterInvoices();
                        showLoading(false);

                        if (allInvoices.isEmpty()) {
                            showEmptyState("No invoices yet");
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Error parsing invoices", Toast.LENGTH_SHORT).show();
                        Log.d("error","Error parsing invoices: " + e.getMessage());

                        showLoading(false);
                        showEmptyState("Error loading invoices");
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this,
                            "Error loading invoices: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    Log.d("error","Error loading invoices: " + e.getMessage());
                    showLoading(false);
                    showEmptyState("Error loading invoices");
                });
    }

    private void filterInvoices() {
        List<Invoice> filteredInvoices = new ArrayList<>();

        if ("all".equals(currentFilter)) {
            filteredInvoices.addAll(allInvoices);
        } else {
            for (Invoice invoice : allInvoices) {
                if (currentFilter.equals(invoice.status)) {
                    filteredInvoices.add(invoice);
                }
            }
        }

        adapter.setInvoices(filteredInvoices);

        // Update summary
        updateSummary(filteredInvoices);

        // Show/hide empty state
        if (filteredInvoices.isEmpty()) {
            String message;
            switch (currentFilter) {
                case "paid":
                    message = "No paid invoices yet";
                    break;
                case "upcoming":
                    message = "No upcoming invoices";
                    break;
                default:
                    message = "No invoices found";
            }
            showEmptyState(message);
        } else {
            hideEmptyState();
        }
    }

    private void updateSummary(List<Invoice> invoices) {
        int count = invoices.size();
        double total = 0;

        for (Invoice invoice : invoices) {
            if ("paid".equals(invoice.status)) {
                total += invoice.amount;
            }
        }

        tvInvoiceCount.setText(count + " invoices");

        if ("paid".equals(currentFilter)) {
            tvTotalAmount.setVisibility(View.VISIBLE);
            tvTotalAmount.setText(String.format("Total: $%.2f", total));
        } else {
            tvTotalAmount.setVisibility(View.GONE);
        }
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        contentLayout.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showEmptyState(String message) {
        emptyStateLayout.setVisibility(View.VISIBLE);
        recyclerViewInvoices.setVisibility(View.GONE);
        tvEmptyState.setText(message);
    }

    private void hideEmptyState() {
        emptyStateLayout.setVisibility(View.GONE);
        recyclerViewInvoices.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}