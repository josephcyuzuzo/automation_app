package com.example.ussdapp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for USSD operations: balance extraction, transfer conditions.
 */
public class UssdHelper {

    /**
     * Extract balance from USSD response text using regex.
     * Assumes balance format like "Balance: 1234.56" or "Your balance is KES 1234.56"
     */
    public static double extractBalance(String ussdText) {
        // Pattern to match amounts like "1234.56" or "1234"
        // Adjust regex based on your provider's format
        Pattern pattern = Pattern.compile("(?:balance|amount|available)\\s*(?:is|:)?\\s*(?:KES|USD|USD)?\\s*([0-9]+(?:\\.[0-9]{2})?)");
        Matcher matcher = pattern.matcher(ussdText.toLowerCase());

        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Check if balance meets condition for follow-up transfer.
     * Example: balance > 100
     */
    public static boolean shouldTriggerTransfer(double balance, double threshold) {
        return balance > threshold;
    }

    /**
     * Log a transaction to the database.
     */
    public static void logTransaction(UssdDatabase db, String type, double amount, String status) {
        TransactionRecord record = new TransactionRecord(amount, type, status, System.currentTimeMillis());
        db.transactionRecordDao().insert(record);
    }

    /**
     * Log a transaction with full automation tracking details.
     */
    public static void logTransactionWithAutomation(UssdDatabase db, String type, double amount, String status,
                                                   double initialBalance, double finalBalance, 
                                                   String targetNumber, String automationStatus) {
        TransactionRecord record = new TransactionRecord(
                amount, type, status, System.currentTimeMillis(),
                initialBalance, finalBalance, targetNumber, automationStatus
        );
        db.transactionRecordDao().insert(record);
    }
}
