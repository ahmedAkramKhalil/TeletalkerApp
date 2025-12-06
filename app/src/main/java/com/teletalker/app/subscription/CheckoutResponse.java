package com.teletalker.app.subscription;

public class CheckoutResponse {
    public Data data;

    public static class Data {
        public String id;
        public String url; // this is the checkout link
    }
}
