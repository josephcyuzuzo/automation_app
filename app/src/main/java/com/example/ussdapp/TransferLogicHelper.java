package com.example.ussdapp;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

/**
 * Helper class for transfer logic and USSD string formatting.
 * 
 * Handles:
 * - Transfer threshold checking (e.g., balance > 1000 RWF)
 * - USSD string formatting and parameter substitution
 * - Automated USSD dialing via Intent.ACTION_CALL
 * - Transaction logging
 */
public class TransferLogicHelper {

    private static final String TAG = "TransferLogicHelper";

    /**
     * USSD configuration constants.
     * Customize these based on your Mobile Money provider.
     */
    private static final double TRANSFER_THRESHOLD = 1000.0; // 1000 RWF minimum
    private static final String CURRENCY = "RWF";
    
    // USSD code template: *182*8*1*{RECIPIENT}*{AMOUNT}*{PIN}#
    private static final String USSD_TRANSFER_TEMPLATE = "*182*8*1*{RECIPIENT}*{AMOUNT}*{PIN}#";

    /**
     * Check if current balance exceeds transfer threshold and trigger USSD if true.
     * 
     * @param context The Android context for starting the Intent
     * @param currentBalance The current account balance
     * @param database The Room database instance for logging
     * @return true if transfer was triggered, false otherwise
     */
    public static boolean checkAndTriggerTransfer(Context context, double currentBalance, UssdDatabase database) {
        Log.d(TAG, "Checking transfer condition: balance=" + currentBalance + ", threshold=" + TRANSFER_THRESHOLD);

        if (currentBalance > TRANSFER_THRESHOLD) {
            Log.d(TAG, "Balance threshold met! Triggering transfer...");
            return triggerUssdTransfer(context, currentBalance, database);
        } else {
            Log.d(TAG, "Balance " + currentBalance + " does not meet threshold " + TRANSFER_THRESHOLD);
            UssdHelper.logTransaction(database, "TRANSFER_CHECK", currentBalance, "INSUFFICIENT_BALANCE");
            return false;
        }
    }

    /**
     * Trigger USSD transfer with default parameters (recipient, amount, PIN).
     * 
     * @param context The Android context for starting the Intent
     * @param amount The transfer amount
     * @param database The Room database instance for logging
     * @return true if transfer was triggered successfully, false otherwise
     */
    public static boolean triggerUssdTransfer(Context context, double amount, UssdDatabase database) {
        // Use default values; customize as needed
        String recipient = "1789883";
        String pin = "1234";
        
        return triggerUssdTransferWithParams(context, recipient, amount, pin, database);
    }

    /**
     * Trigger USSD transfer with custom parameters.
     * 
     * @param context The Android context for starting the Intent
     * @param recipient Recipient phone number or account ID
     * @param amount Transfer amount
     * @param pin PIN for authorization
     * @param database The Room database instance for logging
     * @return true if transfer was triggered successfully, false otherwise
     */
    public static boolean triggerUssdTransferWithParams(
            Context context, 
            String recipient, 
            double amount, 
            String pin, 
            UssdDatabase database) {
        
        try {
            // Format the USSD string with parameters
            String ussdCode = formatUssdString(recipient, String.valueOf((int) amount), pin);
            Log.d(TAG, "Formatted USSD code: " + ussdCode);

            // Create and execute the USSD call intent
            return dialUssdCode(context, ussdCode, database);
        } catch (Exception e) {
            Log.e(TAG, "Error triggering USSD transfer", e);
            UssdHelper.logTransaction(database, "TRANSFER", amount, "FAILED");
            return false;
        }
    }

