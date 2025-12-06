package com.teletalker.app.features.home.fragments.calender.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.teletalker.app.features.home.fragments.calender.data.data_sources.local.ScheduledCallDao;
import com.teletalker.app.features.home.fragments.calender.data.data_sources.local.ScheduledCallDatabase;
import com.teletalker.app.features.home.fragments.calender.data.models.ScheduledCall;

import java.util.List;

public class ScheduledCallRepository {

    private ScheduledCallDao scheduledCallDao;
    private LiveData<List<ScheduledCall>> allScheduledCalls;

    public ScheduledCallRepository(Application application) {
        ScheduledCallDatabase database = ScheduledCallDatabase.getInstance(application);
        scheduledCallDao = database.scheduledCallDao();
        allScheduledCalls = scheduledCallDao.getAllScheduledCalls();
    }

    // Insert
    public void insert(ScheduledCall scheduledCall) {
        scheduledCallDao.insert(scheduledCall);
    }

    // Update
    public void update(ScheduledCall scheduledCall) {
        scheduledCallDao.update(scheduledCall);
    }

    // Delete
    public void delete(ScheduledCall scheduledCall) {
        scheduledCallDao.delete(scheduledCall);
    }

    // Delete all
    public void deleteAllScheduledCalls() {
        scheduledCallDao.deleteAllScheduledCalls();
    }

    // Get all scheduled calls
    public LiveData<List<ScheduledCall>> getAllScheduledCalls() {
        return allScheduledCalls;
    }

    // Get calls by status
    public LiveData<List<ScheduledCall>> getCallsByStatus(String status) {
        return scheduledCallDao.getCallsByStatus(status);
    }

    // Get calls by date range
    public LiveData<List<ScheduledCall>> getCallsByDateRange(long startTime, long endTime) {
        return scheduledCallDao.getCallsByDateRange(startTime, endTime);
    }

    // Get call by ID
//    public LiveData<ScheduledCall> getCallById(long callId) {
//        return scheduledCallDao.getCallById(callId);
//    }

    // Get pending calls that are due
    public List<ScheduledCall> getPendingCallsDue(long currentTime) {
        return scheduledCallDao.getPendingCallsDue(currentTime);
    }

    // Update call status
    public void updateCallStatus(long callId, String status) {
        scheduledCallDao.updateCallStatus(callId, status);
    }
}