package com.teletalker.app.features.home.fragments.settings;// ============================================
// SubscriptionActivity.java - Adapted to Your Design
// ============================================


import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.functions.FirebaseFunctions;
import com.teletalker.app.R;
import com.teletalker.app.databinding.ActivitySubscription2Binding;
import com.teletalker.app.databinding.ActivitySubscriptionBinding;
import com.teletalker.app.network.DeductMinutesResponse;
import com.teletalker.app.network.FirebaseFunctionsManager;
import com.teletalker.app.network.InvoiceResponse;
import com.teletalker.app.network.Package;
import com.teletalker.app.network.UserBalance;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SubscriptionActivity extends AppCompatActivity {

    private ActivitySubscription2Binding binding;
    private FirebaseFunctionsManager functionsManager;
    private FirebaseAuth auth;

    private List<com.teletalker.app.network.Package> packages;
    private String selectedPackageId = null;
    private UserBalance currentBalance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySubscription2Binding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase
        functionsManager = FirebaseFunctionsManager.getInstance();
        auth = FirebaseAuth.getInstance();

        setupToolbar();
        setupListeners();
        loadData();
    }

    // ============================================
    // SETUP
    // ============================================

    private void setupToolbar() {
        binding.arrowBack.setOnClickListener(v -> finish());
    }

    private void setupListeners() {
        // Package selection listener
        binding.subscriptionGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId != -1) {
                selectedPackageId = (String) findViewById(checkedId).getTag();
                binding.subscribeButton.setEnabled(true);
                updateSubscribeButton();
            }
        });

        // Subscribe button listener
        binding.subscribeButton.setOnClickListener(v -> createTopUpInvoice());
    }

    // ============================================
    // LOAD DATA
    // ============================================

    private void loadData() {
        showLoading(true);

        // Load user info
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            updateUserInfo(currentUser);
        }

        // Load balance and packages in parallel
        loadBalance();
        loadPackages();
    }

    private void updateUserInfo(FirebaseUser user) {
        // Update product name to include email or user info
        String email = user.getEmail();
        if (email != null) {
            binding.tvProductDescription.setText(
                    String.format("Welcome %s! Choose a top-up package",
                            email.split("@")[0])
            );
        }
    }

    private void loadBalance() {
        functionsManager.getBalance(new FirebaseFunctionsManager.OnBalanceCallback() {
            @Override
            public void onSuccess(UserBalance balance) {
                currentBalance = balance;
                updateBalanceDisplay(balance);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(SubscriptionActivity.this,
                        "Error loading balance: " + error,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadPackages() {
        functionsManager.getPackages(null, new FirebaseFunctionsManager.OnPackagesCallback() {

            @Override
            public void onSuccess(List<com.teletalker.app.network.Package> packageList) {
                packages = packageList;
                showLoading(false);
                populatePackages(packageList);

            }

            @Override
            public void onError(String error) {
                showLoading(false);
                Toast.makeText(SubscriptionActivity.this,
                        "Error loading packages: " + error,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    // ============================================
    // UPDATE UI
    // ============================================

    private void updateBalanceDisplay(UserBalance balance) {
        // Update the "What's Included" section to show current balance
        binding.tvFreeMinutes.setText(String.format(Locale.US,"%.1f", balance.getFreeMinutesBalance()) );
        binding.tvTotalMinutes.setText(String.format(Locale.US,"%.1f", balance.getMinutesBalance()) );
        binding.tvPaidMinutes.setText(String.format(Locale.US,"%.1f", balance.getPaidMinutesBalance()) );
        binding.tvWhatsIncluded.setText(
                String.format(Locale.US,
                        "Current Balance: %.1f minutes | Plan: %s",
                        balance.getMinutesBalance(),
                        balance.getAppVersion().toUpperCase())
        );
    }

    private void populatePackages(List<com.teletalker.app.network.Package> packageList) {
        // Clear existing radio buttons
        binding.subscriptionGroup.removeAllViews();

        if (packageList.isEmpty()) {
            Toast.makeText(this, "No packages available", Toast.LENGTH_SHORT).show();
            return;
        }

        // Add radio buttons for each package
        for (int i = 0; i < packageList.size(); i++) {
            com.teletalker.app.network.Package pkg = packageList.get(i);
            RadioButton radioButton = createPackageRadioButton(pkg, i == 0);
            binding.subscriptionGroup.addView(radioButton);
        }

        // Select first package by default
        if (binding.subscriptionGroup.getChildCount() > 0) {
            RadioButton firstButton = (RadioButton) binding.subscriptionGroup.getChildAt(0);
            firstButton.setChecked(true);
        }
    }

    private RadioButton createPackageRadioButton(com.teletalker.app.network.Package pkg, boolean isFeatured) {
        RadioButton radioButton = new RadioButton(this);
        radioButton.setId(View.generateViewId());
        radioButton.setTag(pkg.getId());

        // Build display text
        StringBuilder displayText = new StringBuilder();
        displayText.append(pkg.getName());
        displayText.append(" - ");
        displayText.append(String.format(Locale.US, "$%.2f", pkg.getFinalAmount()));
        displayText.append(String.format(Locale.US, " (%.1f minutes)", pkg.getMinutes()));

        // Show discount if applicable
        if (pkg.getDiscountPercent() > 0) {
            displayText.append(String.format(Locale.US,
                    " - SAVE %.0f%%", pkg.getDiscountPercent()));
        }

        radioButton.setText(displayText.toString());
        radioButton.setTextColor(getResources().getColor(android.R.color.white));
        radioButton.setTextSize(16);
        radioButton.setPadding(16, 16, 16, 16);

        // Highlight featured package
        if (isFeatured || pkg.isFeatured()) {
            radioButton.setTextColor(getResources().getColor(R.color.purple_700));
            radioButton.setTypeface(null, android.graphics.Typeface.BOLD);
        }

        return radioButton;
    }

    private void updateSubscribeButton() {
        if (selectedPackageId == null) {
            binding.subscribeButton.setText("Select a package");
            binding.subscribeButton.setEnabled(false);
            return;
        }

        // Find selected package
        Package selectedPackage = null;
        for (com.teletalker.app.network.Package pkg : packages) {
            if (pkg.getId().equals(selectedPackageId)) {
                selectedPackage = pkg;
                break;
            }
        }

        if (selectedPackage != null) {
            binding.subscribeButton.setText(
                    String.format(Locale.US, "Top Up - $%.2f",
                            selectedPackage.getFinalAmount())
            );
            binding.subscribeButton.setEnabled(true);

            // Update trial info to show package details
            binding.tvTrialInfo.setText(
                    String.format(Locale.US,
                            "Add %.1f minutes to your account\nPay once, no subscription",
                            selectedPackage.getMinutes())
            );
        }
    }

    // ============================================
    // CREATE INVOICE
    // ============================================

    private void createTopUpInvoice() {
        if (selectedPackageId == null) {
            Toast.makeText(this, "Please select a package", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);
        binding.subscribeButton.setEnabled(false);

        functionsManager.createTopUpInvoice(
                selectedPackageId,
                null, // no custom amount
                null, // no promo code (you can add input field for this)
                new FirebaseFunctionsManager.OnInvoiceCallback() {

                    @Override
                    public void onSuccess(InvoiceResponse invoice) {
                        showLoading(false);
//                        testPaymentWebhook();
                        openPaymentUrl(invoice.getInvoiceUrl());


                    }

                    @Override
                    public void onError(String error) {
                        showLoading(false);
                        binding.subscribeButton.setEnabled(true);
                        Toast.makeText(SubscriptionActivity.this,
                                "Error creating invoice: " + error,
                                Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void testPaymentWebhook() {
        FirebaseFunctions.getInstance()
                .getHttpsCallable("testPaymentComplete")
                .call(null)
                .addOnSuccessListener(result -> {
                    Map<String, Object> data = (Map<String, Object>) result.getData();

                    double oldBalance = ((Number) data.get("oldBalance")).doubleValue();
                    double newBalance = ((Number) data.get("newBalance")).doubleValue();
                    double minutesAdded = ((Number) data.get("minutesAdded")).doubleValue();

                    Log.d("TEST", "Success!");
                    Log.d("TEST", "Old balance: " + oldBalance);
                    Log.d("TEST", "New balance: " + newBalance);
                    Log.d("TEST", "Minutes added: " + minutesAdded);

//                    Toast.makeText(this, "Balance updated: " + newBalance, Toast.LENGTH_LONG).show();

                    // Refresh your balance UI here
                    loadBalance();
                })
                .addOnFailureListener(e -> {
                    Log.e("TEST", "Failed: " + e.getMessage(), e);
//                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void openPaymentUrl(String url) {
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browserIntent);

            Toast.makeText(this,
                    "Opening payment page...",
                    Toast.LENGTH_SHORT).show();

            // Close activity after opening payment
            finish();
        } catch (Exception e) {
            Toast.makeText(this,
                    "Error opening payment page: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    // ============================================
    // UI HELPERS
    // ============================================

    private void showLoading(boolean loading) {
        if (loading) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.scrollView.setVisibility(View.GONE);
        } else {
            binding.progressBar.setVisibility(View.GONE);
            binding.scrollView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh balance when returning from payment
        if (currentBalance != null) {
            loadBalance();
        }
    }
}