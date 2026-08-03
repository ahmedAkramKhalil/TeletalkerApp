package com.teletalker.app.features.booking;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.Timestamp;
import com.teletalker.app.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mobile bookings dashboard — lists every booking for the signed-in merchant
 * with live status updates via a Firestore snapshot listener. Mirrors the
 * web dashboard so the merchant sees the same outcome pill ("Calling now…",
 * "Confirmed", "Rescheduled by AI: …", etc.) without leaving the phone.
 */
public class BookingsListActivity extends AppCompatActivity {

    private static final String TAG = "BookingsList";

    private RecyclerView list;
    private ProgressBar loading;
    private View emptyState;
    private TextView countTv;

    private final List<BookingRow> rows = new ArrayList<>();
    private BookingsAdapter adapter;
    private ListenerRegistration listenerReg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookings_list);

        Toolbar tb = findViewById(R.id.bookingsToolbar);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.bookings_title);
        }
        tb.setNavigationOnClickListener(v -> finish());

        list      = findViewById(R.id.bookingsList);
        loading   = findViewById(R.id.bookingsLoading);
        emptyState = findViewById(R.id.bookingsEmpty);
        countTv   = findViewById(R.id.bookingsCount);

        adapter = new BookingsAdapter(rows);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        subscribe();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (listenerReg != null) listenerReg.remove();
    }

    /**
     * Real-time Firestore listener. Pulls every booking for this merchant
     * (newest first) and re-renders on each change — so as the device flips
     * status from "calling" → "confirmed" the screen updates instantly.
     */
    private void subscribe() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) {
            Toast.makeText(this, R.string.bookings_signed_out, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        loading.setVisibility(View.VISIBLE);
        listenerReg = FirebaseFirestore.getInstance()
                .collection("bookings")
                .whereEqualTo("merchantId", uid)
                .orderBy("appointmentAt", Query.Direction.DESCENDING)
                .limit(200)
                .addSnapshotListener((snap, err) -> {
                    loading.setVisibility(View.GONE);
                    if (err != null) {
                        Toast.makeText(this,
                                getString(R.string.bookings_load_error, err.getMessage()),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    rows.clear();
                    if (snap != null) {
                        for (QueryDocumentSnapshot d : snap) {
                            rows.add(BookingRow.from(d));
                        }
                    }
                    countTv.setText(getResources()
                            .getQuantityString(R.plurals.bookings_count, rows.size(), rows.size()));
                    emptyState.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                    adapter.notifyDataSetChanged();
                });
    }

    // -----------------------------------------------------------------------
    // Data
    // -----------------------------------------------------------------------

    static final class BookingRow {
        String id;
        String customerName = "";
        String customerPhone = "";
        String serviceName = "";
        long appointmentMs = 0;
        long previousAppointmentMs = 0;
        String status = "scheduled";
        String aiNote = "";

        static BookingRow from(QueryDocumentSnapshot d) {
            BookingRow r = new BookingRow();
            r.id = d.getId();
            r.customerName  = d.getString("customerName");
            r.customerPhone = d.getString("customerPhone");
            r.status        = nz(d.getString("status"), "scheduled");
            r.aiNote        = nz(d.getString("aiNote"), "");

            Object svc = d.get("service");
            if (svc instanceof Map) {
                Object n = ((Map<?, ?>) svc).get("name");
                if (n instanceof String) r.serviceName = (String) n;
            }
            Timestamp t = d.getTimestamp("appointmentAt");
            if (t != null) r.appointmentMs = t.toDate().getTime();
            Timestamp pt = d.getTimestamp("previousAppointmentAt");
            if (pt != null) r.previousAppointmentMs = pt.toDate().getTime();
            return r;
        }
    }

    private static String nz(String s, String fallback) {
        return s == null ? fallback : s;
    }

    // -----------------------------------------------------------------------
    // Adapter
    // -----------------------------------------------------------------------

    static final class BookingsAdapter extends RecyclerView.Adapter<BookingsAdapter.VH> {
        private final List<BookingRow> data;
        private static final SimpleDateFormat WHEN_FMT =
                new SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault());

        BookingsAdapter(List<BookingRow> data) { this.data = data; }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_booking_row, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            BookingRow r = data.get(position);
            h.name.setText(r.customerName == null || r.customerName.isEmpty()
                    ? h.itemView.getContext().getString(R.string.bookings_unknown_customer)
                    : r.customerName);

            String when = r.appointmentMs > 0 ? WHEN_FMT.format(new Date(r.appointmentMs)) : "—";
            String svc = r.serviceName == null ? "" : r.serviceName;
            if (svc.isEmpty()) {
                h.subtitle.setText(when);
            } else {
                h.subtitle.setText(svc + " · " + when);
            }

            // "(was Tue May 12, 7:30 AM)" when rescheduled by AI
            if ("rescheduled".equals(r.status) && r.previousAppointmentMs > 0) {
                String prev = WHEN_FMT.format(new Date(r.previousAppointmentMs));
                h.previousTime.setVisibility(View.VISIBLE);
                h.previousTime.setText(h.itemView.getContext()
                        .getString(R.string.bookings_was_prefix, prev));
            } else {
                h.previousTime.setVisibility(View.GONE);
            }

            // Status pill
            applyStatusPill(h.statusBadge, r.status);

            // AI note pill
            if (r.aiNote != null && !r.aiNote.isEmpty()) {
                h.aiNote.setVisibility(View.VISIBLE);
                h.aiNote.setText(r.aiNote);
                applyNoteTint(h.aiNote, r.status);
            } else {
                h.aiNote.setVisibility(View.GONE);
            }
        }

        @Override public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView name, subtitle, previousTime, statusBadge, aiNote;
            VH(@NonNull View v) {
                super(v);
                name         = v.findViewById(R.id.rowName);
                subtitle     = v.findViewById(R.id.rowSubtitle);
                previousTime = v.findViewById(R.id.rowPreviousTime);
                statusBadge  = v.findViewById(R.id.rowStatus);
                aiNote       = v.findViewById(R.id.rowAiNote);
            }
        }

        private static void applyStatusPill(TextView v, String status) {
            int label;
            int bg;
            switch (status == null ? "" : status) {
                case "confirmed":  label = R.string.bookings_status_confirmed;
                                   bg = R.drawable.pill_success; break;
                case "called":     label = R.string.bookings_status_called;
                                   bg = R.drawable.pill_success; break;
                case "calling":    label = R.string.bookings_status_calling;
                                   bg = R.drawable.pill_warning; break;
                case "rescheduled":label = R.string.bookings_status_rescheduled;
                                   bg = R.drawable.pill_info;    break;
                case "no_answer":  label = R.string.bookings_status_no_answer;
                                   bg = R.drawable.pill_warning; break;
                case "declined":   label = R.string.bookings_status_declined;
                                   bg = R.drawable.pill_danger;  break;
                case "failed":     label = R.string.bookings_status_failed;
                                   bg = R.drawable.pill_danger;  break;
                case "pending":    label = R.string.bookings_status_pending;
                                   bg = R.drawable.pill_muted;   break;
                case "scheduled":
                default:           label = R.string.bookings_status_scheduled;
                                   bg = R.drawable.pill_info;    break;
            }
            v.setText(label);
            v.setBackgroundResource(bg);
        }

        private static void applyNoteTint(TextView v, String status) {
            int bg;
            switch (status == null ? "" : status) {
                case "confirmed":   bg = R.drawable.note_bg_success;  break;
                case "rescheduled": bg = R.drawable.note_bg_info;     break;
                case "declined":    bg = R.drawable.note_bg_danger;   break;
                case "no_answer":   bg = R.drawable.note_bg_warning;  break;
                case "failed":      bg = R.drawable.note_bg_danger;   break;
                default:            bg = R.drawable.note_bg_muted;    break;
            }
            v.setBackgroundResource(bg);
        }
    }
}
