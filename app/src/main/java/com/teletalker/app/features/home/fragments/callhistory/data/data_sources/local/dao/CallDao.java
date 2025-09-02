package com.teletalker.app.features.home.fragments.callhistory.data.data_sources.local.dao;

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

    @Query("SELECT * FROM calls ORDER BY callTime DESC")
    List<CallEntity> getAllCalls();

    @Delete
    void deleteCall(CallEntity call);

    @Query("SELECT * FROM calls ORDER BY id DESC LIMIT 2")
    List<CallEntity> getLastTwoCallRecords();


}