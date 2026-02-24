package com.example.ussdapp;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Room Database abstract class for USSD transaction records.
 * Implements the singleton pattern for database access.
 */
@Database(entities = {TransactionRecord.class}, version = 1, exportSchema = false)
public abstract class UssdDatabase extends RoomDatabase {

    private static UssdDatabase instance;
    private static final Object LOCK = new Object();
    private static final String DATABASE_NAME = "ussd_transaction_database";

    /**
     * Get reference to the TransactionRecordDao.
     */
    public abstract TransactionRecordDao transactionRecordDao();

    /**
     * Get singleton instance of the database.
     * Thread-safe implementation using synchronized lock.
     */
    public static UssdDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = buildDatabase(context);
                }
            }
        }
        return instance;
    }

    /**
     * Build the database with configuration.
     */
    private static UssdDatabase buildDatabase(Context context) {
        return Room.databaseBuilder(
                context.getApplicationContext(),
                UssdDatabase.class,
                DATABASE_NAME
        )
                .allowMainThreadQueries() // For simplicity; use background threads in production
                .addCallback(new RoomDatabase.Callback() {
                    @Override
                    public void onCreate(SupportSQLiteDatabase db) {
                        super.onCreate(db);
                        // Initialize database if needed
                    }
                })
                .build();
    }

    /**
     * Destroy the database instance (useful for testing).
     */
    public static void destroyInstance() {
        synchronized (LOCK) {
            if (instance != null && instance.isOpen()) {
                instance.close();
            }
            instance = null;
        }
    }
}
