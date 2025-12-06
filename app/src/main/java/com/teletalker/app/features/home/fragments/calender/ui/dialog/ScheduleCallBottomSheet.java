package com.teletalker.app.features.home.fragments.calender.ui.dialog;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.teletalker.app.R;
import com.teletalker.app.features.home.fragments.calender.data.data_sources.local.ScheduledCallDatabase;
import com.teletalker.app.features.home.fragments.calender.data.models.ScheduledCall;
import com.teletalker.app.services.scheduler.CallSchedulerManager;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.Executors;

public class ScheduleCallBottomSheet extends BottomSheetDialogFragment {
    private static final String TAG = "ScheduleCallBottomSheet";

    private TextInputEditText etPhoneNumber, etContactName, etPurpose, etDate, etTime, etConversationNotes;
    private ChipGroup chipGroupDuration;
    private MaterialButton btnCancel, btnSchedule;

    private ScheduledCall existingCall;
    private Calendar selectedDateTime;
    private OnScheduleCallListener listener;

    public interface OnScheduleCallListener {
        void onScheduleCall(ScheduledCall scheduledCall);
    }

    public static ScheduleCallBottomSheet newInstance() {
        return new ScheduleCallBottomSheet();
    }

    public static ScheduleCallBottomSheet newInstance(ScheduledCall call) {
        ScheduleCallBottomSheet sheet = new ScheduleCallBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable("existing_call", (Serializable) call);
        sheet.setArguments(args);
        return sheet;
    }

    public void setOnScheduleCallListener(OnScheduleCallListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new BottomSheetDialog(requireContext(), getTheme());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_schedule_call, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupListeners();
        loadExistingCallIfAny();
    }

    private void initViews(View view) {
        etPhoneNumber = view.findViewById(R.id.etPhoneNumber);
        etContactName = view.findViewById(R.id.etContactName);
        etPurpose = view.findViewById(R.id.etPurpose); // ⭐ ADD THIS
        etDate = view.findViewById(R.id.etDate);
        etTime = view.findViewById(R.id.etTime);
        etConversationNotes = view.findViewById(R.id.etConversationNotes);
        chipGroupDuration = view.findViewById(R.id.chipGroupDuration);
        btnCancel = view.findViewById(R.id.btnCancel);
        btnSchedule = view.findViewById(R.id.btnSchedule);

        selectedDateTime = Calendar.getInstance();
    }

    private void setupListeners() {
        etDate.setOnClickListener(v -> showDatePicker());
        etTime.setOnClickListener(v -> showTimePicker());
        btnCancel.setOnClickListener(v -> dismiss());
        btnSchedule.setOnClickListener(v -> scheduleCall());
    }

    private void loadExistingCallIfAny() {
        if (getArguments() != null) {
            existingCall = (ScheduledCall) getArguments().getSerializable("existing_call");
            if (existingCall != null) {
                populateFields(existingCall);
                btnSchedule.setText("Update Call");
            }
        }
    }

    private void populateFields(ScheduledCall call) {
        etPhoneNumber.setText(call.getPhoneNumber());
        etContactName.setText(call.getContactName());
        etPurpose.setText(call.getPurpose()); // ⭐ ADD THIS
        etConversationNotes.setText(call.getConversationNotes());

        selectedDateTime.setTimeInMillis(call.getScheduledDateTime());
        updateDateTimeDisplay();

        selectDurationChip(call.getDurationMinutes());
    }

