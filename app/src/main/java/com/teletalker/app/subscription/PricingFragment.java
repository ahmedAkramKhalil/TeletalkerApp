package com.teletalker.app.subscription;

import static android.app.Activity.RESULT_OK;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.teletalker.app.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class PricingFragment extends Fragment {

    private static final String TAG = "PricingFragment";

    // Views
    private ImageView arrowBack;
    private TextView tvProductName;
    private TextView tvProductDescription;
    private ProgressBar progressBar;
    private ScrollView scrollView;
    private RadioGroup subscriptionGroup;
    private Button subscribeButton;
    private TextView tvTrialInfo;
    private LinearLayout featuresContainer;

    // Data
    private Product currentProduct;
    private Price selectedPrice;
    private List<RadioButton> radioButtons = new ArrayList<>();
    private List<Price> allPrices = new ArrayList<>();

    // API
    private PaddleApiService apiService;
    private boolean isLoadingFromApi = false;

    // Activity Result Launcher
    private ActivityResultLauncher<Intent> checkoutLauncher;

    Retrofit retrofit;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Retrofit


        // Register checkout launcher
        checkoutLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        // Payment successful!
                        Intent data = result.getData();
                        if (data != null) {
                            String checkoutId = data.getStringExtra("checkout_id");
                            String transactionId = data.getStringExtra("transaction_id");

                            Toast.makeText(getContext(),
                                    "Payment successful! Checkout ID: " + checkoutId,
                                    Toast.LENGTH_LONG).show();

                            // Now verify the payment on your backend
//                            verifyPaymentOnBackend(checkoutId, transactionId);
                        }
                    } else {
                        // Payment cancelled
                        Toast.makeText(getContext(), "Payment cancelled", Toast.LENGTH_SHORT).show();
                    }
                }
        );

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_pricing, container, false);

        initViews(view);
        setupListeners();
        loadProductAndPricing();

        return view;
    }


    private static class AuthInterceptor implements Interceptor {
        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
            Request originalRequest = chain.request();

            // Add Authorization header
            Request authenticatedRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer " + "pdl_sdbx_apikey_01k6573bmfgbne8bhntxp3rrkr_kGzNagwxnBp9gFRZqNFmj4_AWH")
                    .build();

            return chain.proceed(authenticatedRequest);
        }
    }


    public Retrofit getClient() {
        if (retrofit == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(new AuthInterceptor())
                    .build();
            retrofit = new Retrofit.Builder()
                    .baseUrl(PaddleConfig.API_URL + "/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }


    private void initViews(View view) {
        arrowBack = view.findViewById(R.id.arrow_back);
        tvProductName = view.findViewById(R.id.tvProductName);
        tvProductDescription = view.findViewById(R.id.tvProductDescription);
        progressBar = view.findViewById(R.id.progressBar);
        scrollView = view.findViewById(R.id.scrollView);
        subscriptionGroup = view.findViewById(R.id.subscriptionGroup);
        subscribeButton = view.findViewById(R.id.subscribeButton);
        tvTrialInfo = view.findViewById(R.id.tvTrialInfo);
        featuresContainer = view.findViewById(R.id.featuresContainer);
    }

    private void setupListeners() {
        // Back button
        arrowBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });
        // Subscribe button
        subscribeButton.setOnClickListener(v -> createCheckout("email@gmail.com", selectedPrice.getId()));
        // RadioGroup selection
        subscriptionGroup.setOnCheckedChangeListener((group, checkedId) -> {
            onPriceSelected(checkedId);
        });
    }

    private void loadProductAndPricing() {
        // Show loading state
        showLoading(true);

        // Check network connectivity
        if (!isNetworkAvailable()) {
            Log.w(TAG, "No network connection, using offline pricing");
            showError("No internet connection", true);
            loadOfflinePricing();
            return;
        }

        // Fetch from API
        isLoadingFromApi = true;
        fetchPricingFromApi();
    }


    private void setupRetrofit() {
        Retrofit retrofit = PaddleApiClient.getClient();
        apiService = retrofit.create(PaddleApiService.class);
    }


    private void fetchPricingFromApi() {
        Log.d(TAG, "Fetching pricing from Paddle API...");

        Retrofit retrofit = getClient();
        apiService = retrofit.create(PaddleApiService.class);

        apiService.getProductWithPrices(PaddleConfig.PRODUCT_TELETALKER_PREMIUM,
                "prices").enqueue(new Callback<PaddleProductWithPricesResponse>() {
            @Override
            public void onResponse(Call<PaddleProductWithPricesResponse> call, Response<PaddleProductWithPricesResponse> response) {
                isLoadingFromApi = false;

                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "API Response successful");
                    handleApiSuccess(response.body());
                } else {
                    Log.e(TAG, "API Error: " + response.code() + " - " + response.message());
                    handleApiError("Failed to load pricing (Code: " + response.code() + ")");
                }

            }

            @Override
            public void onFailure(Call<PaddleProductWithPricesResponse> call, Throwable t) {
                isLoadingFromApi = false;
                Log.e(TAG, "API Failure: " + t.getMessage(), t);
                handleApiError("Network error: " + t.getMessage());

            }
        });
    }

    private void handleApiSuccess(PaddleProductWithPricesResponse response) {
        // Convert API response to Price models
        List<Price> prices = convertToPrices(response);

        if (prices.isEmpty()) {
            Log.w(TAG, "No prices returned from API, using offline data");
            showError("No pricing plans available", true);
            loadOfflinePricing();
            return;
        }

        Log.d(TAG, "Loaded " + prices.size() + " prices from API");

        // Sort prices: monthly first, then yearly, then others
        sortPrices(prices);

        // Store prices
        allPrices = prices;

        // Create product
        currentProduct = new Product(
                PaddleConfig.PRODUCT_TELETALKER_PREMIUM,
                "Teletalker",
                "Try Teletalker premium features",
                "",
                prices,
                "subscription"
        );

        // Display
        showLoading(false);
        displayProduct(currentProduct);
        displayFeatures();

        // Show success message
        if (getView() != null) {
            Snackbar.make(getView(), "Pricing loaded successfully", Snackbar.LENGTH_SHORT).show();
        }
    }

    private void handleApiError(String errorMessage) {
        Log.e(TAG, "Handling API error: " + errorMessage);
        showError(errorMessage, true);
        loadOfflinePricing();
    }

    private void loadOfflinePricing() {
        Log.d(TAG, "Loading offline pricing data");

        List<Price> prices = getMockPrices();
        allPrices = prices;

        currentProduct = new Product(
                PaddleConfig.PRODUCT_TELETALKER_PREMIUM,
                "Teletalker",
                "Try Teletalker premium features",
                "",
                prices,
                "subscription"
        );

        showLoading(false);
        displayProduct(currentProduct);
        displayFeatures();
    }

    /**
     * Convert Paddle API response to list of Price objects
     * @param response The PaddleProductWithPricesResponse from API
     * @return List of Price objects
     */
    private List<Price> convertToPrices(PaddleProductWithPricesResponse response) {
        List<Price> prices = new ArrayList<>();

        // Check if response has data
        if (response.getData() == null) {
            Log.w(TAG, "API response data is null");
            return prices;
        }

        // Get the list of prices from product data
        List<PaddlePriceData> paddlePrices = response.getData().getPrices();

        if (paddlePrices == null || paddlePrices.isEmpty()) {
            Log.w(TAG, "API returned empty prices list");
            return prices;
        }

        Log.d(TAG, "Converting " + paddlePrices.size() + " prices from API");

        for (PaddlePriceData paddlePrice : paddlePrices) {
            try {
                // Skip inactive prices
                if (!"active".equals(paddlePrice.getStatus())) {
                    Log.d(TAG, "Skipping inactive price: " + paddlePrice.getId());
                    continue;
                }

                // Get billing cycle
                String billingCycle = getBillingCycleFromApi(paddlePrice);

                // Get trial days
                int trialDays = getTrialDaysFromApi(paddlePrice);

                // Get price amount and currency
                String amount = "0";
                String currency = "USD";
                if (paddlePrice.getUnitPrice() != null) {
                    amount = paddlePrice.getUnitPrice().getFormattedAmount();
                    currency = paddlePrice.getUnitPrice().getCurrencyCode();
                }

                // Determine if popular (yearly plans are typically popular)
                boolean isPopular = "year".equals(billingCycle);

                // Create Price object
                Price price = new Price(
                        paddlePrice.getId(),
                        paddlePrice.getProductId(),
                        paddlePrice.getDescription() != null ?
                                paddlePrice.getDescription() : capitalizeFirst(billingCycle),
                        amount,
                        currency,
                        billingCycle,
                        trialDays,
                        isPopular
                );

                prices.add(price);
                Log.d(TAG, "Added price: " + price.getDescription() + " - " +
                        price.getFormattedPrice() + price.getBillingCycleText());

            } catch (Exception e) {
                Log.e(TAG, "Error converting price: " + paddlePrice.getId(), e);
            }
        }

        return prices;
    }
    private String getBillingCycleFromApi(PaddlePriceData paddlePrice) {
        if (paddlePrice.getBillingCycle() == null) {
            return "one_time";
        }

        String interval = paddlePrice.getBillingCycle().getInterval();
        int frequency = paddlePrice.getBillingCycle().getFrequency();

        if ("month".equals(interval) && frequency == 1) {
            return "month";
        } else if ("year".equals(interval) && frequency == 1) {
            return "year";
        } else if ("week".equals(interval)) {
            return "week";
        } else if ("day".equals(interval)) {
            return "day";
        } else {
            return interval;
        }
    }

    private int getTrialDaysFromApi(PaddlePriceData paddlePrice) {
        if (paddlePrice.getTrialPeriod() == null) {
            return 0;
        }

        return paddlePrice.getTrialPeriod().getDays();
    }

    private void sortPrices(List<Price> prices) {
        Collections.sort(prices, new Comparator<Price>() {
            @Override
            public int compare(Price p1, Price p2) {
                // Sort order: month, year, week, day, one_time
                return getPriorityOrder(p1.getBillingCycle()) -
                        getPriorityOrder(p2.getBillingCycle());
            }

            private int getPriorityOrder(String billingCycle) {
                switch (billingCycle) {
                    case "month":
                        return 1;
                    case "year":
                        return 2;
                    case "week":
                        return 3;
                    case "day":
                        return 4;
                    case "one_time":
                        return 5;
                    default:
                        return 6;
                }
            }
        });
    }

    private void displayProduct(Product product) {
        // Set product info
        tvProductName.setText(product.getName());
        tvProductDescription.setText(product.getDescription());

        // Clear existing radio buttons
        subscriptionGroup.removeAllViews();
        radioButtons.clear();

        // Add price options dynamically
        List<Price> prices = product.getPrices();

        if (prices.isEmpty()) {
            showError("No pricing plans available", false);
            return;
        }

        for (int i = 0; i < prices.size(); i++) {
            Price price = prices.get(i);
            addPriceOption(price, i == 0); // First option checked by default

            // Add divider between options (except last one)
            if (i < prices.size() - 1) {
                addDivider();
            }
        }

        // Auto-select first option
        selectedPrice = prices.get(0);
        subscribeButton.setEnabled(true);
        updateSubscribeButton();
    }

    private void displayFeatures() {
        featuresContainer.removeAllViews();

        String[] features = {
                "Unlimited voice & video calls",
                "HD quality calls",
                "No advertisements",
                "Priority customer support"
        };

        for (String feature : features) {
            addFeatureItem(feature);
        }
    }

    private void addFeatureItem(String featureText) {
        LinearLayout featureLayout = new LinearLayout(getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dpToPx(16));
        featureLayout.setLayoutParams(params);
        featureLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // Icon
        ImageView icon = new ImageView(getContext());
        icon.setImageResource(R.drawable.ic_flash);

        // Text
        TextView text = new TextView(getContext());
        text.setText(featureText);
        text.setTextColor(getResources().getColor(android.R.color.white));
        text.setTextSize(16);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        textParams.setMarginStart(dpToPx(32));
        text.setLayoutParams(textParams);

        featureLayout.addView(icon);
        featureLayout.addView(text);

        featuresContainer.addView(featureLayout);
    }

    private void addPriceOption(Price price, boolean isChecked) {
        // Create container for radio button and text
        LinearLayout container = new LinearLayout(getContext());
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(android.view.Gravity.CENTER_VERTICAL);
        container.setPadding(0, dpToPx(8), 0, dpToPx(8));

        // Create RadioButton
        RadioButton radioButton = new RadioButton(getContext());
        radioButton.setId(View.generateViewId());
        radioButton.setButtonDrawable(R.drawable.radio_button_selector);
        radioButton.setChecked(isChecked);
        radioButton.setTag(price); // Store price object in tag
        radioButton.setFocusable(false);
        radioButton.setFocusableInTouchMode(false);

        radioButtons.add(radioButton);

        // Create text container
        LinearLayout textContainer = new LinearLayout(getContext());
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        textContainer.setLayoutParams(textParams);
        textContainer.setOrientation(LinearLayout.VERTICAL);

        // Plan name
        TextView tvPlanName = new TextView(getContext());
        tvPlanName.setText(price.getDescription());
        tvPlanName.setTextColor(getResources().getColor(android.R.color.white));
        tvPlanName.setTextSize(16);
        tvPlanName.setTypeface(null, android.graphics.Typeface.BOLD);

        // Price text
        TextView tvPrice = new TextView(getContext());
        tvPrice.setText(price.getFormattedPrice() + price.getBillingCycleText());
        tvPrice.setTextColor(0xCCFFFFFF); // 80% white
        tvPrice.setTextSize(14);

        textContainer.addView(tvPlanName);
        textContainer.addView(tvPrice);

        container.addView(radioButton);
        container.addView(textContainer);

        // Add "BEST VALUE" badge for popular plans
        if (price.isPopular()) {
            MaterialCardView badge = createBestValueBadge();
            container.addView(badge);
        }

        // Make entire container clickable
        container.setOnClickListener(v -> {
            subscriptionGroup.check(radioButton.getId());
        });

        subscriptionGroup.addView(container);
    }

    private MaterialCardView createBestValueBadge() {
        MaterialCardView badge = new MaterialCardView(getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMarginStart(dpToPx(8));
        badge.setLayoutParams(params);
        badge.setCardBackgroundColor(getResources().getColor(android.R.color.white));
        badge.setRadius(dpToPx(8));
        badge.setCardElevation(0);

        TextView badgeText = new TextView(getContext());
        badgeText.setText("BEST VALUE");
        badgeText.setTextColor(getResources().getColor(android.R.color.black));
        badgeText.setTextSize(12);
        badgeText.setTypeface(null, android.graphics.Typeface.BOLD);
        badgeText.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));

        badge.addView(badgeText);
        return badge;
    }

    private void addDivider() {
        View divider = new View(getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(0.5f)
        );
        params.setMargins(0, dpToPx(8), 0, dpToPx(8));
        divider.setLayoutParams(params);
        divider.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));

        subscriptionGroup.addView(divider);
    }

    private void onPriceSelected(int checkedId) {
        // Find the selected price
        for (RadioButton rb : radioButtons) {
            if (rb.getId() == checkedId) {
                selectedPrice = (Price) rb.getTag();
                subscribeButton.setEnabled(true);
                updateSubscribeButton();

                Log.d(TAG, "Selected price: " + selectedPrice.getDescription() +
                        " (" + selectedPrice.getId() + ")");
                break;
            }
        }
    }

    private void updateSubscribeButton() {
        if (selectedPrice != null) {
            // Update button text
            if (selectedPrice.getTrialDays() > 0) {
                subscribeButton.setText("Start " + selectedPrice.getTrialDays() + "-Day Free Trial");
            } else {
                subscribeButton.setText("Subscribe for " + selectedPrice.getFormattedPrice());
            }

            // Update trial info text
            updateTrialInfo();
        }
    }

    private void updateTrialInfo() {
        if (selectedPrice == null) return;

        String trialText;
        if (selectedPrice.getTrialDays() > 0) {
            trialText = selectedPrice.getTrialDays() + "-day free trial, then " +
                    selectedPrice.getFormattedPrice() + selectedPrice.getBillingCycleText() +
                    ". Cancel anytime.";
        } else {
            trialText = selectedPrice.getFormattedPrice() + selectedPrice.getBillingCycleText() +
                    ". Cancel anytime.";
        }

        tvTrialInfo.setText(trialText);
    }

    private void proceedToCheckout(String Url) {
        if (selectedPrice == null) {
            Toast.makeText(getContext(), "Please select a plan", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Proceeding to checkout with price: " + selectedPrice.getId());
// https://sandbox-pay.paddle.io/hsc_01k67fjt6aep2kdda0tm0y6rk0_7k0hjqqqnna0rrjckx5cttceap9m0hwd


        Intent intent = new Intent(getActivity(), CheckoutActivity.class);
        intent.putExtra("checkout_url", Url);
//        intent.putExtra("email", getUserEmail());
//        intent.putExtra("product_id", currentProduct.getId());
//        intent.putExtra("price_id", selectedPrice.getId());

        checkoutLauncher.launch(intent);
    }


    public void createCheckout(String email, String priceId) {


        String checkoutBaseURL = "";

// 2. Parameters
        String appUserId = "user_123";
        String countryCode = "US";
        String postalCode = "10021";

// 3. Build complete URL with parameters
        String checkoutUrl = checkoutBaseURL +
                "?price_id=" + priceId +
                "&user_email=" + Uri.encode(email) +
                "&custom_data=" + Uri.encode(appUserId) +
                "&user_country=" + countryCode +
                "&user_postcode=" + Uri.encode(postalCode);

// 4. Launch checkout
        Intent intent = new Intent(getActivity(), CheckoutActivity.class);
        intent.putExtra("checkout_url", checkoutUrl);
        startActivity(intent);


//        PaddleApiService api =     getClient().create(PaddleApiService.class);

        Log.d("createCheckout"," " + priceId);
//        CheckoutRequest.Item item = new CheckoutRequest.Item(priceId, 1);
//        CheckoutRequest.Customer customer = new CheckoutRequest.Customer(email);
//
//        CheckoutRequest request = new CheckoutRequest();
//        request.items = java.util.Collections.singletonList(item);
//        request.customer = customer;
//        request.return_url = "teletalker://payment/success"; // your deep link
//
//        Call<CheckoutResponse> call = api.createCheckout(request);
//        call.enqueue(new Callback<CheckoutResponse>() {
//            @Override
//            public void onResponse(Call<CheckoutResponse> call, Response<CheckoutResponse> response) {
//                if (response.isSuccessful() && response.body() != null) {
//                    Log.d("onResponse"," " + priceId);
//                    String checkoutUrl = response.body().data.url;
//                    // 👉 Open in WebView / Chrome Custom Tab
//                    proceedToCheckout(checkoutUrl);
//                } else {
//                    Log.d("onResponse"," onResponse2" + priceId);
//                    Log.d("erro"," " + response.toString());
//                    // handle error
//                }
//            }
//
//            @Override
//            public void onFailure(Call<CheckoutResponse> call, Throwable t) {
//                Log.d("erro"," " + t.getMessage());
//                // handle failure
//            }
//        });
    }




    private void handlePaymentSuccess(Intent data) {
        String checkoutId = data != null ? data.getStringExtra("checkout_id") : null;

        Log.d(TAG, "Payment successful! Checkout ID: " + checkoutId);

        Toast.makeText(getContext(), "Premium activated!", Toast.LENGTH_LONG).show();

        // Save premium status
        SharedPreferences prefs = requireContext()
                .getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean("is_premium", true)
                .putString("checkout_id", checkoutId)
                .putLong("activated_at", System.currentTimeMillis())
                .apply();

        // Navigate back
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    // UI Helper Methods

    private void showLoading(boolean show) {
        if (show) {
            progressBar.setVisibility(View.VISIBLE);
            scrollView.setVisibility(View.GONE);
            subscribeButton.setEnabled(false);
        } else {
            progressBar.setVisibility(View.GONE);
            scrollView.setVisibility(View.VISIBLE);
        }
    }

    private void showError(String message, boolean canRetry) {
        if (getView() == null) return;

        Snackbar snackbar = Snackbar.make(getView(), message,
                canRetry ? Snackbar.LENGTH_INDEFINITE : Snackbar.LENGTH_LONG);

        if (canRetry) {
            snackbar.setAction("RETRY", v -> {
                loadProductAndPricing();
            });
        }

        snackbar.show();
    }

    private boolean isNetworkAvailable() {
        if (getContext() == null) return false;

        ConnectivityManager cm = (ConnectivityManager)
                getContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }

        return false;
    }

    // Mock data - Fallback when API fails
    private List<Price> getMockPrices() {
        List<Price> prices = new ArrayList<>();

        prices.add(new Price(
                "pri_mock_monthly",
                PaddleConfig.PRODUCT_TELETALKER_PREMIUM,
                "Monthly",
                "10.00",
                "USD",
                "month",
                3,
                false
        ));

        prices.add(new Price(
                "pri_mock_yearly",
                PaddleConfig.PRODUCT_TELETALKER_PREMIUM,
                "Yearly",
                "100.00",
                "USD",
                "year",
                3,
                true
        ));

        return prices;
    }

    // Helper Methods

    private String getUserId() {
        // Get from your auth system
        SharedPreferences prefs = requireContext()
                .getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        return prefs.getString("user_id", "user_" + System.currentTimeMillis());
    }

    private String getUserEmail() {
        // Get from your auth system
        SharedPreferences prefs = requireContext()
                .getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        return prefs.getString("user_email", "user@example.com");
    }

    private String capitalizeFirst(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    private int dpToPx(float dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Refresh if data is stale or failed to load
        if (allPrices.isEmpty() && !isLoadingFromApi) {
            loadProductAndPricing();
        }
    }
}