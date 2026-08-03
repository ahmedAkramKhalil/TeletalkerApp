package com.teletalker.app.features.home.fragments.calender;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.teletalker.app.R;
import com.teletalker.app.databinding.FragmentCalenderBinding;
import com.teletalker.app.features.home.fragments.calender.data.models.ScheduledCall;
import com.teletalker.app.features.home.fragments.calender.ui.CalendarMonthView;
import com.teletalker.app.features.home.fragments.calender.ui.adapter.ScheduledCallsAdapter;
import com.teletalker.app.features.home.fragments.calender.ui.dialog.ScheduleCallBottomSheet;
import com.teletalker.app.features.home.fragments.settings.SubscriptionPlanActivity;
import com.teletalker.app.utils.SubscriptionManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CalenderFragment extends Fragment  implements SubscriptionManager.SubscriptionListener{

    private FragmentCalenderBinding binding;
    private CalenderViewModel viewModel;
    private ScheduledCallsAdapter adapter;

    private CalendarMonthView calendarView;
    private RecyclerView rvScheduledCalls;
    private LinearLayout emptyStateLayout;
    private TextView tvScheduledCallsCount;
    private FloatingActionButton fabAddScheduledCall;
    private SubscriptionManager subscriptionManager;

    private long selectedDate = System.currentTimeMillis();

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(CalenderViewModel.class);

        binding = FragmentCalenderBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        subscriptionManager = SubscriptionManager.getInstance(requireContext());
        subscriptionManager.addListener(this);
        checkAccess();

        initViews(root);
        setupRecyclerView();
        setupCalendar();
        setupFAB();
        observeViewModel();

        return root;
    }
    private void checkAccess() {
        if (subscriptionManager.isStandard()) {
            // Standard - show calendar
            showCalendar();
        } else {
            // Lite - show upgrade prompt
            showUpgradePrompt();
        }
    }

    private void showCalendar() {
        // Show your calendar UI
        if (binding.calendarContent != null) {
            binding.calendarContent.setVisibility(View.VISIBLE);
        }
        if (binding.upgradeLayout != null) {
            binding.upgradeLayout.setVisibility(View.GONE);
        }
    }

    private void showUpgradePrompt() {
        // Hide calendar, show upgrade prompt
        if (binding.calendarContent != null) {
            binding.calendarContent.setVisibility(View.GONE);
        }
        if (binding.upgradeLayout != null) {
            binding.upgradeLayout.setVisibility(View.VISIBLE);
        }

        // Set click listener to upgrade
        if (binding.btnUpgrade != null) {
            binding.btnUpgrade.setOnClickListener(v -> openSubscriptionPlan());
        }

        // Or if no upgrade layout, redirect immediately
        // openSubscriptionPlan();
    }

    private void openSubscriptionPlan() {
        Intent intent = new Intent(requireContext(), SubscriptionPlanActivity.class);
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        checkAccess();
    }

    @Override
    public void onPlanChanged(String newPlan) {
        if (isAdded()) {
            requireActivity().runOnUiThread(this::checkAccess);
        }
    }

    private void initViews(View root) {
        calendarView = root.findViewById(R.id.calendarView);
        rvScheduledCalls = root.findViewById(R.id.rvScheduledCalls);
        emptyStateLayout = root.findViewById(R.id.emptyStateLayout);
        tvScheduledCallsCount = root.findViewById(R.id.tvScheduledCallsCount);
        fabAddScheduledCall = root.findViewById(R.id.fabAddScheduledCall);
    }

    private void setupRecyclerView() {
        adapter = new ScheduledCallsAdapter();
        rvScheduledCalls.setLayoutManager(new LinearLayoutManager(getContext()));
        rvScheduledCalls.setAdapter(adapter);

        adapter.setOnScheduledCallClickListener(new ScheduledCallsAdapter.OnScheduledCallClickListener() {
            @Override
            public void onCallClick(ScheduledCall call) {
                showCallDetails(call);
            }

            @Override
            public void onEditClick(ScheduledCall call) {
                showEditDialog(call);
            }

            @Override
            public void onDeleteClick(ScheduledCall call) {
                confirmDelete(call);
            }
        });
    }

    private void setupCalendar() {
        // Tapping a day filters the list to that day's calls (the event dots make
        // days scannable). Scheduling a new call is on the FAB.
        calendarView.setOnDateSelectedListener(dayStart -> {
            selectedDate = dayStart;
            viewModel.filterCallsByDate(selectedDate);
        });
    }

    /** Map each scheduled call onto its day as a colored event dot for the calendar. */
    private Map<String, List<Integer>> buildEventDots(List<ScheduledCall> calls) {
        Map<String, List<Integer>> map = new HashMap<>();
        if (calls == null) return map;
        for (ScheduledCall call : calls) {
            String key = CalendarMonthView.dayKey(call.getScheduledDateTime());
            List<Integer> list = map.get(key);
            if (list == null) { list = new ArrayList<>(); map.put(key, list); }
            list.add(statusDotColor(call.getStatus()));
        }
        return map;
    }

    private int statusDotColor(String status) {
        if (status == null) return 0xFF93A0B4;
        switch (status.toLowerCase()) {
            case "completed": return 0xFF2FD07E; // green
            case "pending":
            case "in_progress":
            case "scheduled": return 0xFFF5B44C; // amber
            case "failed":
            case "no_answer": return 0xFFFF5C68; // red
            case "cancelled": return 0xFF5D6B80; // grey
            default: return 0xFF93A0B4;
        }
    }

    private static final String SCHEDULE_SHEET_TAG = "ScheduleCallBottomSheet";

    private void showScheduleDialogForDate(long dateMillis) {
        // Guard against rapid double-taps stacking two sheets (IllegalStateException).
        if (getChildFragmentManager().findFragmentByTag(SCHEDULE_SHEET_TAG) != null) return;
        ScheduleCallBottomSheet bottomSheet = ScheduleCallBottomSheet.newInstanceForDate(dateMillis);
        bottomSheet.setOnScheduleCallListener(scheduledCall -> viewModel.insertScheduledCall(scheduledCall));
        bottomSheet.show(getChildFragmentManager(), SCHEDULE_SHEET_TAG);
    }

    private void setupFAB() {
        fabAddScheduledCall.setOnClickListener(v -> showScheduleDialog());
    }

    private void observeViewModel() {
        // Observe all scheduled calls
        viewModel.getAllScheduledCalls().observe(getViewLifecycleOwner(), scheduledCalls -> {
            // Paint event dots on the calendar for every day that has a call.
            calendarView.setEvents(buildEventDots(scheduledCalls));
            if (scheduledCalls != null && !scheduledCalls.isEmpty()) {
                adapter.submitList(scheduledCalls);
                rvScheduledCalls.setVisibility(View.VISIBLE);
                emptyStateLayout.setVisibility(View.GONE);
                tvScheduledCallsCount.setText(String.valueOf(scheduledCalls.size()));
            } else {
                rvScheduledCalls.setVisibility(View.GONE);
                emptyStateLayout.setVisibility(View.VISIBLE);
                tvScheduledCallsCount.setText("0");
            }
        });

        // Observe filtered calls (by selected date)
        viewModel.getFilteredCalls().observe(getViewLifecycleOwner(), filteredCalls -> {
            if (filteredCalls != null) {
                adapter.submitList(filteredCalls);
            }
        });

        // ✅ FIX: Observe operation status and consume it to prevent re-showing
        viewModel.getOperationStatus().observe(getViewLifecycleOwner(), status -> {
            if (status != null && !status.isEmpty()) {
                Toast.makeText(getContext(), status, Toast.LENGTH_SHORT).show();
                // Clear the status after showing to prevent re-showing on config change
                viewModel.clearOperationStatus();
            }
        });
    }

    private void showScheduleDialog() {
        ScheduleCallBottomSheet bottomSheet = ScheduleCallBottomSheet.newInstance();
        bottomSheet.setOnScheduleCallListener(scheduledCall -> {
            viewModel.insertScheduledCall(scheduledCall);
            // ✅ REMOVED: Duplicate toast (already shown in BottomSheet)
        });
        bottomSheet.show(getChildFragmentManager(), "ScheduleCallBottomSheet");
    }

    private void showEditDialog(ScheduledCall call) {
        ScheduleCallBottomSheet bottomSheet = ScheduleCallBottomSheet.newInstance(call);
        bottomSheet.setOnScheduleCallListener(scheduledCall -> {
            viewModel.updateScheduledCall(scheduledCall);
            // ✅ REMOVED: Duplicate toast (already shown in BottomSheet)
        });
        bottomSheet.show(getChildFragmentManager(), "ScheduleCallBottomSheet");
    }

    private void showCallDetails(ScheduledCall call) {
        // TODO: Show detailed view in a dialog or navigate to details screen
        Toast.makeText(getContext(),
                "Call to: " + call.getContactName() + "\n" + call.getPhoneNumber(),
                Toast.LENGTH_SHORT).show();
    }

    private void confirmDelete(ScheduledCall call) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Delete Scheduled Call")
                .setMessage("Are you sure you want to delete this scheduled call to " +
                        call.getContactName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    viewModel.deleteScheduledCall(call);
                    Toast.makeText(getContext(), "Call deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}