    private void showDatePicker() {
        Calendar now = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    selectedDateTime.set(Calendar.YEAR, year);
                    selectedDateTime.set(Calendar.MONTH, month);
                    selectedDateTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateDateTimeDisplay();
                },
                selectedDateTime.get(Calendar.YEAR),
                selectedDateTime.get(Calendar.MONTH),
                selectedDateTime.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.getDatePicker().setMinDate(now.getTimeInMillis());
        datePickerDialog.show();
    }

    private void showTimePicker() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(
                requireContext(),
                (view, hourOfDay, minute) -> {
                    selectedDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    selectedDateTime.set(Calendar.MINUTE, minute);
                    updateDateTimeDisplay();
                },
                selectedDateTime.get(Calendar.HOUR_OF_DAY),
                selectedDateTime.get(Calendar.MINUTE),
                false
        );
        timePickerDialog.show();
    }

    private void updateDateTimeDisplay() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

        etDate.setText(dateFormat.format(selectedDateTime.getTime()));
        etTime.setText(timeFormat.format(selectedDateTime.getTime()));
    }

    private void selectDurationChip(int minutes) {
        int chipId;
        switch (minutes) {
            case 5: chipId = R.id.chip5Min; break;
            case 10: chipId = R.id.chip10Min; break;
            case 15: chipId = R.id.chip15Min; break;
            case 30: chipId = R.id.chip30Min; break;
            case 60: chipId = R.id.chip60Min; break;
            default: chipId = R.id.chip10Min; break;
        }
        chipGroupDuration.check(chipId);
    }

    private int getSelectedDuration() {
        int selectedChipId = chipGroupDuration.getCheckedChipId();
        if (selectedChipId == R.id.chip5Min) return 5;
        if (selectedChipId == R.id.chip15Min) return 15;
        if (selectedChipId == R.id.chip30Min) return 30;
        if (selectedChipId == R.id.chip60Min) return 60;
        return 10;
    }

    private void scheduleCall() {
        // Validate inputs
        String phoneNumber = etPhoneNumber.getText().toString().trim();
        String contactName = etContactName.getText().toString().trim();
        String purpose = etPurpose.getText().toString().trim(); // ⭐ ADD THIS
        String notes = etConversationNotes.getText().toString().trim();

        // Validation
        if (phoneNumber.isEmpty()) {
            Toast.makeText(getContext(), "Please enter a phone number", Toast.LENGTH_SHORT).show();
            return;
        }

        if (purpose.isEmpty()) {
            Toast.makeText(getContext(), "Please enter the call purpose", Toast.LENGTH_SHORT).show();
            return;
        }

        if (notes.isEmpty()) {
            Toast.makeText(getContext(), "Please provide AI conversation instructions", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedDateTime.getTimeInMillis() <= System.currentTimeMillis()) {
            Toast.makeText(getContext(), "Please select a future date and time", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create or update ScheduledCall
        ScheduledCall scheduledCall = existingCall != null ? existingCall : new ScheduledCall();
        scheduledCall.setPhoneNumber(phoneNumber);
        scheduledCall.setContactName(contactName.isEmpty() ? "Unknown" : contactName);
        scheduledCall.setPurpose(purpose); // ⭐ ADD THIS
        scheduledCall.setScheduledDateTime(selectedDateTime.getTimeInMillis());
        scheduledCall.setDurationMinutes(getSelectedDuration());
        scheduledCall.setConversationNotes(notes);
        scheduledCall.setStatus("pending");
        scheduledCall.setUpdatedAt(System.currentTimeMillis());

        // ⭐ SAVE TO DATABASE
        saveToDatabase(scheduledCall);
    }

    private void saveToDatabase(ScheduledCall scheduledCall) {
        // Disable button to prevent double-tap
        btnSchedule.setEnabled(false);
        btnSchedule.setText("Saving...");

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                ScheduledCallDatabase database = ScheduledCallDatabase.getInstance(requireContext());

                if (existingCall != null) {
                    // Update existing call
                    database.scheduledCallDao().update(scheduledCall);
                    Log.d(TAG, "✅ Updated scheduled call: " + scheduledCall.getId());
                } else {
                    // Insert new call
                    long callId = database.scheduledCallDao().insert(scheduledCall);
                    scheduledCall.setId(callId);

                    Log.d(TAG, "✅ Saved new scheduled call with ID: " + callId);
                }

                if (getContext() != null) {
                    CallSchedulerManager schedulerManager = new CallSchedulerManager(getContext());
                    schedulerManager.scheduleCall(scheduledCall);
                }


                // Success - notify on main thread
                requireActivity().runOnUiThread(() -> {
                    String message = existingCall != null ? "Call updated" : "Call scheduled";
                    Toast.makeText(getContext(), message + " successfully", Toast.LENGTH_SHORT).show();

                    // Notify listener
                    if (listener != null) {
                        listener.onScheduleCall(scheduledCall);
                    }

                    dismiss();
                });

            } catch (Exception e) {
                Log.e(TAG, "❌ Error saving scheduled call: " + e.getMessage(), e);

                // Error - notify on main thread
                requireActivity().runOnUiThread(() -> {
                    btnSchedule.setEnabled(true);
                    btnSchedule.setText(existingCall != null ? "Update Call" : "Schedule Call");
                    Toast.makeText(getContext(), "Failed to save call: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}