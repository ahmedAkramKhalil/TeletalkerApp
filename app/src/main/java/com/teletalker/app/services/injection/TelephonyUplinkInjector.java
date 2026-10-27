package com.teletalker.app.services.injection;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Injects PCM into the uplink of a PSTN (SIM) call through the platform API that
 * AOSP added in Android 13 for exactly this purpose:
 *
 *   AudioManager#isPstnCallAudioInterceptable()
 *   AudioManager#getCallUplinkInjectionAudioTrack(AudioFormat)
 *
 * Both are @SystemApi and require android.permission.CALL_AUDIO_INTERCEPTION
 * (signature|privileged) — granted to us as a priv-app through the Magisk
 * module's privapp allowlist. They are called by reflection because the app
 * compiles against the public SDK.
 *
 * Unlike the tinyalsa path (scripts/inject_stream.sh), this does not depend on
 * legacy mixer control names ("Incall_Music Audio Mixer MultiMediaN",
 * "CALL_REC Audio Mixer ..."). It works wherever the vendor audio HAL exposes a
 * Telephony Tx device to the audio policy, which is the case on modern
 * Snapdragon (AudioReach/PAL) devices where those mixer names no longer exist.
 *
 * Gating: only used on API 33+, and only when the platform reports the call is
 * interceptable. On API <= 32 (the Samsung A01 Core / Android 12 baseline) this
 * class reports unsupported and the existing tinyalsa path is used unchanged.
 *
 * Override for testing (root): setprop persist.teletalker.inject_path
 *   auto (default) | uplink (force this path) | tinyalsa (never use this path)
 */
public final class TelephonyUplinkInjector {
    private static final String TAG = "TelephonyUplinkInjector";
    private static final String PATH_PROP = "persist.teletalker.inject_path";

    private final AudioManager audioManager;
    private AudioTrack track;

    public TelephonyUplinkInjector(Context context) {
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    /** True when this path should be tried before tinyalsa on this device. */
    public boolean isPreferred() {
        String mode = readProp(PATH_PROP, "auto");
        if ("tinyalsa".equals(mode)) return false;
        if (Build.VERSION.SDK_INT < 33) {
            if ("uplink".equals(mode)) Log.w(TAG, "uplink forced but API < 33 has no such API");
            return false;
        }
        boolean interceptable = isInterceptable();
        Log.i(TAG, "isPstnCallAudioInterceptable=" + interceptable + " mode=" + mode);
        return interceptable;
    }

    public boolean isInterceptable() {
        if (Build.VERSION.SDK_INT < 33 || audioManager == null) return false;
        try {
            Method m = AudioManager.class.getMethod("isPstnCallAudioInterceptable");
            return (Boolean) m.invoke(audioManager);
        } catch (Throwable t) {
            Log.w(TAG, "isPstnCallAudioInterceptable unavailable: " + t);
            return false;
        }
    }

    /**
     * Opens the uplink track. Must be called while the SIM call is active
     * (audio mode IN_CALL); the platform refuses otherwise.
     */
    public synchronized boolean open(int sampleRate) {
        if (track != null) return true;
        if (Build.VERSION.SDK_INT < 33 || audioManager == null) return false;
        try {
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build();
            Method m = AudioManager.class.getMethod("getCallUplinkInjectionAudioTrack", AudioFormat.class);
            AudioTrack t = (AudioTrack) m.invoke(audioManager, format);
            if (t == null || t.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "uplink track not initialized");
                if (t != null) t.release();
                return false;
            }
            t.play();
            track = t;
            Log.i(TAG, "uplink injection track open @" + sampleRate + "Hz, routed to "
                    + (t.getRoutedDevice() != null ? t.getRoutedDevice().getType() : "?"));
            return true;
        } catch (java.lang.reflect.InvocationTargetException e) {
            Log.e(TAG, "getCallUplinkInjectionAudioTrack refused: " + e.getTargetException());
            return false;
        } catch (Throwable t) {
            Log.e(TAG, "uplink injection unavailable: " + t);
            return false;
        }
    }

    public synchronized boolean isOpen() {
        return track != null;
    }

    /** Blocking write of 16-bit little-endian PCM. */
    public boolean write(byte[] pcm) {
        AudioTrack t;
        synchronized (this) {
            t = track;
        }
        if (t == null) return false;
        int off = 0;
        while (off < pcm.length) {
            int n = t.write(pcm, off, pcm.length - off);
            if (n < 0) {
                Log.e(TAG, "AudioTrack.write error " + n);
                return false;
            }
            off += n;
        }
        return true;
    }

    public synchronized void close() {
        if (track == null) return;
        try {
            track.stop();
        } catch (Throwable ignored) {
        }
        track.release();
        track = null;
        Log.i(TAG, "uplink injection track closed");
    }

    private static String readProp(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String v = (String) sp.getMethod("get", String.class, String.class).invoke(null, key, def);
            return v == null || v.isEmpty() ? def : v;
        } catch (Throwable t) {
            return def;
        }
    }
}
