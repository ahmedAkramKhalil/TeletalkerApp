package com.teletalker.app.features.home.fragments.calender.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity(tableName = "scheduled_calls")
public class ScheduledCall implements Serializable {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private String phoneNumber;
    private String contactName;
    private long scheduledDateTime; // Unix timestamp
    private int durationMinutes;

    // AI conversation fields
    private String purpose; // Brief purpose of the call (e.g., "Follow up on order")
    private String conversationNotes; // Detailed notes for AI about what to say

    // Status tracking
    private String status; // pending, in_progress, completed, failed, cancelled
    private long createdAt;
    private long updatedAt;

    // Repeat functionality
    private boolean isRepeating;
    private String repeatType; // none, daily, weekly, monthly

    // Call tracking
    private String callSid; // For tracking actual call (Twilio/ElevenLabs)
    private long actualCallStartTime; // When the call actually started
    private long actualCallEndTime; // When the call actually ended
    private int actualCallDurationSeconds; // Actual duration of the call

    public ScheduledCall() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.status = "pending";
        this.isRepeating = false;
        this.repeatType = "none";
        this.actualCallStartTime = 0;
        this.actualCallEndTime = 0;
        this.actualCallDurationSeconds = 0;
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
        this.updatedAt = System.currentTimeMillis();
    }

    public long getScheduledDateTime() {
        return scheduledDateTime;
    }

    public void setScheduledDateTime(long scheduledDateTime) {
        this.scheduledDateTime = scheduledDateTime;
        this.updatedAt = System.currentTimeMillis();
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getConversationNotes() {
        return conversationNotes;
    }

    public void setConversationNotes(String conversationNotes) {
        this.conversationNotes = conversationNotes;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = System.currentTimeMillis();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isRepeating() {
        return isRepeating;
    }

    public void setRepeating(boolean repeating) {
        isRepeating = repeating;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getRepeatType() {
        return repeatType;
    }

    public void setRepeatType(String repeatType) {
        this.repeatType = repeatType;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getCallSid() {
        return callSid;
    }

    public void setCallSid(String callSid) {
        this.callSid = callSid;
        this.updatedAt = System.currentTimeMillis();
    }

    public long getActualCallStartTime() {
        return actualCallStartTime;
    }

    public void setActualCallStartTime(long actualCallStartTime) {
        this.actualCallStartTime = actualCallStartTime;
        this.updatedAt = System.currentTimeMillis();
    }

    public long getActualCallEndTime() {
        return actualCallEndTime;
    }

    public void setActualCallEndTime(long actualCallEndTime) {
        this.actualCallEndTime = actualCallEndTime;
        this.updatedAt = System.currentTimeMillis();
    }

    public int getActualCallDurationSeconds() {
        return actualCallDurationSeconds;
    }

    public void setActualCallDurationSeconds(int actualCallDurationSeconds) {
        this.actualCallDurationSeconds = actualCallDurationSeconds;
        this.updatedAt = System.currentTimeMillis();
    }

    // Helpful utility methods

    /**
     * Check if this call is due (should be executed now)
     */
    public boolean isDue() {
        return scheduledDateTime <= System.currentTimeMillis() &&
                "pending".equals(status);
    }

    /**
     * Check if this call is overdue (missed the scheduled time)
     */
    public boolean isOverdue() {
        long fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000);
        return scheduledDateTime < fiveMinutesAgo && "pending".equals(status);
    }

    /**
     * Check if this call is currently in progress
     */
    public boolean isInProgress() {
        return "in_progress".equals(status);
    }

    /**
     * Check if this call is completed
     */
    public boolean isCompleted() {
        return "completed".equals(status);
    }

    /**
     * Check if this call has failed
     */
    public boolean hasFailed() {
        return "failed".equals(status);
    }

    /**
     * Check if this call was cancelled
     */
    public boolean isCancelled() {
        return "cancelled".equals(status);
    }

    /**
     * Mark this call as started
     */
    public void markStarted() {
        this.status = "in_progress";
        this.actualCallStartTime = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * Mark this call as completed
     */
    public void markCompleted() {
        this.status = "completed";
        this.actualCallEndTime = System.currentTimeMillis();
        if (this.actualCallStartTime > 0) {
            this.actualCallDurationSeconds =
                    (int) ((this.actualCallEndTime - this.actualCallStartTime) / 1000);
        }
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * Mark this call as failed with reason
     */
    public void markFailed() {
        this.status = "failed";
        this.actualCallEndTime = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * Get a display-friendly summary of the call
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();

        if (contactName != null && !contactName.isEmpty()) {
            summary.append(contactName);
        } else {
            summary.append(phoneNumber);
        }

        if (purpose != null && !purpose.isEmpty()) {
            summary.append(" - ").append(purpose);
        }

        return summary.toString();
    }

    /**
     * Validate that this call has all required fields
     */
    public boolean isValid() {
        return phoneNumber != null && !phoneNumber.isEmpty() &&
                scheduledDateTime > 0 &&
                durationMinutes > 0;
    }

    /**
     * Check if this call has AI instructions
     */
    public boolean hasAIInstructions() {
        return conversationNotes != null && !conversationNotes.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "ScheduledCall{" +
                "id=" + id +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", contactName='" + contactName + '\'' +
                ", purpose='" + purpose + '\'' +
                ", scheduledDateTime=" + scheduledDateTime +
                ", status='" + status + '\'' +
                ", hasNotes=" + hasAIInstructions() +
                '}';
    }
}