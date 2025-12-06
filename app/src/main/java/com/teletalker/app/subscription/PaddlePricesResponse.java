package com.teletalker.app.subscription;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class PaddlePricesResponse {

    @SerializedName("data")
    private List<PaddlePriceData> data;

    @SerializedName("meta")
    private Meta meta;

    // Getters
    public List<PaddlePriceData> getData() {
        return data;
    }

    public Meta getMeta() {
        return meta;
    }

    // Meta class for pagination
    public static class Meta {
        @SerializedName("request_id")
        private String requestId;

        @SerializedName("pagination")
        private Pagination pagination;

        public String getRequestId() {
            return requestId;
        }

        public Pagination getPagination() {
            return pagination;
        }
    }

    public static class Pagination {
        @SerializedName("per_page")
        private int perPage;

        @SerializedName("next")
        private String next;

        @SerializedName("has_more")
        private boolean hasMore;

        @SerializedName("estimated_total")
        private int estimatedTotal;

        // Getters
        public int getPerPage() { return perPage; }
        public String getNext() { return next; }
        public boolean isHasMore() { return hasMore; }
        public int getEstimatedTotal() { return estimatedTotal; }
    }
}