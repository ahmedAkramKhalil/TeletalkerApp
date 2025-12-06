package com.teletalker.app.services;

import android.net.Uri;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

/**
 * ConnectionService for handling outgoing calls
 * Works alongside InCallService (CallDetector) which monitors all calls
 */
public class TeleTalkerConnectionService extends ConnectionService {

    private static final String TAG = "TTConnectionService";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "✅ ConnectionService created");
    }

    /**
     * Creates outgoing connection - InCallService will still receive this call
     */
    @Override
    public Connection onCreateOutgoingConnection(
            PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {

        Log.d(TAG, "📞 Creating outgoing connection");

        Uri handle = request.getAddress();
        if (handle == null) {
            Log.e(TAG, "❌ No phone number provided");
            return Connection.createFailedConnection(
                    new DisconnectCause(DisconnectCause.ERROR, "No phone number")
            );
        }

        String phoneNumber = handle.getSchemeSpecificPart();
        Log.d(TAG, "📱 Dialing: " + phoneNumber);

        // Create our custom connection
        TeleTalkerConnection connection = new TeleTalkerConnection(this, phoneNumber);

        // Set connection properties
        connection.setAddress(handle, TelecomManager.PRESENTATION_ALLOWED);
        connection.setCallerDisplayName(phoneNumber, TelecomManager.PRESENTATION_ALLOWED);

        // Set capabilities
        connection.setConnectionCapabilities(
                Connection.CAPABILITY_SUPPORT_HOLD |
                        Connection.CAPABILITY_HOLD |
                        Connection.CAPABILITY_MUTE
        );

        Log.d(TAG, "✅ Outgoing connection created - InCallService will also receive this");
        return connection;
    }

    /**
     * Let system handle incoming calls - InCallService will monitor them
     */
    @Override
    public Connection onCreateIncomingConnection(
            PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {

        Log.d(TAG, "📲 Incoming call - letting system handle (InCallService will monitor)");

        // Return null - system handles incoming calls
        // InCallService (CallDetector) will receive the callback
        return null;
    }

    /**
     * Custom Connection class for our outgoing calls
     */
    private static class TeleTalkerConnection extends Connection {

        private final String phoneNumber;
        private final TeleTalkerConnectionService service;

        public TeleTalkerConnection(TeleTalkerConnectionService service, String phoneNumber) {
            this.service = service;
            this.phoneNumber = phoneNumber;

            // Set initial state
            setDialing();
            Log.d(TAG, "🔄 Connection dialing: " + phoneNumber);
        }

        @Override
        public void onAnswer() {
            Log.d(TAG, "✅ Call answered: " + phoneNumber);
            setActive();
        }

        @Override
        public void onReject() {
            Log.d(TAG, "❌ Call rejected: " + phoneNumber);
            setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
            destroy();
        }

        @Override
        public void onDisconnect() {
            Log.d(TAG, "🔴 Call disconnected: " + phoneNumber);
            setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
            destroy();
        }

        @Override
        public void onAbort() {
            Log.d(TAG, "⚠️ Call aborted: " + phoneNumber);
            setDisconnected(new DisconnectCause(DisconnectCause.CANCELED));
            destroy();
        }

        @Override
        public void onHold() {
            Log.d(TAG, "⏸️ Call on hold: " + phoneNumber);
            setOnHold();
        }

        @Override
        public void onUnhold() {
            Log.d(TAG, "▶️ Call resumed: " + phoneNumber);
            setActive();
        }

        @Override
        public void onPlayDtmfTone(char c) {
            Log.d(TAG, "🔢 DTMF tone: " + c);
        }

        @Override
        public void onStopDtmfTone() {
            Log.d(TAG, "🔢 DTMF tone stopped");
        }
    }
}