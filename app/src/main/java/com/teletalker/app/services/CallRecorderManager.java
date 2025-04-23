package com.teletalker.app.services;

import android.app.AlertDialog;
import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CallRecorderManager {
    private MediaRecorder recorder;
    private Map<String, MediaPlayer> playerMap = new HashMap<>();
    private String currentPlayingFile;
    private Context context;
    private int currentPosition = 0;

    public CallRecorderManager(Context context) {
        this.context = context;
    }

    public void startRecording() {
        try {
            currentPlayingFile = context.getExternalFilesDir(null).getAbsolutePath() + "/call_" + System.currentTimeMillis() + ".3gp";
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.setOutputFile(currentPlayingFile);
            recorder.prepare();
            recorder.start();
            Log.d("CallRecorder", "Recording started");
        } catch (Exception e) {
            Log.e("CallRecorder", "Recording failed: " + e.getMessage());
        }
    }

    public void stopRecording() {
        try {
            if (recorder != null) {
                recorder.stop();
                recorder.release();
                recorder = null;
                Log.d("CallRecorder", "Recording stopped");
            }
        } catch (Exception e) {
            Log.e("CallRecorder", "Stop failed: " + e.getMessage());
        }
    }

    public void togglePlay(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            Log.e("CallRecorder", "filePath is null or empty");
            return;
        }
        MediaPlayer currentPlayer = playerMap.get(filePath);

        if (currentPlayingFile != null && !currentPlayingFile.equals(filePath)) {
            stopPreviousPlayback();
            currentPlayer = null;
        }

        if (currentPlayer == null) {
            MediaPlayer newPlayer = new MediaPlayer();
            try {
                newPlayer.setDataSource(filePath);
                newPlayer.prepare();
                newPlayer.start();
                currentPlayingFile = filePath;
                playerMap.put(filePath, newPlayer);
                Toast.makeText(context, "Start playing", Toast.LENGTH_SHORT).show();

                newPlayer.setOnCompletionListener(mp -> {
                    releasePlayer(filePath);
                    currentPlayingFile = null;
                    currentPosition = 0;
                    Toast.makeText(context, "End playing", Toast.LENGTH_SHORT).show();
                });
            } catch (IOException e) {
                Log.e("CallRecorder", "Playback failed: " + e.getMessage());
            }

        } else if (currentPlayer.isPlaying()) {
            currentPlayer.pause();
            currentPosition = currentPlayer.getCurrentPosition();
            Log.d("CallRecorder", "Playback paused");
        } else {
            currentPlayer.seekTo(currentPosition);
            currentPlayer.start();
            Log.d("CallRecorder", "Playback resumed");
        }
    }

    public void stopPreviousPlayback() {
        for (MediaPlayer mp : playerMap.values()) {
            if (mp.isPlaying()) {
                mp.stop();
                mp.release();
            }
        }
        playerMap.clear();
        currentPlayingFile = null;
        currentPosition = 0;
    }

    private void releasePlayer(String filePath) {
        MediaPlayer mp = playerMap.remove(filePath);
        if (mp != null) {
            mp.release();
        }
    }

    public boolean isRecording() {
        return recorder != null;
    }

    public boolean isPlaying(String filePath) {
        return playerMap.containsKey(filePath) && playerMap.get(filePath).isPlaying();
    }

    public boolean isPaused(String filePath) {
        return currentPlayingFile != null
                && currentPlayingFile.equals(filePath)
                && playerMap.containsKey(filePath)
                && !playerMap.get(filePath).isPlaying();
    }


    public String getCurrentPlayingFile() {
        return currentPlayingFile;
    }

}
