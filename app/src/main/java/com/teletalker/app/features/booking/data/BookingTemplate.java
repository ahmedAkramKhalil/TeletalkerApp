package com.teletalker.app.features.booking.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Catalog of public booking-page templates we ship on hosting.
 * Each ID matches a file under /public/templates/<id>.html on
 * teletalker-21348.web.app, and a thumbnail at
 * /public/templates/thumbs/<id>.png.
 *
 * Keep this list in sync with KNOWN_TEMPLATES inside book-loader.html
 * and the `templates` array in index.html.
 */
public final class BookingTemplate {

    public final String id;
    public final String name;
    public final String tagline;

    private BookingTemplate(String id, String name, String tagline) {
        this.id = id;
        this.name = name;
        this.tagline = tagline;
    }

    public String thumbnailUrl() {
        return "https://teletalker-21348.web.app/templates/thumbs/" + id + ".png";
    }

    public String previewUrl() {
        return "https://teletalker-21348.web.app/templates/" + previewFile();
    }

    /** Maps the canonical id to the actual filename on hosting. */
    private String previewFile() {
        if ("airbnb-booking".equals(id)) return "AirBnB_Booking.html";
        return id + ".html";
    }

    public static final List<BookingTemplate> ALL = Collections.unmodifiableList(Arrays.asList(
            new BookingTemplate("dental-clinic",      "Dental Clinic",          "Multi-step patient form, services & dentist picker"),
            new BookingTemplate("general-clinic",     "General Clinic",         "Quick appointment flow for walk-in clinics"),
            new BookingTemplate("clinic-appointment", "Clinic Appointment",     "Streamlined appointment booking"),
            new BookingTemplate("barber",             "Barber Shop",            "Service + barber + time slot"),
            new BookingTemplate("salon-spa",          "Salon & Spa",            "Treatment menu with stylist picker"),
            new BookingTemplate("salon-massage",      "Salon & Massage",        "Combined service catalog"),
            new BookingTemplate("massage-wellness",   "Massage & Wellness",     "Therapy-focused booking flow"),
            new BookingTemplate("pet-care",           "Pet Care",               "Grooming, vet, daycare options"),
            new BookingTemplate("daycare-center",     "Daycare Center",         "Drop-off scheduling for parents"),
            new BookingTemplate("cleaning-services",  "Cleaning Services",      "Home or office cleaning slots"),
            new BookingTemplate("auto-repair",        "Auto Repair",            "Vehicle + service picker"),
            new BookingTemplate("gadget-repair",      "Gadget Repair",          "Device + issue + drop-off time"),
            new BookingTemplate("swimming-pool",      "Swimming Pool",          "Lane + duration booking"),
            new BookingTemplate("events-venue",       "Events Venue",           "Date range + guest count"),
            new BookingTemplate("resort-stay",        "Resort Stay",            "Check-in / check-out with rooms"),
            new BookingTemplate("hotel-rental",       "Hotel Rental",           "Standard hotel booking"),
            new BookingTemplate("condo-reservation",  "Condo Reservation",      "Short-term condo rental"),
            new BookingTemplate("airbnb-rental",      "Airbnb Rental",          "Date range + guest count"),
            new BookingTemplate("airbnb-booking",     "Airbnb Booking",         "Premium airbnb-style flow")
    ));

    public static BookingTemplate findById(String id) {
        if (id == null) return ALL.get(0);
        for (BookingTemplate t : ALL) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return ALL.get(0);
    }
}
