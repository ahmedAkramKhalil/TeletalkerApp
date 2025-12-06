package com.teletalker.app.features.home.fragments.calender;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.teletalker.app.features.home.fragments.calender.data.models.ScheduledCall;
import com.teletalker.app.features.home.fragments.calender.data.repository.ScheduledCallRepository;
import com.teletalker.app.services.scheduler.CallSchedulerManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;

public class CalenderViewModel extends AndroidViewModel {

    private final ScheduledCallRepository repository;
    private final LiveData<List<ScheduledCall>> allScheduledCalls;
    private final MutableLiveData<List<ScheduledCall>> filteredCalls;
    private final MutableLiveData<String> operationStatus;

    private final CallSchedulerManager schedulerManager;

    public CalenderViewModel(@NonNull Application application) {
        super(application);
        repository = new ScheduledCallRepository(application);
        allScheduledCalls = repository.getAllScheduledCalls();
        filteredCalls = new MutableLiveData<>();
        operationStatus = new MutableLiveData<>();
        schedulerManager = new CallSchedulerManager(application);

        // Initialize periodic background checker
        schedulerManager.initializePeriodicCheck();
    }

    // Getters for LiveData
    public LiveData<List<ScheduledCall>> getAllScheduledCalls() {
        return allScheduledCalls;
    }

    public LiveData<List<ScheduledCall>> getFilteredCalls() {
        return filteredCalls;
    }

    public LiveData<String> getOperationStatus() {
        return operationStatus;
    }

    // CRUD Operations
    public void insertScheduledCall(ScheduledCall call) {
        new Thread(() -> {
            try {
                repository.insert(call);
                CallSchedulerManager schedulerManager = new CallSchedulerManager(getApplication());
                schedulerManager.scheduleCall(call);

                operationStatus.postValue("Call scheduled successfully!");
            } catch (Exception e) {
                operationStatus.postValue("Error: " + e.getMessage());
            }
        }).start();
    }

    public void updateScheduledCall(ScheduledCall call) {
        new Thread(() -> {
            try {
                call.setUpdatedAt(System.currentTimeMillis());
                repository.update(call);
                operationStatus.postValue("Call updated successfully!");
            } catch (Exception e) {
                operationStatus.postValue("Error: " + e.getMessage());
            }
        }).start();
    }

    public void deleteScheduledCall(ScheduledCall call) {
        new Thread(() -> {
            try {
                repository.delete(call);
                operationStatus.postValue("Call deleted successfully!");
            } catch (Exception e) {
                operationStatus.postValue("Error: " + e.getMessage());
            }
        }).start();
    }

    // Filter calls by date
    public void filterCallsByDate(long dateTimestamp) {
        List<ScheduledCall> allCalls = allScheduledCalls.getValue();
        if (allCalls == null || allCalls.isEmpty()) {
            filteredCalls.setValue(new ArrayList<>());
            return;
        }

        // Get start and end of selected day
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dateTimestamp);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startOfDay = calendar.getTimeInMillis();

        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        long endOfDay = calendar.getTimeInMillis();

        // Filter calls for the selected day
        List<ScheduledCall> filtered = allCalls.stream()
                .filter(call -> {
                    long scheduledTime = call.getScheduledDateTime();
                    return scheduledTime >= startOfDay && scheduledTime <= endOfDay;
                })
                .collect(Collectors.toList());

        filteredCalls.setValue(filtered);
    }

    // Get upcoming calls (next 7 days)
    public void getUpcomingCalls() {
        List<ScheduledCall> allCalls = allScheduledCalls.getValue();
        if (allCalls == null || allCalls.isEmpty()) {
            filteredCalls.setValue(new ArrayList<>());
            return;
        }

        long now = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 7);
        long nextWeek = calendar.getTimeInMillis();

        List<ScheduledCall> upcoming = allCalls.stream()
                .filter(call -> {
                    long scheduledTime = call.getScheduledDateTime();
                    return scheduledTime >= now && scheduledTime <= nextWeek
                            && call.getStatus().equals("pending");
                })
                .collect(Collectors.toList());

        filteredCalls.setValue(upcoming);
    }

    // Get pending calls only
    public void getPendingCalls() {
        List<ScheduledCall> allCalls = allScheduledCalls.getValue();
        if (allCalls == null || allCalls.isEmpty()) {
            filteredCalls.setValue(new ArrayList<>());
            return;
        }

        List<ScheduledCall> pending = allCalls.stream()
                .filter(call -> call.getStatus().equals("pending"))
                .collect(Collectors.toList());

        filteredCalls.setValue(pending);
    }

    // Mark call as completed
    public void markCallAsCompleted(ScheduledCall call, String callSid) {
        new Thread(() -> {
            try {
                call.setStatus("completed");
                call.setCallSid(callSid);
                call.setUpdatedAt(System.currentTimeMillis());
                repository.update(call);
            } catch (Exception e) {
                operationStatus.postValue("Error updating call status: " + e.getMessage());
            }
        }).start();
    }

    // Mark call as failed
    public void markCallAsFailed(ScheduledCall call, String reason) {
        new Thread(() -> {
            try {
                call.setStatus("failed");
                call.setUpdatedAt(System.currentTimeMillis());
                repository.update(call);
                operationStatus.postValue("Call failed: " + reason);
            } catch (Exception e) {
                operationStatus.postValue("Error updating call status: " + e.getMessage());
            }
        }).start();
    }

    // Cancel a scheduled call
    public void cancelScheduledCall(ScheduledCall call) {
        new Thread(() -> {
            try {
                call.setStatus("cancelled");
                call.setUpdatedAt(System.currentTimeMillis());
                repository.update(call);
                operationStatus.postValue("Call cancelled");
            } catch (Exception e) {
                operationStatus.postValue("Error cancelling call: " + e.getMessage());
            }
        }).start();
    }
}