    /**
     * Format the USSD string by substituting parameters into the template.
     * 
     * Template: *182*8*1*{RECIPIENT}*{AMOUNT}*{PIN}#
     * Example output: *182*8*1*1789883*500*1234#
     * 
     * @param recipient Recipient identifier
     * @param amount Transfer amount as string
     * @param pin PIN for authorization
     * @return Formatted USSD code string
     */
    public static String formatUssdString(String recipient, String amount, String pin) {
        String ussdCode = USSD_TRANSFER_TEMPLATE
                .replace("{RECIPIENT}", recipient)
                .replace("{AMOUNT}", amount)
                .replace("{PIN}", pin);

        Log.d(TAG, "formatUssdString - Template: " + USSD_TRANSFER_TEMPLATE);
        Log.d(TAG, "formatUssdString - Result: " + ussdCode);
        
        return ussdCode;
    }

    /**
     * Format the USSD string with custom template and parameters.
     * 
     * Allows flexibility for different USSD code formats across providers.
     * 
     * @param template USSD template with placeholders (e.g., "*180*{TRANSFER_AMOUNT}*{RECIPIENT}*{PIN}#")
     * @param recipient Recipient identifier
     * @param amount Transfer amount
     * @param pin PIN for authorization
     * @return Formatted USSD code string
     */
    public static String formatUssdStringCustom(String template, String recipient, String amount, String pin) {
        String ussdCode = template
                .replace("{RECIPIENT}", recipient)
                .replace("{AMOUNT}", amount)
                .replace("{PIN}", pin)
                .replace("{TRANSFER_AMOUNT}", amount); // Alternative placeholder

        Log.d(TAG, "formatUssdStringCustom - Template: " + template);
        Log.d(TAG, "formatUssdStringCustom - Result: " + ussdCode);
        
        return ussdCode;
    }

    /**
     * Dial a USSD code using Intent.ACTION_CALL.
     * 
     * Requires CALL_PHONE permission in AndroidManifest.xml.
     * 
     * @param context The Android context for starting the Intent
     * @param ussdCode The USSD code to dial (e.g., "*182*8*1*1789883*500*1234#")
     * @param database The Room database instance for logging
     * @return true if Intent was started successfully, false otherwise
     */
    public static boolean dialUssdCode(Context context, String ussdCode, UssdDatabase database) {
        try {
            // Validate USSD code format
            if (!isValidUssdFormat(ussdCode)) {
                Log.w(TAG, "USSD code format may be invalid: " + ussdCode);
            }

            // Create Intent with ACTION_CALL
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + ussdCode));
            callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Attempt to start the activity
            context.startActivity(callIntent);
            Log.d(TAG, "USSD call initiated: " + ussdCode);
            
