package com.teletalker.app.subscription;

import java.util.List;

// Product.java

import java.util.List;

public class Product {

    private String id;
    private String name;
    private String description;
    private String imageUrl;
    private List<Price> prices;
    private String type;

    public Product(String id, String name, String description, String imageUrl,
                   List<Price> prices, String type) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.prices = prices;
        this.type = type;
    }

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

    public String getImageUrl() {
        return imageUrl;
    }

    public List<Price> getPrices() {
        return prices;
    }

    public String getType() {
        return type;
    }

    public boolean isSubscription() {
        return "subscription".equalsIgnoreCase(type);
    }
}