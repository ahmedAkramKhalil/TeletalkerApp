package com.teletalker.app.features.home.fragments.callhistory.data.data_sources.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;

import java.util.List;


@Dao
public interface CallDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCall(CallEntity call);

    /**
     * Returns LiveData so Room emits a new list automatically every time the
     * {@code calls} table changes (insert/update/delete). This fixes the
     * stale-cache bug where new calls didn't appear in the UI because the
     * ViewModel only queried once at construction.
     */
    @Query("SELECT * FROM calls ORDER BY callTime DESC")
    LiveData<List<CallEntity>> getAllCalls();

    @Delete
    void deleteCall(CallEntity call);

    @Query("SELECT * FROM calls ORDER BY id DESC LIMIT 3")
    List<CallEntity> getRecentCallRecords();

    // Home dashboard summary counters
    @Query("SELECT COUNT(*) FROM calls WHERE callType LIKE 'AI Call%'")
    int getAiAnsweredCount();

    @Query("SELECT COUNT(*) FROM calls WHERE duration IS NULL OR duration = ''")
    int getMissedCount();
    @Query("SELECT * FROM calls ORDER BY id DESC LIMIT 2")
    LiveData<List<CallEntity>> getLastTwoCallRecords();


}
