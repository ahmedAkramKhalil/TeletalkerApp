package com.teletalker.app.network;

public class Package {
    private String id;
    private String name;
    private double amount;
    private double finalAmount;
    private double minutes;
    private double pricePerMinute;
    private double discountPercent;
    private double discountAmount;
    private boolean featured;
    private String description;

    public Package() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public double getFinalAmount() { return finalAmount; }
    public void setFinalAmount(double finalAmount) { this.finalAmount = finalAmount; }

    public double getMinutes() { return minutes; }
    public void setMinutes(double minutes) { this.minutes = minutes; }

    public double getPricePerMinute() { return pricePerMinute; }
    public void setPricePerMinute(double pricePerMinute) { this.pricePerMinute = pricePerMinute; }

    public double getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(double discountPercent) { this.discountPercent = discountPercent; }

    public double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(double discountAmount) { this.discountAmount = discountAmount; }

    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
