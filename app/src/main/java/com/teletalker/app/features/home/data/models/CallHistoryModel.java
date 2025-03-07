package com.teletalker.app.features.home.data.models;

public class CallHistoryModel {
    private final long id;
    private final String contactName;
    private final int profileImage;
    private final String language;
    private final String phoneNumber;
    private final int callType;
    private final long callDuration;
    private final String timestamp;

    public CallHistoryModel(
            long id,
            String contactName,
            String phoneNumber,
            int profileImage,
            String language,
            int callType,
            long callDuration,
            String timestamp
    ) {
        this.id = id;
        this.contactName = contactName;
        this.phoneNumber = phoneNumber;
        this.profileImage = profileImage;
        this.language = language;
        this.callType = callType;
        this.callDuration = callDuration;
        this.timestamp = timestamp;
    }

    public long getId() {
        return id;
    }

    public String getContactName() {
        return contactName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public int getCallType() {
        return callType;
    }

    public long getCallDuration() {
        return callDuration;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public int getProfileImage() {
        return profileImage;
    }

    public String getLanguage() {
        return language;
    }



}
