package com.teletalker.app.services;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;

import java.util.List;

public class VoIPCallService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(@NonNull AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            AccessibilityNodeInfo source = event.getSource();
            if (source == null) return;

            logAllTexts(source);

        }
    }


    private void logAllTexts(AccessibilityNodeInfo node) {
        if (node == null) return;

        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            Log.d("VOIP_DEBUG", "Node text: " + text);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            logAllTexts(node.getChild(i));
        }
    }

    @Override
    public void onInterrupt() {
        Log.d("VoIPCallService", "Service Interrupted");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d("VoIPCallService", "Service Connected");
    }
}
