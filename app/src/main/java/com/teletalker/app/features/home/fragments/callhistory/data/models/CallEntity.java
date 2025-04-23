package com.teletalker.app.features.home.fragments.callhistory.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "calls")
public class CallEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;
    public String phoneNumber;
    public String callerName;
    public String callType;
    public String callStatus;
    public String duration;
    public String callTime;
    public String recordingFilePath;
    private boolean isCallRecordingPlay;
    private boolean isCallRecorded;

    public CallEntity(String phoneNumber, String callerName, String callType,String callStatus, String duration, String callTime, String recordingFilePath, boolean isCallRecorded) {
        this.phoneNumber = phoneNumber;
        this.callerName = callerName;
        this.callType = callType;
        this.callStatus = callStatus;
        this.duration = duration;
        this.callTime = callTime;
        this.recordingFilePath = recordingFilePath;
        this.isCallRecordingPlay = false;
        this.isCallRecorded = isCallRecorded;
    }
    public boolean isCallRecordingPlay() {
        return isCallRecordingPlay;
    }

    public void toggleCallRecordingPlay() {
        isCallRecordingPlay = !isCallRecordingPlay;
    }

    public void setCallRecordingPlay(boolean callRecordingPlay) {
        this.isCallRecordingPlay = callRecordingPlay;
    }

    public boolean isCallRecorded() {
        return isCallRecorded;
    }


}