package com.teletalker.app.features.home.fragments.calender.data.data_sources.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.teletalker.app.features.home.fragments.calender.data.models.ScheduledCall;

import java.util.List;

@Dao
public interface ScheduledCallDao {

    // ============================================================================
    // BASIC CRUD (Keep existing)
    // ============================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ScheduledCall scheduledCall);

    @Update
    void update(ScheduledCall scheduledCall);

    @Delete
    void delete(ScheduledCall scheduledCall);

    @Query("DELETE FROM scheduled_calls")
    void deleteAllScheduledCalls();

    // ============================================================================
    // LIVEDATA QUERIES (Keep existing for UI)
    // ============================================================================

    @Query("SELECT * FROM scheduled_calls ORDER BY scheduledDateTime ASC")
    LiveData<List<ScheduledCall>> getAllScheduledCalls();

    @Query("SELECT * FROM scheduled_calls WHERE status = :status ORDER BY scheduledDateTime ASC")
    LiveData<List<ScheduledCall>> getCallsByStatus(String status);

    @Query("SELECT * FROM scheduled_calls WHERE scheduledDateTime >= :startTime AND scheduledDateTime <= :endTime ORDER BY scheduledDateTime ASC")
    LiveData<List<ScheduledCall>> getCallsByDateRange(long startTime, long endTime);

    // ============================================================================
    // ESSENTIAL QUERIES (For ScheduledCallHelper)
    // ============================================================================

    @Query("SELECT * FROM scheduled_calls WHERE id = :callId LIMIT 1")
    ScheduledCall getCallById(long callId);

    @Query("SELECT * FROM scheduled_calls WHERE scheduledDateTime <= :currentTime AND status = 'pending' ORDER BY scheduledDateTime ASC")
    List<ScheduledCall> getPendingCallsDue(long currentTime);

    @Query("SELECT * FROM scheduled_calls " +
            "WHERE phoneNumber = :phoneNumber " +
            "AND status IN ('pending', 'in_progress') " +
            "AND scheduledDateTime >= :windowStart " +
            "AND scheduledDateTime <= :windowEnd " +
            "ORDER BY scheduledDateTime DESC")
    List<ScheduledCall> getActiveCallsByPhoneNumber(String phoneNumber, long windowStart, long windowEnd);

    @Query("SELECT * FROM scheduled_calls " +
            "WHERE status = 'in_progress' " +
            "ORDER BY actualCallStartTime DESC " +
            "LIMIT 1")
    ScheduledCall getMostRecentInProgressCall();

    @Query("SELECT * FROM scheduled_calls " +
            "WHERE status = 'pending' " +
            "AND scheduledDateTime >= :windowStart " +
            "ORDER BY scheduledDateTime DESC " +
            "LIMIT 1")
    ScheduledCall getMostRecentPendingCall(long windowStart);

    // ============================================================================
    // STATUS UPDATES
    // ============================================================================


    @Query("SELECT COUNT(*) FROM scheduled_calls WHERE status = :status")
    int getCountByStatus(String status);



        @Query("SELECT status FROM scheduled_calls WHERE id = :callId")
        String getCallStatus(long callId);


        /**
         * Update call status
         */
        @Query("UPDATE scheduled_calls SET status = :status WHERE id = :callId")
        void updateCallStatus(long callId, String status);

        /**
         * Get all pending calls (for debugging and rescheduling)
         */
        @Query("SELECT * FROM scheduled_calls WHERE status = 'pending' ORDER BY scheduledDateTime ASC")
        List<ScheduledCall> getAllPendingCalls();

        @Query("UPDATE scheduled_calls SET status = 'missed' WHERE status = 'pending' AND scheduledDateTime < :currentTime")
        void markMissedCalls(long currentTime);

        /**
         * Get count of pending calls (useful for UI)
         */
        @Query("SELECT COUNT(*) FROM scheduled_calls WHERE status = 'pending'")
        int getPendingCallsCount();

        /**
         * Delete old completed/failed calls (cleanup)
         */
        @Query("DELETE FROM scheduled_calls WHERE (status = 'completed' OR status = 'failed') AND scheduledDateTime < :cutoffTime")
        void deleteOldCalls(long cutoffTime);

}