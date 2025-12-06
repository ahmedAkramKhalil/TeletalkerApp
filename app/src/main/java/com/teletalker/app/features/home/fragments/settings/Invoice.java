package com.teletalker.app.features.home.fragments.settings;

public class Invoice {
    String id;
    double amount;
    String currency;
    String status; // "paid", "upcoming", "cancelled"
    String type;
    String description;
    String dueDate;
    String createdAt;
    String paidAt;
}
