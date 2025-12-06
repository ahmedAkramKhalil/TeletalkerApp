package com.teletalker.app.subscription;

import com.google.gson.annotations.SerializedName;

public class PaddleProductResponse {

    @SerializedName("data")
    private PaddleProductData data;

    public PaddleProductData getData() {
        return data;
    }

    public static class PaddleProductData {
        @SerializedName("id")
        private String id;

        @SerializedName("name")
        private String name;

        @SerializedName("description")
        private String description;

        @SerializedName("type")
        private String type; // "standard", "custom"

        @SerializedName("tax_category")
        private String taxCategory;

        @SerializedName("image_url")
        private String imageUrl;

        @SerializedName("custom_data")
        private Object customData;

        @SerializedName("status")
        private String status; // "active", "archived"

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

        public String getImageUrl() {
            return imageUrl;
        }

        public String getStatus() {
            return status;
        }
    }
}