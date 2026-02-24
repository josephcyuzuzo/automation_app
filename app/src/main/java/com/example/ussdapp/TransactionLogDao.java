package com.example.ussdapp;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

/**
 * Data Access Object for TransactionLog.
 */
@Dao
public interface TransactionLogDao {

    @Insert
    void insert(TransactionLog log);

    @Query("SELECT * FROM transaction_logs ORDER BY timestamp DESC")
    List<TransactionLog> getAllLogs();

    @Query("SELECT * FROM transaction_logs WHERE type = :type ORDER BY timestamp DESC")
    List<TransactionLog> getLogsByType(String type);

    @Query("DELETE FROM transaction_logs")
    void deleteAllLogs();
}
