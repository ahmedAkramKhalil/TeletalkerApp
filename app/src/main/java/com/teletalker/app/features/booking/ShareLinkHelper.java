package com.teletalker.app.features.booking;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.teletalker.app.R;

/** Builds + shares the public booking page URL. */
public final class ShareLinkHelper {

    /** Public booking app base URL. Update if the web app moves. */
    public static final String BASE_URL = "https://teletalker-21348.web.app";

    private ShareLinkHelper() {}

    public static String urlFor(String slug) {
        if (slug == null || slug.isEmpty()) return BASE_URL;
        return BASE_URL + "/book/" + slug;
    }

    /** Open the Android share sheet with the link prefilled. */
    public static void share(Context ctx, String slug, String businessName) {
        String url = urlFor(slug);
        String name = businessName != null && !businessName.isEmpty() ? businessName : "TeleTalker";
        String text = ctx.getString(R.string.booking_share_text, name, url);

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        send.putExtra(Intent.EXTRA_SUBJECT, name + " — book online");
        ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.booking_share_title)));
    }

    /** Copy link to clipboard with a toast. */
    public static void copy(Context ctx, String slug) {
        String url = urlFor(slug);
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("TeleTalker booking link", url));
            Toast.makeText(ctx, R.string.booking_link_copied, Toast.LENGTH_SHORT).show();
        }
    }
}
