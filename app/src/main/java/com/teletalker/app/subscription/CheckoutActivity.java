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

/**
 * CheckoutActivity - Handles Paddle Hosted Checkout
 *
 * This activity only accepts a complete checkout URL.
 * Build your checkout URL with all parameters BEFORE launching this activity.
 *
 * Required Intent Extra:
 * - "checkout_url": Complete Paddle hosted checkout URL with all parameters
 *
 * Example:
 * String checkoutUrl = "https://pay.paddle.io/checkout/hsc_xxxxx?price_id=pri_xxxxx&user_email=user@example.com";
 * Intent intent = new Intent(this, CheckoutActivity.class);
 * intent.putExtra("checkout_url", checkoutUrl);
 * startActivity(intent);
 */
public class CheckoutActivity extends AppCompatActivity {

    private WebView webView;
    private static final String TAG = "PaddleCheckout";

    // Custom URL schemes for deep linking back to app
    private static final String SUCCESS_SCHEME = "teletalker://checkout/success";
    private static final String CANCEL_SCHEME = "teletalker://checkout/cancel";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        webView = findViewById(R.id.webView);
        setupWebView();

        // Get the complete checkout URL from intent
        String checkoutUrl = getIntent().getStringExtra("checkout_url");

        if (checkoutUrl == null || checkoutUrl.isEmpty()) {
            Log.e(TAG, "No checkout URL provided");
            Toast.makeText(this, "Invalid checkout URL", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Validate URL is a Paddle checkout URL
        if (!isValidPaddleUrl(checkoutUrl)) {
            Log.e(TAG, "Invalid Paddle URL: " + checkoutUrl);
            Toast.makeText(this, "Invalid Paddle checkout URL", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Log.d(TAG, "Loading checkout URL: " + checkoutUrl);
        webView.loadUrl(checkoutUrl);
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSupportMultipleWindows(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                Log.d(TAG, "URL Loading: " + url);

                // Handle success redirect
                if (url.startsWith(SUCCESS_SCHEME) ||
                        url.contains("checkout/success") ||
                        url.contains("checkout_id=")) {
                    handleSuccessUrl(url);
                    return true;
                }

                // Handle cancel redirect
                if (url.startsWith(CANCEL_SCHEME) ||
                        url.contains("checkout/cancel")) {
                    handleCancelUrl();
                    return true;
                }

                // Allow Paddle domains
                if (url.startsWith("https://pay.paddle.io") ||
                        url.startsWith("https://buy.paddle.com") ||
                        url.startsWith("https://checkout.paddle.com") ||
                        url.startsWith("https://sandbox-buy.paddle.com") ||
                        url.startsWith("https://sandbox-checkout.paddle.com")) {
                    return false;
                }

                // Allow payment provider redirects (Stripe, PayPal, etc)
                if (url.contains("stripe.com") ||
                        url.contains("paypal.com") ||
                        url.contains("checkout.")) {
                    return false;
                }

                // Block other external URLs
                Log.w(TAG, "Blocking external URL: " + url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Page finished: " + url);

                // Check if we've landed on success page by URL pattern
                if (url.contains("checkout_id=") ||
                        url.contains("/success") ||
                        url.contains("transaction_id=")) {
                    handleSuccessUrl(url);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                Log.e(TAG, "WebView error: " + error.getDescription());
            }
        });
    }

    private boolean isValidPaddleUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        // Check if URL is a valid Paddle checkout URL
        return url.startsWith("https://pay.paddle.io/checkout/") ||
                url.startsWith("https://buy.paddle.com") ||
                url.startsWith("https://checkout.paddle.com") ||
                url.startsWith("https://sandbox-buy.paddle.com");
    }

    private void handleSuccessUrl(String url) {
        Log.d(TAG, "Payment success detected: " + url);

        runOnUiThread(() -> {
            try {
                Uri uri = Uri.parse(url);

                // Extract payment information from URL parameters
                String checkoutId = uri.getQueryParameter("checkout_id");
                String transactionId = uri.getQueryParameter("transaction_id");
                String customData = uri.getQueryParameter("custom_data");

                // Create result intent
                Intent resultIntent = new Intent();
                if (checkoutId != null) {
                    resultIntent.putExtra("checkout_id", checkoutId);
                }
                if (transactionId != null) {
                    resultIntent.putExtra("transaction_id", transactionId);
                }
                if (customData != null) {
                    resultIntent.putExtra("user_id", customData);
                }
                resultIntent.putExtra("success_url", url);

                Toast.makeText(this, "Payment successful!", Toast.LENGTH_LONG).show();

                setResult(RESULT_OK, resultIntent);
                finish();

            } catch (Exception e) {
                Log.e(TAG, "Error parsing success URL", e);

                // Return success even if we couldn't parse everything
                Intent resultIntent = new Intent();
                resultIntent.putExtra("success_url", url);
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });
    }

    private void handleCancelUrl() {
        Log.d(TAG, "Payment cancelled");

        runOnUiThread(() -> {
            Toast.makeText(this, "Payment cancelled", Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            // User pressed back - treat as cancellation
            handleCancelUrl();
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