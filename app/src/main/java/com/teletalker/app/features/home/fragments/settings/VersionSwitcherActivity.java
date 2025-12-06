package com.teletalker.app.features.home.fragments.settings;


import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.teletalker.app.R;

import android.widget.TextView;
import android.widget.ImageView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class VersionSwitcherActivity extends AppCompatActivity {

    // Views
    private TextView tvVersionTitle;
    private TextView tvVersionDescription;
    private ImageView ivVersionBadge;
    private CardView cardSubscriptionInfo;
    private CardView cardFeatures;
    private TextView tvSubscriptionStatus;
    private TextView tvMonthlyPrice;
    private TextView tvNextBilling;
    private MaterialButton btnSwitchVersion;
    private MaterialButton btnViewInvoices;
    private MaterialButton btnCancelSubscription;
    private CircularProgressIndicator progressBar;
    private View contentLayout;

    // Firebase
    private FirebaseFunctions functions;
    private FirebaseAuth auth;
    private String currentPlan = "lite";
    private boolean isLoading = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_version_switcher);

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();

        functions = FirebaseFunctions.getInstance("us-central1");

        // Check if user is authenticated
        if (auth.getCurrentUser() == null) {
            showError("You must be logged in to manage subscription");
            finish();
            return;
        }

        // Initialize views
        initViews();

        // Setup toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("App Version");
        }

        // Load current subscription status
        loadSubscriptionStatus();
    }

    private void initViews() {
        tvVersionTitle = findViewById(R.id.tvVersionTitle);
        tvVersionDescription = findViewById(R.id.tvVersionDescription);
        ivVersionBadge = findViewById(R.id.ivVersionBadge);
        cardSubscriptionInfo = findViewById(R.id.cardSubscriptionInfo);
        cardFeatures = findViewById(R.id.cardFeatures);
        tvSubscriptionStatus = findViewById(R.id.tvSubscriptionStatus);
        tvMonthlyPrice = findViewById(R.id.tvMonthlyPrice);
        tvNextBilling = findViewById(R.id.tvNextBilling);
        btnSwitchVersion = findViewById(R.id.btnSwitchVersion);
        btnViewInvoices = findViewById(R.id.btnViewInvoices);
        btnCancelSubscription = findViewById(R.id.btnCancelSubscription);
        progressBar = findViewById(R.id.progressBar);
        contentLayout = findViewById(R.id.contentLayout);

        // Set click listeners
        btnSwitchVersion.setOnClickListener(v -> handleVersionSwitch());
        btnViewInvoices.setOnClickListener(v -> openInvoicesActivity());
        btnCancelSubscription.setOnClickListener(v -> showCancelConfirmation());
    }

    private void loadSubscriptionStatus() {
        // Check authentication first
        if (!isUserAuthenticated()) {
            showError("Please login to view subscription");
            finish();
            return;
        }

        showLoading(true);

        functions.getHttpsCallable("getSubscriptionStatus")
                .call()
                .addOnSuccessListener(result -> {
                    try {
                        Map<String, Object> data = (Map<String, Object>) result.getData();
                        boolean hasSubscription = (boolean) data.get("hasSubscription");

                        if (hasSubscription) {
                            currentPlan = (String) data.get("plan");
                            String status = (String) data.get("status");
                            Integer monthlyPrice = (Integer) data.get("monthlyPrice");
                            String nextBillingDate = (String) data.get("nextBillingDate");

                            updateUIForStandardPlan(status, monthlyPrice, nextBillingDate);
                        } else {
                            updateUIForLitePlan();
                        }

                        showLoading(false);
                    } catch (Exception e) {
                        Log.d(" parsing subscription "," parsing subscription " + e.getMessage());
                        showError("Error parsing subscription data");
                        showLoading(false);
                    }
                })
                .addOnFailureListener(e -> {
                    showError("Error loading subscription: " + e.getMessage());
                    showLoading(false);
                    // Default to lite on error
                    updateUIForLitePlan();
                });
    }

    private void handleVersionSwitch() {
        if (isLoading) return;

        if ("lite".equals(currentPlan)) {
            // Upgrade to Standard
            showUpgradeConfirmation();
        } else {
            // Already on Standard
            Toast.makeText(this,
                    "You're already on the Standard plan!\nUse 'Cancel Subscription' to downgrade.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showUpgradeConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Upgrade to Standard")
                .setMessage("Subscribe to Standard plan for $10/month?\n\n" +
                        "✓ Unlimited call recording\n" +
                        "✓ Advanced AI features\n" +
                        "✓ Cloud backup\n" +
                        "✓ Priority support\n" +
                        "✓ No ads\n\n" +
                        "You'll be billed $10 every 30 days.")
                .setPositiveButton("Subscribe Now", (dialog, which) -> upgradeToStandard())
                .setNegativeButton("Maybe Later", null)
                .setCancelable(true)
                .show();
    }

    private void upgradeToStandard() {
        // Check authentication first
        if (!isUserAuthenticated()) {
            showError("Please login to upgrade");
            return;
        }

        showLoading(true);

        functions.getHttpsCallable("createSubscription")
                .call()
                .addOnSuccessListener(result -> {
                    try {
                        Map<String, Object> data = (Map<String, Object>) result.getData();
                        boolean success = (boolean) data.get("success");

                        if (success) {
                            Toast.makeText(this,
                                    "🎉 Successfully upgraded to Standard!",
                                    Toast.LENGTH_LONG).show();

                            // Reload subscription status
                            loadSubscriptionStatus();
                        } else {
                            showError("Upgrade failed. Please try again.");
                            showLoading(false);
                        }
                    } catch (Exception e) {
                        showError("Error processing upgrade");
                        showLoading(false);
                    }
                })
                .addOnFailureListener(e -> {
                    showError("Error upgrading: " + e.getMessage());
                    showLoading(false);
                });
    }

    private void showCancelConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Cancel Subscription?")
                .setMessage("Are you sure you want to cancel your Standard subscription?\n\n" +
                        "❌ You'll lose access to:\n" +
                        "• Unlimited call recording\n" +
                        "• Advanced AI features\n" +
                        "• Cloud backup\n" +
                        "• Priority support\n\n" +
                        "You'll be downgraded to the Lite (free) plan.")
                .setPositiveButton("Yes, Cancel", (dialog, which) -> cancelSubscription())
                .setNegativeButton("Keep Subscription", null)
                .setCancelable(true)
                .show();
    }

    private void cancelSubscription() {
        // Check authentication first
        if (!isUserAuthenticated()) {
            showError("Please login to cancel subscription");
            return;
        }

        showLoading(true);

        functions.getHttpsCallable("cancelSubscription")
                .call()
                .addOnSuccessListener(result -> {
                    Toast.makeText(this,
                            "Subscription cancelled successfully",
                            Toast.LENGTH_SHORT).show();

                    // Reload subscription status
                    loadSubscriptionStatus();
                })
                .addOnFailureListener(e -> {
                    showError("Error cancelling: " + e.getMessage());
                    showLoading(false);
                });
    }

    private void updateUIForLitePlan() {
        currentPlan = "lite";

        // Update header
        tvVersionTitle.setText("Lite Plan");
        tvVersionDescription.setText("Free • Limited Features");
        ivVersionBadge.setImageResource(R.drawable.ic_lite_badge);

        // Hide subscription info
        cardSubscriptionInfo.setVisibility(View.GONE);

        // Show features card
        cardFeatures.setVisibility(View.VISIBLE);

        // Update button
        btnSwitchVersion.setText("Upgrade to Standard");
        btnSwitchVersion.setEnabled(true);
        btnSwitchVersion.setVisibility(View.VISIBLE);

        // Hide cancel button
        btnCancelSubscription.setVisibility(View.GONE);

        // Show invoices button
        btnViewInvoices.setVisibility(View.VISIBLE);
    }

    private void updateUIForStandardPlan(String status, Integer monthlyPrice, String nextBillingDate) {
        currentPlan = "standard";

        // Update header
        tvVersionTitle.setText("Standard Plan");
        tvVersionDescription.setText("Premium • All Features Unlocked");
        ivVersionBadge.setImageResource(R.drawable.ic_standard_badge);

        // Show subscription info
        cardSubscriptionInfo.setVisibility(View.VISIBLE);
        tvSubscriptionStatus.setText("Status: " + status.toUpperCase());
        tvMonthlyPrice.setText(String.format(Locale.US, "$%.2f/month", monthlyPrice));

        if (nextBillingDate != null && !nextBillingDate.isEmpty()) {
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                Date date = inputFormat.parse(nextBillingDate);
                tvNextBilling.setText("Next billing: " + outputFormat.format(date));
            } catch (Exception e) {
                tvNextBilling.setText("Next billing: " + nextBillingDate.substring(0, 10));
            }
        }

        // Hide features card
        cardFeatures.setVisibility(View.GONE);

        // Update button
        btnSwitchVersion.setText("✓ Standard (Active)");
        btnSwitchVersion.setEnabled(false);
        btnSwitchVersion.setVisibility(View.VISIBLE);

        // Show cancel button
        btnCancelSubscription.setVisibility(View.VISIBLE);

        // Show invoices button
        btnViewInvoices.setVisibility(View.VISIBLE);
    }

    private void openInvoicesActivity() {
        Intent intent = new Intent(this, InvoicesActivity.class);
        startActivity(intent);
    }

    private void showLoading(boolean show) {
        isLoading = show;
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        contentLayout.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private boolean isUserAuthenticated() {
        return auth != null && auth.getCurrentUser() != null;
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check authentication when returning
        if (!isUserAuthenticated()) {
            showError("Session expired. Please login again.");
            finish();
            return;
        }
        // Refresh status when returning to this activity
        loadSubscriptionStatus();
    }
}