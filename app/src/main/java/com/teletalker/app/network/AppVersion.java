package com.teletalker.app.network;

import java.util.Map;

public class AppVersion {
    private String versionId;
    private String name;
    private boolean active;
    private int sortOrder;
    private Features features;
    private Limits limits;

    public static class Features {
        public boolean advancedAI;
        public boolean basicAI;
        public boolean callRecording;
        public boolean cloudStorage;
        public int dailyCallLimit;
        public int maxCallDuration;

        public static Features fromMap(Map<String, Object> map) {
            Features f = new Features();
            if (map == null) return f;
            f.advancedAI = getBool(map, "advancedAI");
            f.basicAI = getBool(map, "basicAI");
            f.callRecording = getBool(map, "callRecording");
            f.cloudStorage = getBool(map, "cloudStorage");
            f.dailyCallLimit = getInt(map, "dailyCallLimit", 10);
            f.maxCallDuration = getInt(map, "maxCallDuration", 5);
            return f;
        }
    }

    public static class Limits {
        public boolean canTopUp;
        public int freeMinutesOnSignup;
        public int maxBalance;

        public static Limits fromMap(Map<String, Object> map) {
            Limits l = new Limits();
            if (map == null) return l;
            l.canTopUp = getBool(map, "canTopUp");
            l.freeMinutesOnSignup = getInt(map, "freeMinutesOnSignup", 0);
            l.maxBalance = getInt(map, "maxBalance", 100);
            return l;
        }
    }

    public static AppVersion fromMap(Map<String, Object> map) {
        if (map == null) return null;
        AppVersion v = new AppVersion();
        v.versionId = (String) map.get("versionId");
        v.name = (String) map.get("name");
        v.active = getBool(map, "active");
        v.sortOrder = getInt(map, "sortOrder", 0);
        v.features = Features.fromMap((Map<String, Object>) map.get("features"));
        v.limits = Limits.fromMap((Map<String, Object>) map.get("limits"));
        return v;
    }

    // Helpers
    private static boolean getBool(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof Boolean && (Boolean) val;
    }

    private static int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        return val instanceof Number ? ((Number) val).intValue() : defaultVal;
    }

    // Getters
    public String getVersionId() { return versionId; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public int getSortOrder() { return sortOrder; }
    public Features getFeatures() { return features; }
    public Limits getLimits() { return limits; }
}