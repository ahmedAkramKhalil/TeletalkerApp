package com.teletalker.app.features.booking.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ServiceItem {
    public String id;
    public String name = "";
    public int durationMin = 30;
    public Double price;            // nullable
    public String description = "";

    public ServiceItem() {
        this.id = "s_" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static ServiceItem of(String name, int durationMin) {
        ServiceItem s = new ServiceItem();
        s.name = name;
        s.durationMin = durationMin;
        return s;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("durationMin", durationMin);
        if (price != null) m.put("price", price);
        if (description != null && !description.isEmpty()) m.put("description", description);
        return m;
    }
}
