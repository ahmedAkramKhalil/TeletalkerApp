package com.teletalker.app.subscription;

import com.google.gson.annotations.SerializedName;


import com.google.gson.annotations.SerializedName;

public class PaddlePriceData {

    @SerializedName("id")
    private String id;

    @SerializedName("product_id")
    private String productId;

    @SerializedName("description")
    private String description;

    @SerializedName("type")
    private String type;

    @SerializedName("billing_cycle")
    private BillingCycle billingCycle;

    @SerializedName("trial_period")
    private TrialPeriod trialPeriod;

    @SerializedName("unit_price")
    private UnitPrice unitPrice;

    @SerializedName("unit_price_overrides")
    private Object unitPriceOverrides;

    @SerializedName("quantity")
    private Quantity quantity;

    @SerializedName("status")
    private String status;

    @SerializedName("custom_data")
    private Object customData;

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

    public String getType() {
        return type;
    }

    public BillingCycle getBillingCycle() {
        return billingCycle;
    }

    public TrialPeriod getTrialPeriod() {
        return trialPeriod;
    }

    public UnitPrice getUnitPrice() {
        return unitPrice;
    }

    public String getStatus() {
        return status;
    }

    /**
     * Billing Cycle information
     */
    public static class BillingCycle {

        @SerializedName("interval")
        private String interval; // "day", "week", "month", "year"

        @SerializedName("frequency")
        private int frequency;

        public String getInterval() {
            return interval;
        }

        public int getFrequency() {
            return frequency;
        }
    }

    /**
     * Trial Period information
     */
    public static class TrialPeriod {

        @SerializedName("interval")
        private String interval;

        @SerializedName("frequency")
        private int frequency;

        public String getInterval() {
            return interval;
        }

        public int getFrequency() {
            return frequency;
        }

        /**
         * Convert trial period to days
         */
        public int getDays() {
            if (interval == null) {
                return 0;
            }

            switch (interval.toLowerCase()) {
                case "day":
                    return frequency;
                case "week":
                    return frequency * 7;
                case "month":
                    return frequency * 30;
                case "year":
                    return frequency * 365;
                default:
                    return 0;
            }
        }
    }

    /**
     * Unit Price information
     */
    public static class UnitPrice {

        @SerializedName("amount")
        private String amount; // Amount in cents (e.g., "1000" = $10.00)

        @SerializedName("currency_code")
        private String currencyCode;

        public String getAmount() {
            return amount;
        }

        public String getCurrencyCode() {
            return currencyCode;
        }

        /**
         * Get formatted amount (converts cents to dollars)
         */
        public String getFormattedAmount() {
            try {
                double value = Double.parseDouble(amount);
                return String.format("%.2f", value / 100.0);
            } catch (NumberFormatException e) {
                return amount;
            }
        }
    }

    /**
     * Quantity information
     */
    public static class Quantity {

        @SerializedName("minimum")
        private int minimum;

        @SerializedName("maximum")
        private int maximum;

        public int getMinimum() {
            return minimum;
        }

        public int getMaximum() {
            return maximum;
        }
    }
}