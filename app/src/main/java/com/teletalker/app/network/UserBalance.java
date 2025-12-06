package com.teletalker.app.network;

public class UserBalance {
    private double minutesBalance;
    private double freeMinutesBalance;
    private double paidMinutesBalance;
    private double totalSpent;
    private double totalMinutesUsed;
    private String appVersion;

    public UserBalance() {}

    // Getters and Setters
    public double getMinutesBalance() { return minutesBalance; }
    public void setMinutesBalance(double minutesBalance) { this.minutesBalance = minutesBalance; }

    public double getFreeMinutesBalance() { return freeMinutesBalance; }
    public void setFreeMinutesBalance(double freeMinutesBalance) { this.freeMinutesBalance = freeMinutesBalance; }

    public double getPaidMinutesBalance() { return paidMinutesBalance; }
    public void setPaidMinutesBalance(double paidMinutesBalance) { this.paidMinutesBalance = paidMinutesBalance; }

    public double getTotalSpent() { return totalSpent; }
    public void setTotalSpent(double totalSpent) { this.totalSpent = totalSpent; }

    public double getTotalMinutesUsed() { return totalMinutesUsed; }
    public void setTotalMinutesUsed(double totalMinutesUsed) { this.totalMinutesUsed = totalMinutesUsed; }

    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
}