            UssdHelper.logTransaction(database, "TRANSFER", 0, "TRIGGERED");
            return true;

        } catch (SecurityException e) {
            Log.e(TAG, "CALL_PHONE permission not granted", e);
            UssdHelper.logTransaction(database, "TRANSFER", 0, "PERMISSION_DENIED");
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error dialing USSD code: " + ussdCode, e);
            UssdHelper.logTransaction(database, "TRANSFER", 0, "FAILED");
            return false;
        }
    }

    /**
     * Validate USSD code format.
     * 
     * Valid USSD codes typically start with * and end with #.
     * 
     * @param ussdCode The USSD code to validate
     * @return true if format appears valid, false otherwise
     */
    public static boolean isValidUssdFormat(String ussdCode) {
        if (ussdCode == null || ussdCode.isEmpty()) {
            return false;
        }

        // USSD codes should start with * and end with #
        if (!ussdCode.startsWith("*") || !ussdCode.endsWith("#")) {
            Log.w(TAG, "USSD code does not start with * or end with #: " + ussdCode);
            return false;
        }

        // Check for at least one * and one #
        int starCount = 0;
        int hashCount = 0;
        for (char c : ussdCode.toCharArray()) {
            if (c == '*') starCount++;
            if (c == '#') hashCount++;
        }

        if (starCount < 1 || hashCount != 1) {
            Log.w(TAG, "USSD code format invalid (stars=" + starCount + ", hashes=" + hashCount + "): " + ussdCode);
            return false;
        }

        return true;
    }

    /**
     * Build a complete transfer operation.
     * 
     * Orchestrates:
     * 1. Check balance threshold
     * 2. Format USSD string
     * 3. Dial USSD code
     * 4. Log transaction
     * 
     * @param context The Android context
     * @param currentBalance The current account balance
     * @param recipient Recipient phone number or account ID
     * @param transferAmount Transfer amount
     * @param pin PIN for authorization
     * @param database The Room database instance for logging
     * @return true if transfer was successfully triggered, false otherwise
     */
    public static boolean executeTransferOperation(
            Context context,
            double currentBalance,
            String recipient,
            double transferAmount,
            String pin,
            UssdDatabase database) {

        Log.d(TAG, "Executing transfer operation: balance=" + currentBalance + 
              ", recipient=" + recipient + ", amount=" + transferAmount);

        // Step 1: Check balance threshold
        if (currentBalance <= TRANSFER_THRESHOLD) {
            Log.d(TAG, "Transfer cannot proceed: insufficient balance");
            UssdHelper.logTransaction(database, "TRANSFER", currentBalance, "INSUFFICIENT_BALANCE");
            return false;
        }

        // Step 2: Verify transfer amount is valid
        if (transferAmount <= 0) {
            Log.d(TAG, "Transfer cannot proceed: invalid amount");
            UssdHelper.logTransaction(database, "TRANSFER", transferAmount, "INVALID_AMOUNT");
            return false;
        }

        // Step 3: Format USSD string
        String ussdCode = formatUssdString(recipient, String.valueOf((int) transferAmount), pin);

        // Step 4: Dial USSD code
        boolean success = dialUssdCode(context, ussdCode, database);

        if (success) {
            Log.d(TAG, "Transfer operation executed successfully");
        } else {
            Log.d(TAG, "Transfer operation failed");
        }

        return success;
    }

    /**
     * Get the configured transfer threshold.
     * 
     * @return The minimum balance required to trigger a transfer
     */
    public static double getTransferThreshold() {
        return TRANSFER_THRESHOLD;
    }

    /**
     * Get the configured currency.
     * 
     * @return The currency code (e.g., "RWF")
     */
    public static String getCurrency() {
        return CURRENCY;
    }

    /**
     * Get the USSD template being used.
     * 
     * @return The USSD code template
     */
    public static String getUssdTemplate() {
        return USSD_TRANSFER_TEMPLATE;
    }

    /**
     * Example USSD templates for different Mobile Money providers.
     * Uncomment and customize based on your provider.
     */
    public static class UssdTemplates {
        // MTN Rwanda: *182*8*1*{RECIPIENT}*{AMOUNT}*{PIN}#
        public static final String MTN_RWANDA = "*182*8*1*{RECIPIENT}*{AMOUNT}*{PIN}#";
        
        // Airtel Rwanda: *144*2*{AMOUNT}*{RECIPIENT}*{PIN}#
        public static final String AIRTEL_RWANDA = "*144*2*{AMOUNT}*{RECIPIENT}*{PIN}#";
        
        // Vodacom Rwanda: *171*1*{AMOUNT}*{RECIPIENT}*{PIN}#
        public static final String VODACOM_RWANDA = "*171*1*{AMOUNT}*{RECIPIENT}*{PIN}#";
        
        // Example Kenya Safaricom: *334*2*1*{AMOUNT}*{RECIPIENT}*{PIN}#
        public static final String SAFARICOM_KENYA = "*334*2*1*{AMOUNT}*{RECIPIENT}*{PIN}#";
        
        // Example Uganda MTN: *165*3*1*{RECIPIENT}*{AMOUNT}*{PIN}#
        public static final String MTN_UGANDA = "*165*3*1*{RECIPIENT}*{AMOUNT}*{PIN}#";
    }
}
