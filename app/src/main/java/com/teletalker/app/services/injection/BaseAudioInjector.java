package com.teletalker.app.services.injection;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public abstract class BaseAudioInjector implements AudioInjector {
    protected static final String TAG = "AudioInjector";

    protected final Context context;
    protected final AudioManager audioManager;
    protected final boolean isRooted;
    protected volatile boolean isInjecting = false;
    protected InjectionCallback callback;

    // Audio settings
    protected static final int TELEPHONY_SAMPLE_RATE = 8000;
    protected static final int HIGH_QUALITY_SAMPLE_RATE = 44100;

    public BaseAudioInjector(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.isRooted = checkRootAccess();
    }

    protected boolean checkRootAccess() {
        try {
            Process process = Runtime.getRuntime().exec("su -c echo test");
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    protected String executeRootCommand(String command) {
        if (!isRooted) return null;

        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            process.waitFor();
            return output.toString().trim();
        } catch (Exception e) {
            Log.e(TAG, "Failed to execute: " + command, e);
            return null;
        }
    }

    protected void executeRootCommands(String[] commands) {
        for (String command : commands) {
            executeRootCommand(command);
        }
    }

    protected String resolveAudioPath(String audioPath) throws IOException {
        if (audioPath.startsWith("assets/")) {
            return copyAssetToTemp(audioPath.substring(7));
        } else if (audioPath.startsWith("raw/")) {
            return copyRawToTemp(audioPath.substring(4));
        } else {
            // Regular file path
            File file = new File(audioPath);
            if (!file.exists()) {
                throw new IOException("Audio file not found: " + audioPath);
            }
            return audioPath;
        }
    }

    private String copyAssetToTemp(String assetPath) throws IOException {
        File tempDir = new File(context.getCacheDir(), "audio_temp");
        tempDir.mkdirs();

        String fileName = "temp_" + System.currentTimeMillis() + "_" + new File(assetPath).getName();
        File tempFile = new File(tempDir, fileName);

        try (InputStream is = context.getAssets().open(assetPath);
             FileOutputStream fos = new FileOutputStream(tempFile)) {

            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        }

        return tempFile.getAbsolutePath();
    }

    private String copyRawToTemp(String resourceName) throws IOException {
        int resourceId = context.getResources().getIdentifier(
                resourceName, "raw", context.getPackageName());

        if (resourceId == 0) {
            throw new IOException("Raw resource not found: " + resourceName);
        }

        File tempDir = new File(context.getCacheDir(), "audio_temp");
        tempDir.mkdirs();

        File tempFile = new File(tempDir, "temp_" + resourceName + ".wav");

        try (InputStream is = context.getResources().openRawResource(resourceId);
             FileOutputStream fos = new FileOutputStream(tempFile)) {

            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        }

        return tempFile.getAbsolutePath();
    }

    protected byte[] readAudioFile(String filePath) throws IOException {
        File file = new File(filePath);
        byte[] data = new byte[(int) file.length()];

        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(data);
        }

        return data;
    }

    @Override
    public void cleanup() {
        // Clean up temp files
        File tempDir = new File(context.getCacheDir(), "audio_temp");
        if (tempDir.exists()) {
            deleteRecursive(tempDir);
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}