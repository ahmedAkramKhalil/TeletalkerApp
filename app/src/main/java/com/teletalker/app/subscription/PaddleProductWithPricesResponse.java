package com.teletalker.app.subscription;

import com.google.gson.annotations.SerializedName;
import java.util.List;


import com.google.gson.annotations.SerializedName;
import java.util.List;

public class PaddleProductWithPricesResponse {

    @SerializedName("data")
    private ProductData data;

    @SerializedName("meta")
    private Meta meta;

    public ProductData getData() {
        return data;
    }

    public void setData(ProductData data) {
        this.data = data;
    }

    public Meta getMeta() {
        return meta;
    }

    public void setMeta(Meta meta) {
        this.meta = meta;
    }

    /**
     * Product data with included prices
     */
    public static class ProductData {

        @SerializedName("id")
        private String id;

        @SerializedName("name")
        private String name;

        @SerializedName("description")
        private String description;

        @SerializedName("type")
        private String type;

        @SerializedName("tax_category")
        private String taxCategory;

        @SerializedName("image_url")
        private String imageUrl;

        @SerializedName("custom_data")
        private Object customData;

        @SerializedName("status")
        private String status;

        @SerializedName("created_at")
        private String createdAt;

        @SerializedName("updated_at")
        private String updatedAt;

        @SerializedName("prices")
        private List<PaddlePriceData> prices;

        // Getters
        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getType() {
            return type;
        }

        public String getTaxCategory() {
            return taxCategory;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public Object getCustomData() {
            return customData;
        }

        public String getStatus() {
            return status;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public List<PaddlePriceData> getPrices() {
            return prices;
        }
    }

    /**
     * Meta information
     */
    public static class Meta {

        @SerializedName("request_id")
        private String requestId;

        public String getRequestId() {
            return requestId;
        }
    }
}