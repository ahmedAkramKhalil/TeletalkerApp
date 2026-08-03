package com.teletalker.app.features.home.fragments.calender.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.teletalker.app.R;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lightweight month calendar with per-day event dots — a self-contained
 * replacement for the stock {@link android.widget.CalendarView} (which can't
 * show which days have scheduled calls). No third-party dependency.
 *
 * Feed it events via {@link #setEvents(Map)} (key = {@link #dayKey(long)},
 * value = list of dot colors) and listen for taps via
 * {@link #setOnDateSelectedListener(OnDateSelectedListener)}.
 */
public class CalendarMonthView extends LinearLayout {

    public interface OnDateSelectedListener {
        void onDateSelected(long dayStartMillis);
    }

    private static final String[] WEEKDAYS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"};

    private final Calendar month = Calendar.getInstance();   // any day in the shown month
    private final Calendar selected = Calendar.getInstance(); // selected day
    private Map<String, List<Integer>> events = new HashMap<>();
    private OnDateSelectedListener listener;

    private TextView title;
    private LinearLayout grid;

    private int cHi, cMid, cLo, cBrand, cSurface700, cLine;

    public CalendarMonthView(Context c) { super(c); init(c); }
    public CalendarMonthView(Context c, AttributeSet a) { super(c, a); init(c); }

    private void init(Context c) {
        setOrientation(VERTICAL);
        cHi = ContextCompat.getColor(c, R.color.cal_text_hi);
        cMid = ContextCompat.getColor(c, R.color.cal_text_mid);
        cLo = ContextCompat.getColor(c, R.color.cal_text_lo);
        cBrand = ContextCompat.getColor(c, R.color.cal_brand);
        cSurface700 = ContextCompat.getColor(c, R.color.cal_surface_700);
        cLine = ContextCompat.getColor(c, R.color.cal_line);

        addView(buildHeader(c));
        addView(buildWeekdayRow(c));
        grid = new LinearLayout(c);
        grid.setOrientation(VERTICAL);
        addView(grid, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        selected.setTimeInMillis(System.currentTimeMillis());
        render();
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private LinearLayout buildHeader(Context c) {
        LinearLayout h = new LinearLayout(c);
        h.setOrientation(HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(4), dp(2), dp(4), dp(10));

        title = new TextView(c);
        title.setTextColor(cHi);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        LayoutParams tp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        h.addView(title, tp);

        h.addView(navChip(c, "Today", v -> {
            month.setTimeInMillis(System.currentTimeMillis());
            selected.setTimeInMillis(System.currentTimeMillis());
            render();
            notifySelected();
        }));
        h.addView(navChip(c, "‹", v -> { month.add(Calendar.MONTH, -1); render(); }));
        h.addView(navChip(c, "›", v -> { month.add(Calendar.MONTH, 1); render(); }));
        return h;
    }

    private TextView navChip(Context c, String text, OnClickListener click) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(text.length() > 1 ? cBrand : cHi);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, text.length() > 1 ? 12.5f : 17);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(dp(34));
        t.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(11));
        bg.setColor(cSurface700);
        bg.setStroke(dp(1), cLine);
        t.setBackground(bg);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(6));
        t.setLayoutParams(lp);
        t.setOnClickListener(click);
        return t;
    }

    private LinearLayout buildWeekdayRow(Context c) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setPadding(0, 0, 0, dp(6));
        for (String w : WEEKDAYS) {
            TextView t = new TextView(c);
            t.setText(w);
            t.setTextColor(cLo);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            t.setGravity(Gravity.CENTER);
            t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
            row.addView(t, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        }
        return row;
    }

    /** Normalized key for a day (local). */
    public static String dayKey(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        return cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.MONTH) + "-" + cal.get(Calendar.DAY_OF_MONTH);
    }

    public void setEvents(Map<String, List<Integer>> events) {
        this.events = (events != null) ? events : new HashMap<>();
        render();
    }

    public void setOnDateSelectedListener(OnDateSelectedListener l) { this.listener = l; }

    public long getSelectedDayStart() {
        Calendar d = (Calendar) selected.clone();
        d.set(Calendar.HOUR_OF_DAY, 0); d.set(Calendar.MINUTE, 0);
        d.set(Calendar.SECOND, 0); d.set(Calendar.MILLISECOND, 0);
        return d.getTimeInMillis();
    }

    private void notifySelected() { if (listener != null) listener.onDateSelected(getSelectedDayStart()); }

    private void render() {
        title.setText(MONTHS[month.get(Calendar.MONTH)] + " " + month.get(Calendar.YEAR));
        grid.removeAllViews();

        Calendar first = (Calendar) month.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        // Monday-first offset: Calendar.MONDAY=2 … SUNDAY=1 → column 0..6
        int dow = first.get(Calendar.DAY_OF_WEEK); // 1=Sun..7=Sat
        int lead = (dow + 5) % 7; // Mon=0
        int daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH);

        Context c = getContext();
        int day = 1;
        for (int week = 0; week < 6 && day <= daysInMonth; week++) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(HORIZONTAL);
            for (int col = 0; col < 7; col++) {
                LayoutParams cellLp = new LayoutParams(0, dp(46), 1f);
                if (week == 0 && col < lead) {
                    View spacer = new View(c);
                    row.addView(spacer, cellLp);
                } else if (day <= daysInMonth) {
                    row.addView(buildCell(c, day), cellLp);
                    day++;
                } else {
                    row.addView(new View(c), cellLp);
                }
            }
            grid.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
    }

    private View buildCell(Context c, int day) {
        final Calendar cellCal = (Calendar) month.clone();
        cellCal.set(Calendar.DAY_OF_MONTH, day);
        cellCal.set(Calendar.HOUR_OF_DAY, 0); cellCal.set(Calendar.MINUTE, 0);
        cellCal.set(Calendar.SECOND, 0); cellCal.set(Calendar.MILLISECOND, 0);

        boolean isToday = sameDay(cellCal, Calendar.getInstance());
        boolean isSelected = sameDay(cellCal, selected);

        LinearLayout cell = new LinearLayout(c);
        cell.setOrientation(VERTICAL);
        cell.setGravity(Gravity.CENTER);
        int m = dp(2);
        cell.setPadding(0, dp(4), 0, dp(4));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        if (isSelected) {
            bg.setColor(cBrand);
        } else if (isToday) {
            bg.setColor(cSurface700);
            bg.setStroke(dp(1), cBrand);
        } else {
            bg.setColor(Color.TRANSPARENT);
        }
        cell.setBackground(bg);

        TextView num = new TextView(c);
        num.setText(String.valueOf(day));
        num.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        num.setGravity(Gravity.CENTER);
        if (isSelected) num.setTextColor(0xFF04121D);
        else num.setTextColor(cHi);
        num.setTypeface(num.getTypeface(), isSelected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        cell.addView(num);

        // Event dots
        LinearLayout dots = new LinearLayout(c);
        dots.setOrientation(HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        LayoutParams dotsLp = new LayoutParams(LayoutParams.WRAP_CONTENT, dp(6));
        dotsLp.topMargin = dp(3);
        dots.setLayoutParams(dotsLp);
        List<Integer> colors = events.get(dayKey(cellCal.getTimeInMillis()));
        if (colors != null) {
            int shown = 0;
            for (Integer color : colors) {
                if (shown >= 3) break;
                View dot = new View(c);
                GradientDrawable d = new GradientDrawable();
                d.setShape(GradientDrawable.OVAL);
                d.setColor(isSelected ? 0xFF04121D : color);
                dot.setBackground(d);
                LayoutParams lp = new LayoutParams(dp(5), dp(5));
                lp.setMarginStart(shown == 0 ? 0 : dp(3));
                dots.addView(dot, lp);
                shown++;
            }
        }
        cell.addView(dots);

        cell.setOnClickListener(v -> {
            selected.setTimeInMillis(cellCal.getTimeInMillis());
            render();
            notifySelected();
        });
        return cell;
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.MONTH) == b.get(Calendar.MONTH)
                && a.get(Calendar.DAY_OF_MONTH) == b.get(Calendar.DAY_OF_MONTH);
    }
}
