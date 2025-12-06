package com.teletalker.app.subscription;


public class PaddleConfig {

    // ==========================================
    // PADDLE API CONFIGURATION
    // ==========================================

    // Your Paddle API Key (Sandbox)
    private static final String PADDLE_API_KEY = "pdl_sdbx_apikey_01k6573bmfgbne8bhntxp3rrkr_kGzNagwxnBp9gFRZqNFmj4_AWH";

    public static String getApiKey() {
        return PADDLE_API_KEY;
    }

    // Environment Configuration
    public static final boolean USE_SANDBOX = true;

    // API URLs
    public static final String API_URL = USE_SANDBOX
            ? "https://sandbox-api.paddle.com"
            : "https://api.paddle.com";

    public static final String CHECKOUT_URL = USE_SANDBOX
            ? "https://sandbox-buy.paddle.com"
            : "https://buy.paddle.com";

    // Product Configuration
    public static final String PRODUCT_TELETALKER_PREMIUM = "pro_01k62sx3qwf8qhjhvrmyds7v5m";

    // Price IDs (Will be populated from API response)
    public static class PriceIds {
        public static final String MONTHLY = "pri_01monthly";
        public static final String YEARLY = "pri_01yearly";
    }

    // Redirect URLs
    public static final String SUCCESS_URL = "teletalker://payment/success";
    public static final String CANCEL_URL = "teletalker://payment/cancel";
}