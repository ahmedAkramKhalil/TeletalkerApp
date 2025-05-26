package com.teletalker.app.features.subscription.presentation;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.teletalker.app.R;
import com.teletalker.app.databinding.ActivitySubscriptionBinding;

import java.util.Objects;

public class SubscriptionActivity extends AppCompatActivity {
    ActivitySubscriptionBinding binding;
    private SubscriptionViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivitySubscriptionBinding.inflate(getLayoutInflater());
        viewModel = new ViewModelProvider(this).get(SubscriptionViewModel.class);
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initListeners();
        selectSubscription(true);

        viewModel.events.observe(this, event -> {
            if (event instanceof SubscriptionEvents.PopBackStack) {
                finish();
            }
        });
    }

    private void selectSubscription(boolean isMonthly) {
        binding.monthlySubscription.setChecked(isMonthly);
        binding.yearlySubscription.setChecked(!isMonthly);
        /*bestValue.setVisibility(isMonthly ? View.GONE : View.VISIBLE);*/
    }

    void  initListeners() {
        binding.monthlySubscription.setOnClickListener(v -> selectSubscription(true));
        binding.yearlySubscription.setOnClickListener(v -> selectSubscription(false));
        binding.subscribeButton.setOnClickListener(v -> showCustomDialog());
        binding.arrowBack.setOnClickListener(t -> viewModel.popBackStack());

    }

    private void showCustomDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.custom_dialog);
        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        dialog.setCancelable(false);
        Button btnExplore = dialog.findViewById(R.id.btnExplore);
        btnExplore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }
}