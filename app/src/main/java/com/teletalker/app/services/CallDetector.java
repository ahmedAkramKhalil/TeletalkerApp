package com.teletalker.app.services;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.Nullable;

import com.teletalker.app.features.home.fragments.callhistory.data.data_sources.local.database.CallDatabase;
import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;

public class CallDetector extends Service {
    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;
    private CallRecorderManager recorderManager;
    private boolean isCallStarted;
    private long callAnsweredTime;
    private long callEndTime;
    private String callDuration;
    private String callTime;
    String contactName;
    private String callState;
    private boolean isCallRecorded;


    @Override
    public void onCreate() {
        super.onCreate();
        telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        recorderManager = new CallRecorderManager(this);
        startListening();
    }


    private void startListening() {
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                super.onCallStateChanged(state, phoneNumber);

                switch (state) {

                    case TelephonyManager.CALL_STATE_RINGING:
                        contactName = getContactName(phoneNumber);
                        isCallStarted = true;
                        callTime = convertTimestampToReadableTime(System.currentTimeMillis());
                        callState = "Ringing";
                        callAnsweredTime = 0;
                        Log.d("CallDetector", "Incoming call from: " + phoneNumber);
                        break;

                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        contactName = getContactName(phoneNumber);
                        isCallStarted = true;

                        if ("Ringing".equals(callState)) {
                            callState = "IncomingAnswered";
                            callAnsweredTime = System.currentTimeMillis();
                            callTime = convertTimestampToReadableTime(callAnsweredTime);
                            Log.d("CallDetector", "Incoming call answered from: " + phoneNumber);
                        } else {
                            callState = "Outgoing";
                            callAnsweredTime = System.currentTimeMillis();
                            callTime = convertTimestampToReadableTime(callAnsweredTime);
                            recorderManager.startRecording();
                            Log.d("CallDetector", "Outgoing call started to: " + phoneNumber);
                        }

                        if (!recorderManager.isRecording()) {
                            recorderManager.startRecording();
                            Log.d("CallDetector", "Recording started");
                        } else {
                            Log.d("CallDetector", " Already recording");
                        }

                        break;

                    case TelephonyManager.CALL_STATE_IDLE:
                        contactName = getContactName(phoneNumber);
                        if (isCallStarted) {
                            callEndTime = System.currentTimeMillis();
                            if (callAnsweredTime == 0) {
                                if ("Ringing".equals(callState)) {
                                    callState = "Missed";
                                    callDuration = "";
                                }
                            } else {
                                long durationMillis = callEndTime - callAnsweredTime;
                                callDuration = formatDuration(durationMillis);
                            }

                            if (recorderManager.isRecording()) {
                                recorderManager.stopRecording();
                                isCallRecorded = true;
                            }

                            CallEntity callEntity = new CallEntity(
                                    phoneNumber,
                                    contactName,
                                    "SMS",
                                    callState,
                                    callDuration,
                                    callTime,
                                    recorderManager.getCurrentPlayingFile(),
                                    isCallRecorded
                            );

                            Log.d("CallDetector", "Saving call: " + callEntity.toString());

                            Executors.newSingleThreadExecutor().execute(() -> {
                                CallDatabase.getInstance(getApplicationContext()).callDao().insertCall(callEntity);
                                Log.d("CallDetector", "Call saved to database");
                            });
                        }

                        // Reset after each call
                        isCallStarted = false;
                        callAnsweredTime = 0;
                        callEndTime = 0;
                        break;
                }
            }
        };

        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopListening();
        Log.d("CallDetector", "Service destroyed");
    }

    public void stopListening() {
        if (telephonyManager != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressLint("DefaultLocale")
    private String formatDuration(long millis) {
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / (1000 * 60)) % 60;
        long hours = (millis / (1000 * 60 * 60)) % 24;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    @SuppressLint("Range")
    private String getContactName(String phoneNumber) {
        String contactName = "Unknown";
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return "Unknown";
        }
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        Cursor cursor = getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
            }
            cursor.close();
        }
        return contactName;
    }

    public String convertTimestampToReadableTime(long timestamp) {
        Date date = new Date(timestamp);
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM hh:mm a");
        return sdf.format(date);
    }



}