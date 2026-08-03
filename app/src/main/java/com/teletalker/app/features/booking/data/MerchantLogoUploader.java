package com.teletalker.app.features.booking.data;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

/**
 * Uploads a merchant logo (chosen via ActivityResultContracts.GetContent)
 * to Firebase Storage at gs://teletalker-21348/merchant-logos/{uid}/logo.{ext}
 * and returns the resolved download URL.
 *
 * Why one fixed filename per uid: simple eviction. New upload overwrites the
 * old one; Storage rules only need to match a single path.
 *
 * Size cap is enforced both here (early reject) and in storage.rules
 * (defense in depth). 2 MB matches the rule.
 */
public final class MerchantLogoUploader {

    private static final String TAG = "MerchantLogoUploader";
    public static final long MAX_BYTES = 2 * 1024 * 1024;

    public interface Callback {
        void onUploaded(@NonNull String downloadUrl);
        void onError(@NonNull String message);
    }

    private MerchantLogoUploader() {}

    /**
     * @param ctx Any context (will use applicationContext for ContentResolver).
     * @param sourceUri the Uri returned by ActivityResultContracts.GetContent.
     * @param cb invoked on the main thread when done.
     */
    public static void upload(@NonNull Context ctx, @NonNull Uri sourceUri, @NonNull Callback cb) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            cb.onError("Not signed in");
            return;
        }

        ContentResolver resolver = ctx.getApplicationContext().getContentResolver();
        long size = sizeOf(resolver, sourceUri);
        if (size > MAX_BYTES) {
            cb.onError("Image is too large (max 2 MB)");
            return;
        }

        String mime = resolver.getType(sourceUri);
        String ext = extensionFor(mime);
        StorageReference ref = FirebaseStorage.getInstance()
                .getReference()
                .child("merchant-logos")
                .child(user.getUid())
                .child("logo." + ext);

        StorageMetadata.Builder metaBuilder = new StorageMetadata.Builder();
        if (mime != null && !mime.isEmpty()) metaBuilder.setContentType(mime);
        StorageMetadata metadata = metaBuilder.build();

        UploadTask task = ref.putFile(sourceUri, metadata);
        task.addOnFailureListener(e -> {
            Log.w(TAG, "logo upload failed", e);
            cb.onError(e.getMessage() != null ? e.getMessage() : "Upload failed");
        });
        task.addOnSuccessListener(snapshot -> ref.getDownloadUrl()
                .addOnSuccessListener(uri -> cb.onUploaded(uri.toString()))
                .addOnFailureListener(e -> {
                    Log.w(TAG, "getDownloadUrl failed", e);
                    cb.onError(e.getMessage() != null ? e.getMessage() : "Could not resolve URL");
                }));
    }

    /** Best-effort size lookup. Returns -1 if unknown so we don't reject. */
    private static long sizeOf(ContentResolver resolver, Uri uri) {
        try {
            android.database.Cursor c = resolver.query(uri, null, null, null, null);
            if (c != null) {
                try {
                    int idx = c.getColumnIndex(android.provider.OpenableColumns.SIZE);
                    if (idx >= 0 && c.moveToFirst() && !c.isNull(idx)) {
                        return c.getLong(idx);
                    }
                } finally {
                    c.close();
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    @NonNull
    private static String extensionFor(@Nullable String mime) {
        if (mime != null) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if (ext != null && !ext.isEmpty()) return ext;
        }
        return "png";
    }
}
