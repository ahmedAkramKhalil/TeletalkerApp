package com.teletalker.app.subscription;

import java.util.List;

public class Price {

    private String id;
    private String productId;
    private String description;
    private String unitPrice;
    private String currency;
    private String billingCycle;
    private int trialDays;
    private boolean popular;

    public Price(String id, String productId, String description, String unitPrice,
                 String currency, String billingCycle, int trialDays, boolean popular) {
        this.id = id;
        this.productId = productId;
        this.description = description;
        this.unitPrice = unitPrice;
        this.currency = currency;
        this.billingCycle = billingCycle;
        this.trialDays = trialDays;
        this.popular = popular;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public String getDescription() {
        return description;
    }

    public String getUnitPrice() {
        return unitPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public String getBillingCycle() {
        return billingCycle;
    }

    public int getTrialDays() {
        return trialDays;
    }

    public boolean isPopular() {
        return popular;
    }

    /**
     * Get formatted price with currency symbol
     */
    public String getFormattedPrice() {
        return getCurrencySymbol() + unitPrice;
    }

    /**
     * Get billing cycle text for display
     */
    public String getBillingCycleText() {
        if (billingCycle == null) {
            return "";
        }

        switch (billingCycle.toLowerCase()) {
            case "month":
                return "/month";
            case "year":
                return "/year";
            case "week":
                return "/week";
            case "day":
                return "/day";
            case "one_time":
                return " (one-time)";
            default:
                return "";
        }
    }

    /**
     * Get currency symbol
     */
    private String getCurrencySymbol() {
        if (currency == null) {
            return "$";
        }

        switch (currency.toUpperCase()) {
            case "USD":
                return "$";
            case "EUR":
                return "€";
            case "GBP":
                return "£";
            case "JPY":
            case "CNY":
                return "¥";
            case "INR":
                return "₹";
            default:
                return currency + " ";
        }
    }
}