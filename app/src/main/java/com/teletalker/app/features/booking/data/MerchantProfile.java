package com.teletalker.app.features.booking.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable in-memory representation of the merchant's /merchants/{uid} doc.
 * Mirrors the shape used by the web app and the Firestore document — see
 * web repo /lib/types.ts.
 */
public class MerchantProfile {

    public static final String MODE_INTERVAL = "interval";
    public static final String MODE_SLOTS    = "slots";
    public static final String MODE_ANYTIME  = "anytime";

    public String slug = "";
    public String businessName = "";
    public String timezone = "UTC";
    public String description = "";
    public String phone = "";
    public String address = "";
    public String website = "";
    public String coverImageUrl = "";
    public String logoUrl = "";
    public Map<String, String> socials = new HashMap<>();
    public boolean active = true;

    /** ID of the booking-page template the merchant picked
     *  (e.g. "dental-clinic", "barber", "salon-spa"). Matches the file name in
     *  /public/templates/<id>.html on hosting. Defaults to dental-clinic for
     *  brand-new profiles so the share-link card always points somewhere usable. */
    public String templateId = "dental-clinic";

    public List<ServiceItem> services = new ArrayList<>();

    /** weekday key (mon..sun) -> list of {open,close} ranges */
    public Map<String, List<DayRange>> hours = new LinkedHashMap<>();

    public String bookingMode = MODE_INTERVAL;
    public int slotIntervalMin = 30;

    /** weekday key (mon..sun) -> list of "HH:MM" strings */
    public Map<String, List<String>> manualSlots = new LinkedHashMap<>();

    public String accentColor;     // hex like "#2249B3"
    public String accentColor2;

    /** ElevenLabs agent id used for AI confirmation calls to this merchant. */
    public String bookingAgentId = "";
    /** Display name of the chosen agent — cached so we can render the picker
     *  without re-fetching the agent list every time settings opens. */
    public String bookingAgentName = "";

    // ---------- AI script customization ----------
    // The mobile UI lets the merchant tweak tone + scripts without touching
    // the tool-call rules baked into the base prompt. Empty strings = use
    // defaults. `advancedPrompt` (when non-empty) completely replaces the
    // guided composition for power users.
    public String aiTone = "warm";              // warm | professional | casual | friendly
    public String aiFirstMessage = "";          // template with {placeholders}
    public String aiVoicemailMessage = "";      // template with {placeholders}
    public java.util.List<String> aiCustomRules = new java.util.ArrayList<>();
    public String aiAdvancedPrompt = "";        // overrides guided composition when set

    /** Build a Map<String,Object> suitable for the upsertMerchantProfile callable. */
    public Map<String, Object> toCallable() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("slug", slug);
        data.put("businessName", businessName);
        data.put("timezone", timezone);
        if (description != null && !description.isEmpty()) data.put("description", description);
        if (phone       != null && !phone.isEmpty())       data.put("phone", phone);
        if (address     != null && !address.isEmpty())     data.put("address", address);
        if (website     != null && !website.isEmpty())     data.put("website", website);
        if (coverImageUrl != null && !coverImageUrl.isEmpty()) data.put("coverImageUrl", coverImageUrl);
        if (logoUrl       != null && !logoUrl.isEmpty())       data.put("logoUrl", logoUrl);
        if (socials != null && !socials.isEmpty()) data.put("socials", socials);

        List<Map<String, Object>> svc = new ArrayList<>();
        for (ServiceItem s : services) svc.add(s.toMap());
        data.put("services", svc);

        Map<String, Object> hoursOut = new LinkedHashMap<>();
        for (Map.Entry<String, List<DayRange>> e : hours.entrySet()) {
            List<Map<String, Object>> ranges = new ArrayList<>();
            for (DayRange r : e.getValue()) {
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put("open", r.open);
                rm.put("close", r.close);
                ranges.add(rm);
            }
            if (!ranges.isEmpty()) hoursOut.put(e.getKey(), ranges);
        }
        data.put("hours", hoursOut);

        data.put("bookingMode", bookingMode);
        data.put("slotIntervalMin", slotIntervalMin);

        // Always send templateId so the server can update an existing merchant
        // if they pick a different template.
        data.put("templateId", templateId == null || templateId.isEmpty() ? "dental-clinic" : templateId);

        if (MODE_SLOTS.equals(bookingMode)) {
            Map<String, Object> ms = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> e : manualSlots.entrySet()) {
                if (e.getValue() != null && !e.getValue().isEmpty()) ms.put(e.getKey(), e.getValue());
            }
            data.put("manualSlots", ms);
        }

        if (bookingAgentId != null && !bookingAgentId.isEmpty()) {
            data.put("bookingAgentId", bookingAgentId);
            if (bookingAgentName != null && !bookingAgentName.isEmpty()) {
                data.put("bookingAgentName", bookingAgentName);
            }
        }

        // AI customization — always send (even empty strings) so the server
        // can clear previously-set fields.
        Map<String, Object> ai = new java.util.HashMap<>();
        ai.put("tone",             aiTone == null ? "warm" : aiTone);
        ai.put("firstMessage",     aiFirstMessage == null ? "" : aiFirstMessage);
        ai.put("voicemailMessage", aiVoicemailMessage == null ? "" : aiVoicemailMessage);
        ai.put("customRules",      aiCustomRules == null ? new java.util.ArrayList<>() : aiCustomRules);
        ai.put("advancedPrompt",   aiAdvancedPrompt == null ? "" : aiAdvancedPrompt);
        data.put("aiCustomization", ai);

        if (accentColor != null && !accentColor.isEmpty()) {
            Map<String, Object> theme = new LinkedHashMap<>();
            theme.put("accentColor", accentColor);
            if (accentColor2 != null && !accentColor2.isEmpty()) theme.put("accentColor2", accentColor2);
            data.put("theme", theme);
        }

        return data;
    }

    public boolean hasSlug() {
        return slug != null && !slug.isEmpty();
    }
}
