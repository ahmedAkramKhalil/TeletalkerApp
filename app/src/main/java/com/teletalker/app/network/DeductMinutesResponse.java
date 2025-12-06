package com.teletalker.app.network;

public class DeductMinutesResponse {
    private boolean success;
    private double newBalance;
    private double freeBalance;
    private double paidBalance;
    private double cost;
    private String callLogId;

    public DeductMinutesResponse() {}

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public double getNewBalance() { return newBalance; }
    public void setNewBalance(double newBalance) { this.newBalance = newBalance; }

    public double getFreeBalance() { return freeBalance; }
    public void setFreeBalance(double freeBalance) { this.freeBalance = freeBalance; }

    public double getPaidBalance() { return paidBalance; }
    public void setPaidBalance(double paidBalance) { this.paidBalance = paidBalance; }

    public double getCost() { return cost; }
    public void setCost(double cost) { this.cost = cost; }

    public String getCallLogId() { return callLogId; }
    public void setCallLogId(String callLogId) { this.callLogId = callLogId; }
}
