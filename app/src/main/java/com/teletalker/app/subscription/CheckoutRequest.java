package com.teletalker.app.subscription;

import java.util.List;

public class CheckoutRequest {
    public List<Item> items;
    public Customer customer;
    public String return_url;

    public static class Item {
        public String price_id;
        public int quantity;

        public Item(String priceId, int quantity) {
            this.price_id = priceId;
            this.quantity = quantity;
        }
    }

    public static class Customer {
        public String email;

        public Customer(String email) {
            this.email = email;
        }
    }
}

