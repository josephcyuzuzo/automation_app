package com.example.ussdapp;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Transaction log entity for Room database.
 * Records: Amount, Type, Status, Timestamp
 */
@Entity(tableName = "transaction_logs")
public class TransactionLog {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public double amount;
    public String type; // "BALANCE_CHECK", "TRANSFER", etc.
    public String status; // "SUCCESS", "FAILED", "PENDING"
    public long timestamp; // System.currentTimeMillis()

    public TransactionLog(double amount, String type, String status, long timestamp) {
        this.amount = amount;
        this.type = type;
        this.status = status;
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "TransactionLog{" +
                "id=" + id +
                ", amount=" + amount +
                ", type='" + type + '\'' +
                ", status='" + status + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
