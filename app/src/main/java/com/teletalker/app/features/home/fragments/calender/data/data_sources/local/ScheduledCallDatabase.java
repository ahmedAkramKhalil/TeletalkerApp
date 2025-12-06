package com.teletalker.app.features.home.fragments.calender.data.data_sources.local;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.teletalker.app.features.home.fragments.calender.data.models.ScheduledCall;

/**
 * Room Database for managing scheduled calls.
 *
 * Version History:
 * - v1: Initial database with basic scheduled call fields
 * - v2: Added purpose, actual call tracking fields (actualCallStartTime, actualCallEndTime, actualCallDurationSeconds)
 */
@Database(
        entities = {ScheduledCall.class},
        version = 2,
        exportSchema = false
)
public abstract class ScheduledCallDatabase extends RoomDatabase {

    private static final String TAG = "ScheduledCallDB";
    private static final String DATABASE_NAME = "scheduled_calls_database";

    private static volatile ScheduledCallDatabase instance;

    /**
     * Get the DAO for scheduled call operations
     */
    public abstract ScheduledCallDao scheduledCallDao();

    /**
     * Get database instance (thread-safe singleton)
     */
    public static ScheduledCallDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (ScheduledCallDatabase.class) {
                if (instance == null) {
                    Log.d(TAG, "Creating ScheduledCallDatabase instance");
                    instance = buildDatabase(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * Build the database with migrations
     */
    private static ScheduledCallDatabase buildDatabase(Context context) {
        return Room.databaseBuilder(
                        context,
                        ScheduledCallDatabase.class,
                        DATABASE_NAME
                )
                .addMigrations(MIGRATION_1_2)
                .addCallback(DATABASE_CALLBACK)
                .build();
    }

    /**
     * Migration from version 1 to version 2
     * Adds new fields for call purpose and actual call tracking
     */
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Log.d(TAG, "Migrating database from version 1 to 2");

            try {
                // Add new columns with default values
                database.execSQL("ALTER TABLE scheduled_calls ADD COLUMN purpose TEXT");
                database.execSQL("ALTER TABLE scheduled_calls ADD COLUMN actualCallStartTime INTEGER NOT NULL DEFAULT 0");
                database.execSQL("ALTER TABLE scheduled_calls ADD COLUMN actualCallEndTime INTEGER NOT NULL DEFAULT 0");
                database.execSQL("ALTER TABLE scheduled_calls ADD COLUMN actualCallDurationSeconds INTEGER NOT NULL DEFAULT 0");

                Log.d(TAG, "✅ Database migration 1->2 completed successfully");

            } catch (Exception e) {
                Log.e(TAG, "❌ Error during migration 1->2: " + e.getMessage(), e);
                throw e; // Re-throw to trigger fallback if needed
            }
        }
    };

    /**
     * Database callback for onCreate and onOpen events
     */
    private static final RoomDatabase.Callback DATABASE_CALLBACK = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            Log.d(TAG, "✅ ScheduledCallDatabase created (version " + db.getVersion() + ")");
        }

        @Override
        public void onOpen(@NonNull SupportSQLiteDatabase db) {
            super.onOpen(db);
            Log.d(TAG, "📂 ScheduledCallDatabase opened (version " + db.getVersion() + ")");
        }
    };

    /**
     * For testing purposes - clear the instance
     * ⚠️ WARNING: Only use in tests!
     */
    public static void destroyInstance() {
        if (instance != null) {
            if (instance.isOpen()) {
                instance.close();
            }
            instance = null;
            Log.d(TAG, "Database instance destroyed");
        }
    }

    /**
     * Check if database is open
     */
    public boolean isDatabaseOpen() {
        return isOpen();
    }

    /**
     * Get database version
     */
    public int getDatabaseVersion() {
        return getOpenHelper().getReadableDatabase().getVersion();
    }

    /**
     * Perform database health check
     */
    public boolean performHealthCheck() {
        try {
            // Try a simple query to verify database is accessible
            getOpenHelper().getReadableDatabase().query("SELECT 1");
            Log.d(TAG, "✅ Database health check passed");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "❌ Database health check failed: " + e.getMessage(), e);
            return false;
        }
    }
}