package com.teletalker.app.network;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FIXED: Manager class for all Firebase Cloud Functions
 * NOW includes auth token refresh before each call
 */
public class FirebaseFunctionsManager {
    private static final String TAG = "FirebaseFunctionsManager";

    private static FirebaseFunctionsManager instance;
    private final FirebaseFunctions functions;
    private final FirebaseAuth auth;
    private final Gson gson;

    private FirebaseFunctionsManager() {
        functions = FirebaseFunctions.getInstance();
        auth = FirebaseAuth.getInstance();
        gson = new Gson();
    }

    public static synchronized FirebaseFunctionsManager getInstance() {
        if (instance == null) {
            instance = new FirebaseFunctionsManager();
        }
        return instance;
    }

    // ============================================
    // CALLBACK INTERFACES
    // ============================================

    public interface OnBalanceCallback {
        void onSuccess(UserBalance balance);
        void onError(String error);
    }

    public interface OnPackagesCallback {
        void onSuccess(List<Package> packages);
        void onError(String error);
    }

    public interface OnInvoiceCallback {
        void onSuccess(InvoiceResponse invoice);
        void onError(String error);
    }

    public interface OnDeductMinutesCallback {
        void onSuccess(DeductMinutesResponse response);
        void onError(String error);
    }

    public interface OnPromoCodeCallback {
        void onSuccess(PromoCodeValidation validation);
        void onError(String error);
    }

    public interface OnUpgradeCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public interface OnTokenUpdateCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface OnInitializeUserCallback {
        void onSuccess(double freeMinutes);
        void onError(String error);
    }

    // ============================================
    // HELPER: Ensure Fresh Auth Token
    // ============================================

