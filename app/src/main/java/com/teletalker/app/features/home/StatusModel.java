package com.teletalker.app.features.home;

import com.teletalker.app.R;

public class StatusModel {

    public enum InternetStatus {
        CONNECTED("Connected", R.color.status_green, R.drawable.ic_wifi),
        POOR("Poor Connection", R.color.status_yellow, R.drawable.ic_wifi_weak),
        DISCONNECTED("No Internet", R.color.status_red, R.drawable.ic_wifi_off);

        public final String displayText;
        public final int colorRes;
        public final int iconRes;

        InternetStatus(String displayText, int colorRes, int iconRes) {
            this.displayText = displayText;
            this.colorRes = colorRes;
            this.iconRes = iconRes;
        }
    }

    public enum CallStatus {
        IDLE(false),
        RINGING("Incoming Call", R.color.transpernt, R.drawable.ic_call_incoming, true),
        ACTIVE("Call Active", R.color.transpernt, R.drawable.ic_callhistory_active, true),
        HOLDING("On Hold", R.color.transpernt, R.drawable.ic_call_slash, true),
        ENDING("Ending...", R.color.transpernt, R.drawable.ic_call_slash, true);

        public final boolean isVisible;
        public final String displayText;
        public final int colorRes;
        public final int iconRes;
        public final boolean showDuration;

        CallStatus(boolean isVisible) {
            this.isVisible = isVisible;
            this.displayText = null;
            this.colorRes = 0;
            this.iconRes = 0;
            this.showDuration = false;
        }

        CallStatus(String displayText, int colorRes, int iconRes, boolean showDuration) {
            this.isVisible = true;
            this.displayText = displayText;
            this.colorRes = colorRes;
            this.iconRes = iconRes;
            this.showDuration = showDuration;
        }
    }

    public enum AIStatus {
        HIDDEN(false),
        CONNECTING("Connecting...", R.color.status_yellow, R.drawable.ic_bot, true),
        CONNECTED("Ready", R.color.status_green, R.drawable.ic_bot, false),
        ACTIVE("Speaking", R.color.status_blue, R.drawable.ic_bot, true),
        ERROR("Error", R.color.status_red, R.drawable.ic_bot, false);

        public final boolean isVisible;
        public final String displayText;
        public final int colorRes;
        public final int iconRes;
        public final boolean shouldAnimate;

        AIStatus(boolean isVisible) {
            this.isVisible = isVisible;
            this.displayText = null;
            this.colorRes = 0;
            this.iconRes = 0;
            this.shouldAnimate = false;
        }

        AIStatus(String displayText, int colorRes, int iconRes, boolean shouldAnimate) {
            this.isVisible = true;
            this.displayText = displayText;
            this.colorRes = colorRes;
            this.iconRes = iconRes;
            this.shouldAnimate = shouldAnimate;
        }
    }

    // Status data holders
    public static class InternetState {
        public final InternetStatus status;
        public final long timestamp;

        public InternetState(InternetStatus status) {
            this.status = status;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static class CallState {
        public final CallStatus status;
        public final String phoneNumber;
        public final String contactName;
        public final long startTime;
        public final long timestamp;

        public CallState(CallStatus status, String phoneNumber, String contactName, long startTime) {
            this.status = status;
            this.phoneNumber = phoneNumber != null ? phoneNumber : "";
            this.contactName = contactName != null ? contactName : "Unknown";
            this.startTime = startTime;
            this.timestamp = System.currentTimeMillis();
        }

        public String getDisplayName() {
            return !contactName.equals("Unknown") ? contactName : phoneNumber;
        }

        public long getDuration() {
            return startTime > 0 ? System.currentTimeMillis() - startTime : 0;
        }

        public String getFormattedDuration() {
            long duration = getDuration();
            long seconds = (duration / 1000) % 60;
            long minutes = (duration / (1000 * 60)) % 60;
            long hours = (duration / (1000 * 60 * 60));

            if (hours > 0) {
                return String.format("%d:%02d:%02d", hours, minutes, seconds);
            } else {
                return String.format("%d:%02d", minutes, seconds);
            }
        }
    }

    public static class AIState {
        public final AIStatus status;
        public final boolean isRecording;
        public final boolean isInjecting;
        public final String lastResponse;
        public final long timestamp;

        public AIState(AIStatus status, boolean isRecording, boolean isInjecting, String lastResponse) {
            this.status = status;
            this.isRecording = isRecording;
            this.isInjecting = isInjecting;
            this.lastResponse = lastResponse != null ? lastResponse : "";
            this.timestamp = System.currentTimeMillis();
        }

        public String getDetailsText() {
            if (status == AIStatus.ACTIVE && !lastResponse.isEmpty()) {
                return lastResponse.length() > 30 ?
                        lastResponse.substring(0, 30) + "..." : lastResponse;
            }

            StringBuilder details = new StringBuilder();
            if (isRecording) details.append("Recording • ");
            if (isInjecting) details.append("Injecting • ");

            if (details.length() > 0) {
                details.setLength(details.length() - 3); // Remove last " • "
                return details.toString();
            }

            return "Listening...";
        }
    }
}
