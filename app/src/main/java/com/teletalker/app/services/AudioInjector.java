package com.teletalker.app.services;

import android.content.Context;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AudioInjector {
    private static final String TAG = "AudioInjector";
    private static final String INJECTOR_SCRIPT = "/data/adb/modules/BCR/scripts/audio_injector.sh";

    public static void startInjection() {
        new Thread(() -> {
            try {
                // Just trigger the injector script (PCM is already in module assets)
                Runtime.getRuntime().exec(new String[]{
                        "su", "-c",
                        "nohup " + INJECTOR_SCRIPT + " >/dev/null 2>&1 &"
                });
                Log.d(TAG, "Audio injection started");
            } catch (IOException e) {
                Log.e(TAG, "Injection failed", e);
            }
        }).start();
    }

    public static void stopInjection() {
        try {
            Runtime.getRuntime().exec(new String[]{
                    "su", "-c",
                    "pkill -f audio_injector.sh"
            });
            Log.d(TAG, "Audio injection stopped");
        } catch (IOException e) {
            Log.e(TAG, "Failed to stop injection", e);
        }
    }
}