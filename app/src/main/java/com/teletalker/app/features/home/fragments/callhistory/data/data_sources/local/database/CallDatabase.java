package com.teletalker.app.features.home.fragments.callhistory.data.data_sources.local.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.teletalker.app.features.home.fragments.callhistory.data.data_sources.local.dao.CallDao;
import com.teletalker.app.features.home.fragments.callhistory.data.models.CallEntity;

@Database(entities = {CallEntity.class}, version = 1, exportSchema = false)
public abstract class CallDatabase extends RoomDatabase {
    private static CallDatabase instance;

    public abstract CallDao callDao();

    public static synchronized CallDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                            CallDatabase.class, "call_database")
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}
