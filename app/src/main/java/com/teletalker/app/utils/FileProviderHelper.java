package com.teletalker.app.utils;


import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.FileProvider;

import java.io.File;

/**
 * Helper class for sharing files using FileProvider
 */
public class FileProviderHelper {
    private static final String AUTHORITY = "com.teletalker.app.fileprovider";

    /**
     * Get content URI for a file to share it safely
     */
    public static Uri getUriForFile(Context context, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Use FileProvider for Android 7.0+
            return FileProvider.getUriForFile(context, AUTHORITY, file);
        } else {
            // Direct file URI for older Android versions
            return Uri.fromFile(file);
        }
    }

    /**
     * Share a recording file (audio)
     */
    public static Intent createShareIntent(Context context, File recordingFile) {
        Uri contentUri = getUriForFile(context, recordingFile);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("audio/*"); // or "audio/mp4" for m4a files
        shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Call Recording");
        shareIntent.putExtra(Intent.EXTRA_TEXT, "Recorded call: " + recordingFile.getName());

        // Grant temporary read permission
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        return Intent.createChooser(shareIntent, "Share Recording");
    }

    /**
     * Open a recording file with default audio player
     */
    public static Intent createPlayIntent(Context context, File recordingFile) {
        Uri contentUri = getUriForFile(context, recordingFile);

        Intent playIntent = new Intent(Intent.ACTION_VIEW);
        playIntent.setDataAndType(contentUri, "audio/*");
        playIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        return playIntent;
    }

    /**
     * Create intent to view file in file manager
     */
    public static Intent createViewIntent(Context context, File file) {
        Uri contentUri = getUriForFile(context, file);

        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setDataAndType(contentUri, "*/*");
        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        return viewIntent;
    }

    /**
     * Check if file exists in recordings directory
     */
    public static File getRecordingFile(Context context, String filename) {
        // This matches the path used in CallRecorder
        File recordingsDir = new File(
                context.getExternalFilesDir(null),
                "Music/TeleTalker"
        );

        return new File(recordingsDir, filename);
    }

    /**
     * Get all recording files
     */
    public static File[] getAllRecordings(Context context) {
        File recordingsDir = new File(
                context.getExternalFilesDir(null),
                "Music/TeleTalker"
        );

        if (recordingsDir.exists()) {
            return recordingsDir.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".m4a") ||
                            name.toLowerCase().endsWith(".mp3") ||
                            name.toLowerCase().endsWith(".wav")
            );
        }

        return new File[0];
    }
}