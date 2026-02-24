package com.example.ussdapp;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

/**
 * Data Access Object (DAO) for TransactionRecord.
 * Provides methods to insert, query, and delete transaction records.
 */
@Dao
public interface TransactionRecordDao {

    /**
     * Insert a single transaction record into the database.
     */
    @Insert
    void insert(TransactionRecord record);

    /**
     * Insert multiple transaction records.
     */
    @Insert
    void insertAll(List<TransactionRecord> records);

    /**
     * Fetch all transaction records ordered by timestamp (newest first).
     */
    @Query("SELECT * FROM transaction_records ORDER BY timestamp DESC")
    List<TransactionRecord> getAllRecords();

    /**
     * Fetch records by transaction type (e.g., "BALANCE_CHECK", "TRANSFER").
     */
    @Query("SELECT * FROM transaction_records WHERE type = :type ORDER BY timestamp DESC")
    List<TransactionRecord> getRecordsByType(String type);

    /**
     * Fetch records by status (e.g., "SUCCESS", "FAILED").
     */
    @Query("SELECT * FROM transaction_records WHERE status = :status ORDER BY timestamp DESC")
    List<TransactionRecord> getRecordsByStatus(String status);

    /**
     * Fetch a single record by ID.
     */
    @Query("SELECT * FROM transaction_records WHERE id = :id")
    TransactionRecord getRecordById(int id);

    /**
     * Fetch records within a time range (milliseconds).
     */
    @Query("SELECT * FROM transaction_records WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    List<TransactionRecord> getRecordsByTimeRange(long startTime, long endTime);

    /**
     * Delete a single record.
     */
    @Delete
    void delete(TransactionRecord record);

    /**
     * Delete all records from the database.
     */
    @Query("DELETE FROM transaction_records")
    void deleteAllRecords();

    /**
     * Get the count of all records.
     */
    @Query("SELECT COUNT(*) FROM transaction_records")
    int getRecordCount();

    /**
     * Get the count of records by type.
     */
    @Query("SELECT COUNT(*) FROM transaction_records WHERE type = :type")
    int getRecordCountByType(String type);

    /**
     * Get the last 10 automated transaction logs for dashboard display.
     */
    @Query("SELECT * FROM transaction_records WHERE automation_status IS NOT NULL ORDER BY timestamp DESC LIMIT 10")
    List<TransactionRecord> getRecentAutomatedLogs();

    /**
     * Get automated logs filtered by automation status (SUCCESS/FAILED).
     */
    @Query("SELECT * FROM transaction_records WHERE automation_status = :automationStatus ORDER BY timestamp DESC LIMIT 10")
    List<TransactionRecord> getRecentAutomatedLogsByStatus(String automationStatus);

    /**
     * Get all automated logs (not limited).
     */
    @Query("SELECT * FROM transaction_records WHERE automation_status IS NOT NULL ORDER BY timestamp DESC")
    List<TransactionRecord> getAllAutomatedLogs();

    /**
     * Get count of successful automated actions.
     */
    @Query("SELECT COUNT(*) FROM transaction_records WHERE automation_status = 'SUCCESS'")
    int getSuccessfulAutomationCount();

    /**
     * Get count of failed automated actions.
     */
    @Query("SELECT COUNT(*) FROM transaction_records WHERE automation_status = 'FAILED'")
    int getFailedAutomationCount();

    /**
     * Get total amount transferred via successful automated transfers.
     */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transaction_records WHERE automation_status = 'SUCCESS' AND type = 'TRANSFER'")
    double getTotalTransferAmount();
}
