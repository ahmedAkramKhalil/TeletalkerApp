package com.teletalker.app.features.booking.data;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.teletalker.app.network.FirebaseFunctionsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads the merchant's /merchants/{uid} doc from Firestore and saves changes
 * via the upsertMerchantProfile callable.
 */
public class MerchantProfileRepository {

    private static final String TAG = "MerchantProfileRepo";

    public interface OnProfileLoaded {
        void onLoaded(@NonNull MerchantProfile profile, boolean isNew);
        void onError(String error);
    }

    public interface OnProfileSaved {
        void onSaved();
        void onError(String error);
    }

    public void load(OnProfileLoaded cb) {
        String uid = currentUid();
        if (uid == null) {
            cb.onError("Not signed in");
            return;
        }
        FirebaseFirestore.getInstance()
                .collection("merchants").document(uid).get()
                .addOnSuccessListener(snap -> {
                    MerchantProfile p = parse(snap);
                    cb.onLoaded(p, !snap.exists());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load merchant profile", e);
                    cb.onError(e.getMessage());
                });
    }

    public void save(MerchantProfile p, OnProfileSaved cb) {
        FirebaseFunctionsManager.getInstance().upsertMerchantProfile(
                p.toCallable(),
                new FirebaseFunctionsManager.OnMerchantProfileCallback() {
                    @Override public void onSuccess() { cb.onSaved(); }
                    @Override public void onError(String error) { cb.onError(error); }
                });
    }

    // ----- parse -----

    private MerchantProfile parse(DocumentSnapshot snap) {
        MerchantProfile p = new MerchantProfile();
        if (!snap.exists()) return p;

        p.slug          = string(snap, "slug",          "");
        p.businessName  = string(snap, "businessName",  "");
        p.timezone      = string(snap, "timezone",      "UTC");
        p.description   = string(snap, "description",   "");
        p.phone         = string(snap, "phone",         "");
        p.address       = string(snap, "address",       "");
        p.website       = string(snap, "website",       "");
        p.coverImageUrl = string(snap, "coverImageUrl", "");
        p.logoUrl       = string(snap, "logoUrl",       "");
        p.active        = Boolean.TRUE.equals(snap.getBoolean("active"));

        p.bookingMode = string(snap, "bookingMode", MerchantProfile.MODE_INTERVAL);
        Long interval = snap.getLong("slotIntervalMin");
        p.slotIntervalMin = interval != null ? interval.intValue() : 30;

        // Booking page template chosen by the merchant. Falls back to dental-clinic
        // so a merchant who saved before this field existed still has a working page.
        p.templateId = string(snap, "templateId", "dental-clinic");

        // Services
        Object svcRaw = snap.get("services");
        if (svcRaw instanceof List<?>) {
            for (Object o : (List<?>) svcRaw) {
                if (!(o instanceof Map)) continue;
                Map<?, ?> m = (Map<?, ?>) o;
                ServiceItem s = new ServiceItem();
                if (m.get("id") instanceof String) s.id = (String) m.get("id");
                if (m.get("name") instanceof String) s.name = (String) m.get("name");
                if (m.get("durationMin") instanceof Number)
                    s.durationMin = ((Number) m.get("durationMin")).intValue();
                if (m.get("price") instanceof Number)
                    s.price = ((Number) m.get("price")).doubleValue();
                if (m.get("description") instanceof String) s.description = (String) m.get("description");
                p.services.add(s);
            }
        }

        // Hours: map<weekday, list<{open,close}>>
        Object hoursRaw = snap.get("hours");
        if (hoursRaw instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) hoursRaw).entrySet()) {
                if (!(e.getKey() instanceof String)) continue;
                String day = (String) e.getKey();
                List<DayRange> ranges = new ArrayList<>();
                if (e.getValue() instanceof List<?>) {
                    for (Object r : (List<?>) e.getValue()) {
                        if (!(r instanceof Map)) continue;
                        Map<?, ?> rm = (Map<?, ?>) r;
                        Object op = rm.get("open"), cl = rm.get("close");
                        if (op instanceof String && cl instanceof String) {
                            ranges.add(new DayRange((String) op, (String) cl));
                        }
                    }
                }
                if (!ranges.isEmpty()) p.hours.put(day, ranges);
            }
        }

        // Manual slots
        Object msRaw = snap.get("manualSlots");
        if (msRaw instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) msRaw).entrySet()) {
                if (!(e.getKey() instanceof String)) continue;
                String day = (String) e.getKey();
                List<String> times = new ArrayList<>();
                if (e.getValue() instanceof List<?>) {
                    for (Object t : (List<?>) e.getValue()) {
                        if (t instanceof String) times.add((String) t);
                    }
                }
                if (!times.isEmpty()) p.manualSlots.put(day, times);
            }
        }

        // Booking agent
        p.bookingAgentId   = string(snap, "bookingAgentId",   "");
        p.bookingAgentName = string(snap, "bookingAgentName", "");

        // AI customization
        Object aiRaw = snap.get("aiCustomization");
        if (aiRaw instanceof Map) {
            Map<?,?> ai = (Map<?,?>) aiRaw;
            Object tone = ai.get("tone");
            p.aiTone = tone instanceof String ? (String) tone : "warm";
            Object fm = ai.get("firstMessage");
            p.aiFirstMessage = fm instanceof String ? (String) fm : "";
            Object vm = ai.get("voicemailMessage");
            p.aiVoicemailMessage = vm instanceof String ? (String) vm : "";
            Object adv = ai.get("advancedPrompt");
            p.aiAdvancedPrompt = adv instanceof String ? (String) adv : "";
            Object rules = ai.get("customRules");
            p.aiCustomRules = new java.util.ArrayList<>();
            if (rules instanceof java.util.List) {
                for (Object r : (java.util.List<?>) rules) {
                    if (r instanceof String && !((String) r).isEmpty()) {
                        p.aiCustomRules.add((String) r);
                    }
                }
            }
        }

        // Theme
        Object themeRaw = snap.get("theme");
        if (themeRaw instanceof Map<?, ?>) {
            Map<?, ?> th = (Map<?, ?>) themeRaw;
            if (th.get("accentColor") instanceof String) p.accentColor = (String) th.get("accentColor");
            if (th.get("accentColor2") instanceof String) p.accentColor2 = (String) th.get("accentColor2");
        }

        return p;
    }

    private static String string(DocumentSnapshot s, String key, String fallback) {
        Object v = s.get(key);
        return v instanceof String ? (String) v : fallback;
    }

    private static String currentUid() {
        return FirebaseAuth.getInstance().getCurrentUser() == null ? null
                : FirebaseAuth.getInstance().getCurrentUser().getUid();
    }
}