    /**
     * CRITICAL: This ensures auth token is fresh before calling Firebase Functions
     * This fixes the "unauthenticated" error
     */
    private void ensureFreshToken(Runnable onSuccess, OnErrorCallback onError) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            Log.e(TAG, "❌ User not authenticated");
            onError.onError("User not authenticated");
            return;
        }

        // Force refresh the token to ensure it's valid
        user.getIdToken(true).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String token = task.getResult().getToken();
                if (token != null) {
                    Log.d(TAG, "✅ Auth token refreshed: " + token.substring(0, 20) + "...");
                    onSuccess.run();
                } else {
                    Log.e(TAG, "❌ Auth token is null");
                    onError.onError("Auth token is null");
                }
            } else {
                Log.e(TAG, "❌ Failed to refresh auth token: " + task.getException());
                onError.onError("Failed to refresh auth token");
            }
        });
    }

    private interface OnErrorCallback {
        void onError(String error);
    }

    // ============================================
    // 1. GET BALANCE (FIXED)
    // ============================================

    public void getBalance(OnBalanceCallback callback) {
        Log.d(TAG, "🔄 Getting balance...");

        ensureFreshToken(
                () -> {
                    // Token is fresh, now make the call
                    functions.getHttpsCallable("getbalance")
                            .call()
                            .addOnSuccessListener(result -> {
                                try {
                                    Map<String, Object> data = (Map<String, Object>) result.getData();
                                    UserBalance balance = parseBalance(data);

                                    Log.d(TAG, "✅ Balance retrieved: " + balance.getMinutesBalance());
                                    callback.onSuccess(balance);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error parsing balance", e);
                                    callback.onError("Error parsing balance: " + e.getMessage());
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error getting balance", e);
                                callback.onError(e.getMessage());
                            });
                },
                callback::onError
        );
    }

    // ============================================
    // 2. GET PACKAGES (FIXED)
    // ============================================

    public void getPackages(String promoCode, OnPackagesCallback callback) {
        ensureFreshToken(
                () -> {
                    Map<String, Object> data = new HashMap<>();
                    if (promoCode != null && !promoCode.isEmpty()) {
                        data.put("promoCode", promoCode);
                    }

                    functions.getHttpsCallable("getpackages")
                            .call(data)
                            .addOnSuccessListener(result -> {
                                try {
                                    Map<String, Object> resultData = (Map<String, Object>) result.getData();
                                    List<Map<String, Object>> packagesData = (List<Map<String, Object>>) resultData.get("packages");
                                    List<Package> packages = new ArrayList<>();

                                    for (Map<String, Object> pkgData : packagesData) {
                                        Package pkg = parsePackage(pkgData);
                                        packages.add(pkg);
                                    }

                                    callback.onSuccess(packages);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error parsing packages", e);
                                    callback.onError("Error parsing packages: " + e.getMessage());
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error getting packages", e);
                                callback.onError(e.getMessage());
                            });
                },
                callback::onError
        );
    }

    // ============================================
    // 3. CREATE TOP-UP INVOICE (FIXED)
    // ============================================

    public void createTopUpInvoice(String packageId, Double customAmount, String promoCode,
                                   OnInvoiceCallback callback) {
//        ensureFreshToken(
//
//                () -> {

        FirebaseUser user = auth.getCurrentUser();
        user.getIdToken(true).addOnCompleteListener(task -> {
            // NOW call the function

        Map<String, Object> data = new HashMap<>();

                    if (packageId != null && !packageId.isEmpty()) {
                        data.put("packageId", packageId);
                    } else if (customAmount != null && customAmount > 0) {
                        data.put("customAmount", customAmount);
                    } else {
                        callback.onError("Package ID or custom amount required");
                        return;
                    }

                    if (promoCode != null && !promoCode.isEmpty()) {
                        data.put("promoCode", promoCode);
                    }

                    functions.getHttpsCallable("createtopupinvoice")
                            .call(data)
                            .addOnSuccessListener(result -> {
                                try {
                                    Map<String, Object> resultData = (Map<String, Object>) result.getData();
                                    InvoiceResponse invoice = parseInvoice(resultData);
                                    callback.onSuccess(invoice);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error parsing invoice", e);
                                    callback.onError("Error parsing invoice: " + e.getMessage());
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error creating invoice", e);
                                callback.onError(e.getMessage());
                            });
        });

//                },
//                callback::onError
//        );
    }

    // ============================================
    // 4. DEDUCT MINUTES - FIXED WITH AUTH TOKEN
    // ============================================

    public void deductMinutes(double duration, String phoneNumber, String recordingUrl,
                              OnDeductMinutesCallback callback) {




        Log.d(TAG, "========================================");
        Log.d(TAG, "💰 DEDUCT MINUTES REQUEST");
        Log.d(TAG, "Duration: " + duration + " minutes");
        Log.d(TAG, "Phone: " + phoneNumber);
        Log.d(TAG, "========================================");

        ensureFreshToken(
                () -> {
                    // Token is fresh, now make the call
                    Map<String, Object> data = new HashMap<>();
                    data.put("duration", duration);
                    data.put("phoneNumber", phoneNumber);
                    if (recordingUrl != null) {
                        data.put("recordingUrl", recordingUrl);
                    }

                    Log.d(TAG, "Calling deductminutes function...");

                    functions.getHttpsCallable("deductMinutesV2")
                            .call(data)
                            .addOnSuccessListener(result -> {
                                try {
                                    Map<String, Object> resultData = (Map<String, Object>) result.getData();
                                    DeductMinutesResponse response = parseDeductMinutes(resultData);

                                    Log.d(TAG, "✅ DEDUCT SUCCESS");
                                    Log.d(TAG, "   New Balance: " + response.getNewBalance());
                                    Log.d(TAG, "   Cost: " + response.getCost());
                                    Log.d(TAG, "========================================");

                                    callback.onSuccess(response);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error parsing deduct response", e);
                                    callback.onError("Error parsing response: " + e.getMessage());
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "========================================");
                                Log.e(TAG, "❌ DEDUCT FAILED");
                                Log.e(TAG, "Error: " + e.getMessage());
                                Log.e(TAG, "========================================");
                                callback.onError(e.getMessage());
                            });
                },
                error -> {
                    Log.e(TAG, "========================================");
                    Log.e(TAG, "❌ AUTH TOKEN FAILED");
                    Log.e(TAG, "Error: " + error);
                    Log.e(TAG, "========================================");
                    callback.onError(error);
                }
        );
    }

    // ============================================
    // 5. VALIDATE PROMO CODE (FIXED)
    // ============================================

    public void validatePromoCode(String code, double amount, OnPromoCodeCallback callback) {
        ensureFreshToken(
                () -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("code", code);
                    data.put("amount", amount);

                    functions.getHttpsCallable("validatepromocode")
                            .call(data)
                            .addOnSuccessListener(result -> {
                                try {
                                    Map<String, Object> resultData = (Map<String, Object>) result.getData();
                                    PromoCodeValidation validation = parsePromoValidation(resultData);
                                    callback.onSuccess(validation);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error parsing promo validation", e);
                                    callback.onError("Error parsing validation: " + e.getMessage());
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error validating promo code", e);
                                callback.onError(e.getMessage());
                            });
                },
                callback::onError
        );
    }

    // ============================================
    // 6. UPGRADE USER VERSION (FIXED)
    // ============================================

    public void upgradeUserVersion(String newVersion, OnUpgradeCallback callback) {
        ensureFreshToken(
                () -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("newVersion", newVersion);

                    functions.getHttpsCallable("upgradeuserversion")
                            .call(data)
                            .addOnSuccessListener(result -> {
                                try {
                                    Map<String, Object> resultData = (Map<String, Object>) result.getData();
                                    String message = (String) resultData.get("message");
                                    callback.onSuccess(message);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error parsing upgrade response", e);
                                    callback.onError("Error parsing response: " + e.getMessage());
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error upgrading user", e);
                                callback.onError(e.getMessage());
                            });
                },
                callback::onError
        );
    }

    // ============================================
    // 7. UPDATE FCM TOKEN (FIXED)
    // ============================================

    public void updateFCMToken(String token, OnTokenUpdateCallback callback) {
        ensureFreshToken(
                () -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("token", token);

                    functions.getHttpsCallable("updatefcmtoken")
                            .call(data)
                            .addOnSuccessListener(result -> {
                                Log.d(TAG, "FCM token updated successfully");
                                callback.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error updating FCM token", e);
                                callback.onError(e.getMessage());
                            });
                },
                callback::onError
        );
    }

    // ============================================
    // 8. INITIALIZE NEW USER (FIXED)
    // ============================================

    public void initializeNewUser(OnInitializeUserCallback callback) {
        ensureFreshToken(
                () -> {
                    functions.getHttpsCallable("initializeuser")
                            .call()
                            .addOnSuccessListener(result -> {
                                try {
                                    Map<String, Object> data = (Map<String, Object>) result.getData();
                                    double freeMinutes = getDouble(data, "freeMinutes");
                                    callback.onSuccess(freeMinutes);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error parsing response", e);
                                    callback.onError(e.getMessage());
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error initializing user", e);
                                callback.onError(e.getMessage());
                            });
                },
                callback::onError
        );
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private boolean isUserAuthenticated() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null;
    }

    private UserBalance parseBalance(Map<String, Object> data) {
        UserBalance balance = new UserBalance();
        balance.setMinutesBalance(getDouble(data, "minutesBalance"));
        balance.setFreeMinutesBalance(getDouble(data, "freeMinutesBalance"));
        balance.setPaidMinutesBalance(getDouble(data, "paidMinutesBalance"));
        balance.setTotalSpent(getDouble(data, "totalSpent"));
        balance.setTotalMinutesUsed(getDouble(data, "totalMinutesUsed"));
        balance.setAppVersion((String) data.get("appVersion"));
        return balance;
    }

    private Package parsePackage(Map<String, Object> data) {
        Package pkg = new Package();
        pkg.setId((String) data.get("id"));
        pkg.setName((String) data.get("name"));
        pkg.setAmount(getDouble(data, "amount"));
        pkg.setFinalAmount(getDouble(data, "finalAmount"));
        pkg.setMinutes(getDouble(data, "minutes"));
        pkg.setPricePerMinute(getDouble(data, "pricePerMinute"));
        pkg.setDiscountPercent(getDouble(data, "discountPercent"));
        pkg.setDiscountAmount(getDouble(data, "discountAmount"));
        pkg.setFeatured(getBoolean(data, "featured"));
        pkg.setDescription((String) data.get("description"));
        return pkg;
    }

    private InvoiceResponse parseInvoice(Map<String, Object> data) {
        InvoiceResponse invoice = new InvoiceResponse();
        invoice.setSuccess(getBoolean(data, "success"));
        invoice.setInvoiceId((String) data.get("invoiceId"));
        invoice.setInvoiceUrl((String) data.get("invoiceUrl"));
        invoice.setOriginalAmount(getDouble(data, "originalAmount"));
        invoice.setFinalAmount(getDouble(data, "finalAmount"));
        invoice.setDiscountAmount(getDouble(data, "discountAmount"));
        invoice.setMinutes(getDouble(data, "minutes"));
        return invoice;
    }

    private DeductMinutesResponse parseDeductMinutes(Map<String, Object> data) {
        DeductMinutesResponse response = new DeductMinutesResponse();
        response.setSuccess(getBoolean(data, "success"));
        response.setNewBalance(getDouble(data, "newBalance"));
        response.setFreeBalance(getDouble(data, "freeBalance"));
        response.setPaidBalance(getDouble(data, "paidBalance"));
        response.setCost(getDouble(data, "cost"));
        response.setCallLogId((String) data.get("callLogId"));
        return response;
    }

    private PromoCodeValidation parsePromoValidation(Map<String, Object> data) {
        PromoCodeValidation validation = new PromoCodeValidation();
        validation.setValid(getBoolean(data, "valid"));
        validation.setCode((String) data.get("code"));

        Object discountPercent = data.get("discountPercent");
        if (discountPercent != null) {
            validation.setDiscountPercent(getDouble(data, "discountPercent"));
        }

        Object discountAmount = data.get("discountAmount");
        if (discountAmount != null) {
            validation.setDiscountAmount(getDouble(data, "discountAmount"));
        }

        validation.setDescription((String) data.get("description"));
        validation.setMessage((String) data.get("message"));
        return validation;
    }

    private double getDouble(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    private boolean getBoolean(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return false;
    }
}