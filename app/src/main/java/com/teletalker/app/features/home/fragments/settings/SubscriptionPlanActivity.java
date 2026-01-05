package com.teletalker.app.features.home.fragments.settings;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.functions.FirebaseFunctions;
import com.teletalker.app.R;
import com.teletalker.app.databinding.ActivitySubscriptionPlanBinding;
import com.teletalker.app.network.AppVersion;
import com.teletalker.app.subscription.CheckoutActivity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class SubscriptionPlanActivity extends AppCompatActivity {

    private static final String TAG = "SubscriptionPlan";
    private ActivitySubscriptionPlanBinding binding;
    private FirebaseFunctions functions;

    private String currentPlan = "lite";
    private boolean isSubscribed = false;
    private double monthlyPrice = 10.0;
    private String nextBillingDate = null;

    private AppVersion litePlan;
    private AppVersion standardPlan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySubscriptionPlanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        functions = FirebaseFunctions.getInstance();

        setupListeners();
        loadPlanDetails();
    }

    private void setupListeners() {
        binding.arrowBack.setOnClickListener(v -> finish());

        binding.btnUpgrade.setOnClickListener(v -> {
            if (isSubscribed) {
                Toast.makeText(this, "Already subscribed!", Toast.LENGTH_SHORT).show();
            } else {
                showUpgradeDialog();
            }
        });

        binding.btnCancel.setOnClickListener(v -> showCancelDialog());
    }

    private void loadPlanDetails() {
        showLoading(true);

        functions.getHttpsCallable("getAppVersions")
                .call(null)
                .addOnSuccessListener(result -> {
                    showLoading(false);

                    try {
                        Map<String, Object> data = (Map<String, Object>) result.getData();

                        currentPlan = (String) data.get("currentPlan");
                        isSubscribed = (Boolean) data.get("isSubscribed");
                        monthlyPrice = ((Number) data.get("monthlyPrice")).doubleValue();
                        nextBillingDate = (String) data.get("nextBillingDate");

                        // Parse plans
                        Map<String, Object> plans = (Map<String, Object>) data.get("plans");
                        if (plans != null) {
                            litePlan = AppVersion.fromMap((Map<String, Object>) plans.get("lite"));
                            standardPlan = AppVersion.fromMap((Map<String, Object>) plans.get("standard"));
                        }

                        updateUI();

                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing data", e);
                        Toast.makeText(this, "Error loading plans", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error: " + e.getMessage());
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void updateUI() {
        if (isSubscribed && standardPlan != null) {
            // Standard Plan UI
            binding.tvBadge.setText("Standerd");
            binding.tvCurrentPlan.setText(standardPlan.getName());
            binding.tvPlanStatus.setText("All features unlocked");
            binding.subscriptionInfoLayout.setVisibility(View.VISIBLE);
            binding.tvMonthlyPrice.setText(String.format(Locale.US, "$%.2f", monthlyPrice));

            // Next billing date
            if (nextBillingDate != null) {
                binding.tvNextBilling.setText(formatDate(nextBillingDate));
            } else {
                binding.tvNextBilling.setText("--");
            }

            // Show standard features
            binding.tvFeaturesTitle.setText("Your Plan Features");
            updateFeaturesUI(standardPlan);

            binding.btnUpgrade.setText("✓ Subscribed");
            binding.btnUpgrade.setEnabled(false);
            binding.btnCancel.setVisibility(View.VISIBLE);
            binding.tvPlanInfo.setText("Your subscription renews automatically.");

        } else {
            // Lite Plan UI
            String liteName = litePlan != null ? litePlan.getName() : "Lite";
            String standardName = standardPlan != null ? standardPlan.getName() : "Standard";

            binding.tvBadge.setText("FREE");
            binding.tvCurrentPlan.setText(liteName);
            binding.tvPlanStatus.setText(getLiteLimitsText());
            binding.subscriptionInfoLayout.setVisibility(View.GONE);

            // Show standard features as upgrade benefits
            binding.tvFeaturesTitle.setText("Upgrade to " + standardName);
            if (standardPlan != null) {
                updateFeaturesUI(standardPlan);
            }

            binding.btnUpgrade.setText(String.format(Locale.US, "Upgrade - $%.0f/month", monthlyPrice));
            binding.btnUpgrade.setEnabled(true);
            binding.btnCancel.setVisibility(View.GONE);
            binding.tvPlanInfo.setText("Pay monthly. Cancel anytime.");
        }
    }

    private String getLiteLimitsText() {
        if (litePlan == null || litePlan.getFeatures() == null) {
            return "Limited features";
        }
        AppVersion.Features f = litePlan.getFeatures();
        return String.format(Locale.US, "Max %d calls/day • %d min/call",
                f.dailyCallLimit, f.maxCallDuration);
    }

    private void updateFeaturesUI(AppVersion plan) {
        // Clear existing feature views (except title)
        LinearLayout featuresContainer = binding.featuresCard.findViewById(R.id.featuresContainer);
        if (featuresContainer != null) {
            featuresContainer.removeAllViews();
        }

        if (plan == null || plan.getFeatures() == null) return;

        AppVersion.Features f = plan.getFeatures();

        // Add features dynamically - we'll just update the existing ones
        // The XML has hardcoded features, so we just show/hide based on the data
    }

    private void showUpgradeDialog() {
        String standardName = standardPlan != null ? standardPlan.getName() : "Standard";

        StringBuilder features = new StringBuilder();
        if (standardPlan != null && standardPlan.getFeatures() != null) {
            AppVersion.Features f = standardPlan.getFeatures();
            if (f.callRecording) features.append("✓ Unlimited call recording\n");
            if (f.advancedAI) features.append("✓ Advanced AI features\n");
            if (f.cloudStorage) features.append("✓ Cloud storage\n");
            features.append("✓ Priority support\n");
            features.append("✓ No ads");
        }

        new AlertDialog.Builder(this)
                .setTitle("Upgrade to " + standardName)
                .setMessage(String.format(Locale.US,
                        "Subscribe for $%.0f/month?\n\n%s", monthlyPrice, features.toString()))
                .setPositiveButton("Subscribe", (d, w) -> createSubscription())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createSubscription() {
        showLoading(true);

        functions.getHttpsCallable("createSubscriptionInvoice")
                .call(null)
                .addOnSuccessListener(result -> {
                    showLoading(false);

                    try {
                        Map<String, Object> data = (Map<String, Object>) result.getData();
                        String invoiceUrl = (String) data.get("invoiceUrl");

                        Intent intent = new Intent(this, CheckoutActivity.class);
                        intent.putExtra("checkout_url", invoiceUrl);
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(this, "Error creating invoice", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void showCancelDialog() {
        String standardName = standardPlan != null ? standardPlan.getName() : "Standard";

        new AlertDialog.Builder(this)
                .setTitle("Cancel Subscription?")
                .setMessage("You'll lose access to:\n\n" +
                        "• Unlimited call recording\n" +
                        "• Advanced AI features\n" +
                        "• Cloud storage\n" +
                        "• Priority support\n\n" +
                        "Downgrade to Lite plan?")
                .setPositiveButton("Yes, Cancel", (d, w) -> cancelSubscription())
                .setNegativeButton("Keep", null)
                .show();
    }

    private void cancelSubscription() {
        showLoading(true);

        functions.getHttpsCallable("cancelUserSubscription")
                .call(null)
                .addOnSuccessListener(result -> {
                    showLoading(false);
                    Toast.makeText(this, "Subscription cancelled", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private String formatDate(String isoDate) {
        if (isoDate == null) return "--";
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
            Date date = inputFormat.parse(isoDate);
            return outputFormat.format(date);
        } catch (Exception e) {
            return isoDate.length() > 10 ? isoDate.substring(0, 10) : isoDate;
        }
    }

    private void showLoading(boolean show) {
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.scrollView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPlanDetails();
    }
}