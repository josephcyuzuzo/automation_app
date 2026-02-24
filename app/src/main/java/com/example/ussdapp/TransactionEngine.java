package com.example.ussdapp;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TransactionEngine manages the V2 workflow for automated USSD transactions.
 * 
 * Workflow:
 * 1. Set isAutomatedProcess = true to enable automation mode
 * 2. Call checkBalance() to initiate USSD balance check (*182*6*1#)
 * 3. AccessibilityService detects USSD response and extracts balance via Regex
 * 4. If balance > threshold, automatically trigger transfer USSD
 * 5. Log all transactions to Room Database
 * 
 * Thread-safe implementation for managing concurrent USSD operations.
 */
public class TransactionEngine {

    private static final String TAG = "TransactionEngine";

    // USSD codes
    private static final String USSD_BALANCE_CHECK = "*182*6*1#";
    private static final String USSD_TRANSFER_TEMPLATE = "*182*8*1*{RECIPIENT}*{AMOUNT}*{PIN}#";

    // Balance threshold for transfer (in RWF)
    private static final double TRANSFER_THRESHOLD = 1000.0;

    // Regex pattern for balance extraction
    private static final Pattern BALANCE_PATTERN = 
            Pattern.compile("(?:balance|amount|available|total)\\s*(?:is|:)?\\s*(?:KES|USD|EUR|GHS|RMB)?\\s*([0-9]{1,10}(?:[.,][0-9]{2})?)", Pattern.CASE_INSENSITIVE);

    private Context context;
    private UssdDatabase database;
    private SecurityManager securityManager;
    private double lastExtractedBalance = -1;
    private boolean isCheckingBalance = false;
    private boolean isPendingTransfer = false;

    /**
     * Initialize the TransactionEngine.
     * 
     * @param context The Android application context
     * @param database The Room database instance
     * @param securityManager The security manager for PIN retrieval
     */
    public TransactionEngine(Context context, UssdDatabase database, SecurityManager securityManager) {
        this.context = context;
        this.database = database;
        this.securityManager = securityManager;
        Log.d(TAG, "TransactionEngine initialized");
    }

    /**
     * Initiate a balance check by dialing the USSD code *182*6*1#.
     * 
     * This method:
     * 1. Sets isAutomatedProcess = true to enable automation mode
     * 2. Dials the balance check USSD code
     * 3. AccessibilityService will detect the USSD response
     * 4. Balance is extracted automatically via Regex
     * 5. If balance > threshold, transfer is triggered
     * 
     * @return true if balance check was initiated successfully, false otherwise
     */
    public synchronized boolean checkBalance() {
        if (isCheckingBalance) {
            Log.w(TAG, "Balance check already in progress");
            return false;
        }

        try {
            isCheckingBalance = true;
            lastExtractedBalance = -1;
            
            Log.d(TAG, "Initiating balance check with USSD: " + USSD_BALANCE_CHECK);
            UssdHelper.logTransaction(database, "BALANCE_CHECK_INITIATED", 0, "IN_PROGRESS");

            // Dial the balance check USSD code
            if (!dialUssdCode(USSD_BALANCE_CHECK)) {
                Log.e(TAG, "Failed to dial balance check USSD");
                UssdHelper.logTransaction(database, "BALANCE_CHECK_INITIATED", 0, "FAILED");
                isCheckingBalance = false;
                return false;
            }

            Log.d(TAG, "Balance check USSD dialed successfully");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error during balance check", e);
            UssdHelper.logTransaction(database, "BALANCE_CHECK_INITIATED", 0, "FAILED");
            isCheckingBalance = false;
            return false;
        }
    }

    /**
     * Process the extracted balance and trigger transfer if threshold is met.
     * 
     * This method is called by AccessibilityService after extracting balance from USSD response.
     * 
     * @param extractedBalance The balance extracted via Regex
     * @return true if transfer was triggered, false if balance is below threshold or an error occurred
     */
    public synchronized boolean processExtractedBalance(double extractedBalance) {
        try {
            lastExtractedBalance = extractedBalance;
            Log.d(TAG, "Processing extracted balance: " + extractedBalance + " RWF");

            if (extractedBalance <= 0) {
                Log.w(TAG, "Invalid balance extracted: " + extractedBalance);
                UssdHelper.logTransaction(database, "BALANCE_CHECK", 0, "INVALID_BALANCE");
                isCheckingBalance = false;
                return false;
            }

            // Log the extracted balance
            UssdHelper.logTransaction(database, "BALANCE_CHECK", extractedBalance, "SUCCESS");

            // Check if balance meets transfer threshold
            if (extractedBalance > TRANSFER_THRESHOLD) {
                Log.d(TAG, "Balance threshold met (" + extractedBalance + " > " + TRANSFER_THRESHOLD + ")");
                Log.d(TAG, "Triggering automatic transfer...");
                
                isPendingTransfer = true;
                boolean transferSuccess = triggerAutomaticTransfer(extractedBalance);
                isCheckingBalance = false;
                return transferSuccess;
            } else {
                Log.d(TAG, "Balance below transfer threshold (" + extractedBalance + " <= " + TRANSFER_THRESHOLD + ")");
                UssdHelper.logTransaction(database, "TRANSFER_SKIPPED", extractedBalance, "INSUFFICIENT_BALANCE");
                isCheckingBalance = false;
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing extracted balance", e);
            UssdHelper.logTransaction(database, "BALANCE_CHECK", extractedBalance, "FAILED");
            isCheckingBalance = false;
            return false;
        }
    }

    /**
     * Trigger automatic transfer if balance threshold is met.
     * 
     * Retrieves stored PIN and transfer details, formats USSD code, and dials it.
     * 
     * @param currentBalance The current account balance
     * @return true if transfer was triggered successfully, false otherwise
     */
    private synchronized boolean triggerAutomaticTransfer(double currentBalance) {
        try {
            // Retrieve encrypted PIN from SecurityManager
            String storedPin = securityManager.getDecryptedPin();
            if (storedPin == null || storedPin.isEmpty()) {
                Log.e(TAG, "No PIN stored. Cannot proceed with transfer.");
                UssdHelper.logTransaction(database, "TRANSFER", currentBalance, "NO_PIN_AVAILABLE");
                return false;
            }

            // Retrieve recipient
            String recipient = securityManager.getDecryptedRecipient();
            if (recipient == null || recipient.isEmpty()) {
                // Use default recipient if not stored
                recipient = "1789883";
                Log.w(TAG, "No recipient stored, using default: " + recipient);
            }

            // Calculate transfer amount (example: transfer 50% of balance, or use fixed amount)
            double transferAmount = Math.min(500, currentBalance * 0.5);
            // Alternatively, use a fixed amount:
            // double transferAmount = 500;

            Log.d(TAG, "Initiating transfer: recipient=" + recipient + ", amount=" + transferAmount + ", balance=" + currentBalance);

            // Format and dial transfer USSD
            String transferUssd = formatTransferUssd(recipient, String.valueOf((int) transferAmount), storedPin);
            
            if (!dialUssdCode(transferUssd)) {
                Log.e(TAG, "Failed to dial transfer USSD");
                UssdHelper.logTransactionWithAutomation(database, "TRANSFER", transferAmount, "FAILED",
                        currentBalance, -1, recipient, "FAILED");
                return false;
            }

            Log.d(TAG, "Transfer USSD dialed successfully");
            UssdHelper.logTransactionWithAutomation(database, "TRANSFER", transferAmount, "TRIGGERED",
                    currentBalance, currentBalance - transferAmount, recipient, "IN_PROGRESS");
            isPendingTransfer = false;
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error triggering automatic transfer", e);
            UssdHelper.logTransaction(database, "TRANSFER", 0, "FAILED");
            return false;
        }
    }

    /**
     * Dial a USSD code using Intent.ACTION_CALL.
     * 
     * Requires CALL_PHONE permission in AndroidManifest.xml.
     * 
     * @param ussdCode The USSD code to dial
     * @return true if Intent was started successfully, false otherwise
     */
    private boolean dialUssdCode(String ussdCode) {
        try {
            if (!isValidUssdFormat(ussdCode)) {
                Log.w(TAG, "Invalid USSD format: " + ussdCode);
                return false;
            }

            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + ussdCode));
            callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(callIntent);
            Log.d(TAG, "USSD call initiated: " + ussdCode);
            return true;

        } catch (SecurityException e) {
            Log.e(TAG, "CALL_PHONE permission not granted", e);
            UssdHelper.logTransaction(database, "USSD_CALL", 0, "PERMISSION_DENIED");
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error dialing USSD: " + ussdCode, e);
            return false;
        }
    }

    /**
     * Format the transfer USSD code by substituting parameters.
     * 
     * Template: *182*8*1*{RECIPIENT}*{AMOUNT}*{PIN}#
     * Example: *182*8*1*1789883*500*1234#
     * 
     * @param recipient Recipient identifier
     * @param amount Transfer amount
     * @param pin PIN for authorization
     * @return Formatted USSD code
     */
    private String formatTransferUssd(String recipient, String amount, String pin) {
        String ussd = USSD_TRANSFER_TEMPLATE
                .replace("{RECIPIENT}", recipient)
                .replace("{AMOUNT}", amount)
                .replace("{PIN}", pin);

        Log.d(TAG, "Formatted transfer USSD: " + ussd);
        return ussd;
    }

    /**
     * Validate USSD code format.
     * 
     * @param ussdCode The USSD code to validate
     * @return true if format is valid, false otherwise
     */
    private boolean isValidUssdFormat(String ussdCode) {
        if (ussdCode == null || ussdCode.isEmpty()) {
            return false;
        }

        if (!ussdCode.startsWith("*") || !ussdCode.endsWith("#")) {
            Log.w(TAG, "Invalid USSD format (must start with * and end with #): " + ussdCode);
            return false;
        }

        return true;
    }

    /**
     * Get the last extracted balance.
     * 
     * @return The last extracted balance, or -1 if none has been extracted
     */
    public double getLastExtractedBalance() {
        return lastExtractedBalance;
    }

    /**
     * Check if a balance check is currently in progress.
     * 
     * @return true if balance check is in progress, false otherwise
     */
    public boolean isCheckingBalance() {
        return isCheckingBalance;
    }

    /**
     * Check if a transfer is pending.
     * 
     * @return true if transfer is pending, false otherwise
     */
    public boolean isPendingTransfer() {
        return isPendingTransfer;
    }

    /**
     * Get the transfer threshold.
     * 
     * @return The minimum balance required to trigger a transfer
     */
    public double getTransferThreshold() {
        return TRANSFER_THRESHOLD;
    }

    /**
     * Get the balance check USSD code.
     * 
     * @return The USSD code for balance check
     */
    public String getBalanceCheckUssd() {
        return USSD_BALANCE_CHECK;
    }

    /**
     * Get the transfer USSD template.
     * 
     * @return The USSD template for transfer
     */
    public String getTransferTemplate() {
        return USSD_TRANSFER_TEMPLATE;
    }

    /**
     * Extract balance from USSD response text using regex.
     * 
     * @param ussdText The USSD response text
     * @return The extracted balance, or -1 if not found
     */
    public static double extractBalanceFromText(String ussdText) {
        if (ussdText == null || ussdText.isEmpty()) {
            return -1;
        }

        Matcher matcher = BALANCE_PATTERN.matcher(ussdText);
        if (matcher.find()) {
            try {
                String balanceStr = matcher.group(1).replace(',', '.');
                double balance = Double.parseDouble(balanceStr);
                Log.d(TAG, "Balance extracted via regex: " + balance);
                return balance;
            } catch (NumberFormatException e) {
                Log.w(TAG, "Failed to parse balance", e);
                return -1;
            }
        }

        Log.d(TAG, "No balance pattern match found");
        return -1;
    }

    /**
     * Reset the transaction engine state.
     * Useful for testing or cleanup.
     */
    public synchronized void reset() {
        isCheckingBalance = false;
        isPendingTransfer = false;
        lastExtractedBalance = -1;
        Log.d(TAG, "TransactionEngine state reset");
    }
}
