package com.teletalker.app.services;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.util.Log;

/**
 * Represents an individual call connection
 * Manages the lifecycle and state of a single call
 */
public class TeleTalkerConnection extends Connection {

    private static final String TAG = "TTConnection";
    private final Context context;
    private boolean isMuted = false;
    private boolean isOnHold = false;

    public TeleTalkerConnection(Context context) {
        super();
        this.context = context;
        Log.d(TAG, "TeleTalkerConnection created");
    }

    @Override
    public void onShowIncomingCallUi() {
        super.onShowIncomingCallUi();
        Log.d(TAG, "Show incoming call UI");
        // You can trigger your custom incoming call UI here
    }

    @Override
    public void onAnswer() {
        Log.d(TAG, "Call answered");
        setActive(); // Mark connection as active

        // Notify your AI service that call is active
        notifyCallStateChanged(true);
    }

    @Override
    public void onAnswer(int videoState) {
        super.onAnswer(videoState);
        Log.d(TAG, "Call answered with video state: " + videoState);
        setActive();

        notifyCallStateChanged(true);
    }

    @Override
    public void onReject() {
        Log.d(TAG, "Call rejected");
        setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
        destroy();

        notifyCallStateChanged(false);
    }

    @Override
    public void onDisconnect() {
        Log.d(TAG, "Call disconnected");
        setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        destroy();

        notifyCallStateChanged(false);
    }

    @Override
    public void onAbort() {
        Log.d(TAG, "Call aborted");
        setDisconnected(new DisconnectCause(DisconnectCause.CANCELED));
        destroy();

        notifyCallStateChanged(false);
    }

    @Override
    public void onHold() {
        Log.d(TAG, "Call placed on hold");
        setOnHold();
        isOnHold = true;
    }

    @Override
    public void onUnhold() {
        Log.d(TAG, "Call resumed from hold");
        setActive();
        isOnHold = false;
    }

    @Override
    public void onPlayDtmfTone(char c) {
        Log.d(TAG, "Play DTMF tone: " + c);
        // Handle DTMF tones for menu navigation
    }

    @Override
    public void onStopDtmfTone() {
        Log.d(TAG, "Stop DTMF tone");
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState state) {
        super.onCallAudioStateChanged(state);
        Log.d(TAG, "Audio state changed: " + state);
        isMuted = state.isMuted();

        // You can handle audio routing here (speaker, bluetooth, etc.)
        switch (state.getRoute()) {
            case CallAudioState.ROUTE_EARPIECE:
                Log.d(TAG, "Audio routed to earpiece");
                break;
            case CallAudioState.ROUTE_SPEAKER:
                Log.d(TAG, "Audio routed to speaker");
                break;
            case CallAudioState.ROUTE_BLUETOOTH:
                Log.d(TAG, "Audio routed to bluetooth");
                break;
            case CallAudioState.ROUTE_WIRED_HEADSET:
                Log.d(TAG, "Audio routed to wired headset");
                break;
        }
    }

    @Override
    public void onStateChanged(int state) {
        super.onStateChanged(state);
        Log.d(TAG, "Connection state changed: " + stateToString(state));
    }

    /**
     * Notify AI service about call state changes
     */
    private void notifyCallStateChanged(boolean isActive) {
        try {
            // TODO: Integrate with your AI service
            // Example: Send broadcast or start service
            // Intent intent = new Intent(context, VoIPCallService.class);
            // intent.putExtra("call_active", isActive);
            // context.startService(intent);

            Log.d(TAG, "Call state notification: " + (isActive ? "Active" : "Ended"));
        } catch (Exception e) {
            Log.e(TAG, "Error notifying call state", e);
        }
    }

    public static String stateToString(int state) {
        switch (state) {
            case STATE_INITIALIZING:
                return "INITIALIZING";
            case STATE_NEW:
                return "NEW";
            case STATE_RINGING:
                return "RINGING";
            case STATE_DIALING:
                return "DIALING";
            case STATE_ACTIVE:
                return "ACTIVE";
            case STATE_HOLDING:
                return "HOLDING";
            case STATE_DISCONNECTED:
                return "DISCONNECTED";
            default:
                return "UNKNOWN";
        }
    }
}