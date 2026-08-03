package com.teletalker.app.features.booking.data;

/** A single open-hour window. Times are "HH:MM" 24-hour. */
public class DayRange {
    public String open;
    public String close;

    public DayRange() {}
    public DayRange(String open, String close) {
        this.open = open;
        this.close = close;
    }
}
