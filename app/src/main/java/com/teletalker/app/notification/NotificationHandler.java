package com.teletalker.app.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.teletalker.app.R;
import com.teletalker.app.billing.BillingManager;
import com.teletalker.app.features.home.HomeActivity;
import com.teletalker.app.features.home.fragments.settings.InvoicesActivity;
import com.teletalker.app.utils.SubscriptionManager;

import java.util.Map;

public class NotificationHandler extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "teletalker_payments";

    @Override
    public void onMessageReceived(RemoteMessage message) {
        Map<String, String> data = message.getData();
        String type = data.get("type");
        String action = data.get("action");

        // Sync balance on payment notifications
        if ("payment_success".equals(type) || "renewal_success".equals(type)) {
            BillingManager.getInstance(this).syncBalance();
        }
        if ("payment_success".equals(type) ||
                "renewal_success".equals(type) ||
                "subscription_success".equals(type)) {

            SubscriptionManager.getInstance(this).sync();
        }

        // Show notification
        String title = message.getNotification() != null ?
                message.getNotification().getTitle() : "TeleTalker";
        String body = message.getNotification() != null ?
                message.getNotification().getBody() : "";

        showNotification(title, body, action, data.get("invoiceUrl"));
    }

    private void showNotification(String title, String body, String action, String invoiceUrl) {
        createChannel();

        Intent intent;

        // Route based on action
        if ("open_payment".equals(action) || "view_invoices".equals(action)) {
            intent = new Intent(this, InvoicesActivity.class);
        } else {
            intent = new Intent(this, HomeActivity.class);
        }

        if (invoiceUrl != null) {
            intent.putExtra("invoice_url", invoiceUrl);
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_bing)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Payments",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onNewToken(String token) {
        // Save token to Firebase
        // You already have updatefcmtoken function
    }
}