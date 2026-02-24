package com.example.ussdapp;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room Database entity for transaction records.
 * Stores: Amount, Type, Status, Timestamp
 */
@Entity(tableName = "transaction_records")
public class TransactionRecord {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public double amount;
    public String type; // "BALANCE_CHECK", "TRANSFER", "WITHDRAWAL", etc.
    public String status; // "SUCCESS", "FAILED", "PENDING", "TRIGGERED"
    public long timestamp; // System.currentTimeMillis()
    
    // Automated action tracking fields
    public double initial_balance; // Balance before automated action
    public double final_balance; // Balance after automated action
    public String target_number; // Recipient phone number or account ID
    public String automation_status; // "SUCCESS", "FAILED", "IN_PROGRESS"

    /**
     * Full constructor with all fields including automation tracking.
     */
    public TransactionRecord(double amount, String type, String status, long timestamp, 
                           double initial_balance, double final_balance, String target_number, String automation_status) {
        this.amount = amount;
        this.type = type;
        this.status = status;
        this.timestamp = timestamp;
        this.initial_balance = initial_balance;
        this.final_balance = final_balance;
        this.target_number = target_number;
        this.automation_status = automation_status;
    }

    /**
     * Constructor for creating a basic transaction record (backward compatible).
     */
    public TransactionRecord(double amount, String type, String status, long timestamp) {
        this.amount = amount;
        this.type = type;
        this.status = status;
        this.timestamp = timestamp;
    }

    /**
     * Default constructor (required by Room).
     */
    public TransactionRecord() {
    }

    @Override
    public String toString() {
        return "TransactionRecord{" +
                "id=" + id +
                ", amount=" + amount +
                ", type='" + type + '\'' +
                ", status='" + status + '\'' +
                ", timestamp=" + timestamp +
                ", initial_balance=" + initial_balance +
                ", final_balance=" + final_balance +
                ", target_number='" + target_number + '\'' +
                ", automation_status='" + automation_status + '\'' +
                '}';
    }
}
