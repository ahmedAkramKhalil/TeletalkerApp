// AudioStreamInjector.java
package com.teletalker.app.services.ai;

import android.util.Log;
import java.io.*;

public class AudioStreamInjector {
    private static final String TAG = "AudioStreamInjector";
    private static final String SCRIPT_PATH = "/data/local/tmp/call_injector/inject_stream.sh";
    private static final String PIPE_PATH = "/data/local/tmp/call_injector/audio_stream.pipe";

    private Process streamProcess;
    private OutputStream pipeStream;
    private boolean isStreaming = false;

    // Start streaming mode
    public boolean startStreaming() {
        try {
            // Start stream script
            ProcessBuilder pb = new ProcessBuilder("su", "-c", SCRIPT_PATH + " stream");
            streamProcess = pb.start();

            // Wait for pipe
            Thread.sleep(1000);

            // Open pipe for writing
            File pipe = new File(PIPE_PATH);
            if (pipe.exists()) {
                pipeStream = new FileOutputStream(pipe);
                isStreaming = true;
                Log.i(TAG, "Streaming started");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start streaming", e);
        }
        return false;
    }

    // Stream PCM data
    public void streamPCM(byte[] pcmData) {
        if (isStreaming && pipeStream != null) {
            try {
                pipeStream.write(pcmData);
                pipeStream.flush();
            } catch (IOException e) {
                Log.e(TAG, "Stream write failed", e);
            }
        }
    }

    // Stop streaming
    public void stopStreaming() {
        isStreaming = false;
        try {
            if (pipeStream != null) {
                pipeStream.close();
            }
            if (streamProcess != null) {
                streamProcess.destroy();
            }
        } catch (Exception e) {
            Log.e(TAG, "Stop failed", e);
        }
    }

    // Test injection
    public static void testInjection() {
        try {
            Runtime.getRuntime().exec(new String[]{
                    "su", "-c",
                    "/data/local/tmp/call_injector/test_injection.sh"
            });
        } catch (Exception e) {
            Log.e(TAG, "Test failed", e);
        }
    }
}