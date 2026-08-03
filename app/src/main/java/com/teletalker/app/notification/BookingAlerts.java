package com.teletalker.app.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.teletalker.app.R;
import com.teletalker.app.features.home.fragments.settings.SubscriptionPlanActivity;

import java.util.Locale;

/**
 * Notifications fired by the booking pipeline (separate channel from
 * billing/subscription pings so users can mute one without the other).
 */
public final class BookingAlerts {

    private static final String TAG = "BookingAlerts";
    private static final String CHANNEL_ID = "teletalker_booking";
    private static final int NOTIFICATION_ID_LOW_BALANCE = 0x10A1;

    private BookingAlerts() {}

    /**
     * High-priority alert: a booking just arrived but the device doesn't
     * have enough balance to comfortably place the AI call. The booking is
     * still scheduled — the user can top up before the ~30 s timer fires.
     */
    public static void notifyLowBalance(
            Context ctx, String customerName, double remainingMinutes) {
        ensureChannel(ctx);

        String body = ctx.getString(
                R.string.booking_low_balance_body,
                customerName == null || customerName.isEmpty() ? "a customer" : customerName,
                String.format(Locale.getDefault(), "%.1f", Math.max(0, remainingMinutes)));

        Intent topUp = new Intent(ctx, SubscriptionPlanActivity.class);
        topUp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 91,
                topUp, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_bing)
                .setColor(ctx.getResources().getColor(R.color.colorPrimary, ctx.getTheme()))
                .setContentTitle(ctx.getString(R.string.booking_low_balance_title))
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .addAction(0, ctx.getString(R.string.booking_low_balance_top_up), pi);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID_LOW_BALANCE, b.build());
            Log.d(TAG, "Posted low-balance booking alert (" + remainingMinutes + " min left)");
        }
    }

    private static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.booking_alerts_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(ctx.getString(R.string.booking_alerts_channel_desc));
        nm.createNotificationChannel(ch);
    }
}
