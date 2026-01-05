package com.teletalker.app.payment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.teletalker.app.features.home.HomeActivity;
import com.teletalker.app.utils.SubscriptionManager;

public class PaymentResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        Uri uri = intent.getData();

        if (uri == null) {
            goHome(false);
            return;
        }

        String path = uri.getPath();

        if ("/success".equals(path)) {
            Toast.makeText(this, "Payment successful!", Toast.LENGTH_SHORT).show();

            // Sync subscription (updates balance + plan + notifies listeners)
            SubscriptionManager.getInstance(this).sync(() -> goHome(true));
        } else {
            Toast.makeText(this, "Payment cancelled", Toast.LENGTH_SHORT).show();
            goHome(false);
        }
    }

    private void goHome(boolean success) {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("payment_success", success);
        startActivity(intent);
        finish();
    }
}