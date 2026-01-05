package com.teletalker.app.subscription;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.teletalker.app.R;
import com.teletalker.app.billing.BillingManager;

public class CheckoutActivity extends AppCompatActivity {

    private WebView webView;
    private static final String TAG = "Checkout";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        webView = findViewById(R.id.webView);
        setupWebView();

        String checkoutUrl = getIntent().getStringExtra("checkout_url");

        if (checkoutUrl == null || checkoutUrl.isEmpty()) {
            Toast.makeText(this, "Invalid checkout URL", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Log.d(TAG, "Loading: " + checkoutUrl);
        webView.loadUrl(checkoutUrl);
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                Log.d(TAG, "URL: " + url);

                // Handle deep links
                if (url.startsWith("teletalker://")) {
                    handleDeepLink(url);
                    return true;
                }

                // Allow NowPayments
                if (url.contains("nowpayments.io")) {
                    return false;
                }

                // Open external links in browser
                if (url.startsWith("https://")) {
                    return false;
                }

                return true;
            }
        });
    }

    private void handleDeepLink(String url) {
        Log.d(TAG, "Deep link: " + url);

        if (url.contains("/success")) {
            handleSuccess();
        } else if (url.contains("/cancel")) {
            handleCancel();
        }
    }

    private void handleSuccess() {
        Toast.makeText(this, "Payment successful!", Toast.LENGTH_SHORT).show();

        // Sync balance
        BillingManager.getInstance(this).syncBalance();

        setResult(RESULT_OK);
        finish();
    }

    private void handleCancel() {
        Toast.makeText(this, "Payment cancelled", Toast.LENGTH_SHORT).show();
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            handleCancel();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}