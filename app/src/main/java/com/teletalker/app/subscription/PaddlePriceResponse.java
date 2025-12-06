package com.teletalker.app.subscription;

import com.google.gson.annotations.SerializedName;

public class PaddlePriceResponse {

    @SerializedName("data")
    private PaddlePriceData data;

    @SerializedName("meta")
    private Meta meta;

    public PaddlePriceData getData() {
        return data;
    }

    public Meta getMeta() {
        return meta;
    }

    public static class Meta {
        @SerializedName("request_id")
        private String requestId;

        public String getRequestId() {
            return requestId;
        }
    }
}