package com.example.ussdapp;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.Log;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AccessibilityService to monitor USSD windows, extract balance, and auto-fill/click fields.
 * 
 * Workflow:
 * 1. Listen for TYPE_WINDOW_STATE_CHANGED events (USSD popup appears)
 * 2. Read USSD response text using AccessibilityNodeInfo
 * 3. Extract numerical balance using regex pattern
 * 4. Save transaction record to Room Database
 * 5. Detect EditText fields and input PIN/Amount using ACTION_SET_TEXT
 * 6. Auto-click OK/Confirm buttons
 * 7. If balance meets condition, trigger version 2 logic (second USSD transfer)
 */
public class UssdAccessibilityService extends AccessibilityService {

    private static final String TAG = "UssdAccessibilityService";
    private UssdDatabase database;
    private SecurityManager securityManager;
    private TransactionEngine transactionEngine;
    private static final double BALANCE_THRESHOLD = 100.0;
    private static final String V2_USSD_CODE = "*123*1#";
    private static final String PIN_TO_INPUT = "1234";
    
    // Learning/Automation mode flags
    private boolean isAutomatedProcess = false;
    
    // Regex pattern to extract numerical balance from USSD response
    // Matches: "Balance: 1234.56", "Your balance is 1000", "KES 500.00", "Available amount: 2000"
    private static final Pattern BALANCE_PATTERN = 
            Pattern.compile("(?:balance|amount|available|total)\\s*(?:is|:)?\\s*(?:KES|USD|EUR|GHS|RMB)?\\s*([0-9]{1,10}(?:[.,][0-9]{2})?)", Pattern.CASE_INSENSITIVE);

    // Regex pattern to detect PIN entry request
    // Matches: "Enter PIN", "confirm PIN", "PIN:", "Enter your PIN", etc.
    private static final Pattern PIN_ENTRY_PATTERN = 
            Pattern.compile("(?:enter|confirm|input)\\s+(?:your\\s+)?(?:security)?\\s*pin|pin\\s*(?:entry|number|code|:)", Pattern.CASE_INSENSITIVE);

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            // Check security consent before processing any events
            if (!securityManager.hasSecurityConsentGranted()) {
                Log.w(TAG, "Security consent not yet granted. Ignoring accessibility event.");
                return;
            }
            
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                Log.d(TAG, "TYPE_WINDOW_STATE_CHANGED event fired");
                handleWindowStateChange();
            }
            else if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                Log.d(TAG, "TYPE_WINDOW_CONTENT_CHANGED event fired");
                handleWindowContentChange();
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception in onAccessibilityEvent", e);
        }
    }

    /**
     * Handle TYPE_WINDOW_STATE_CHANGED events (USSD popup opened).
     */
    private void handleWindowStateChange() {
        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode == null) {
                Log.d(TAG, "Root node is null in handleWindowStateChange");
                return;
            }

            String windowText = extractTextFromNode(rootNode);
            Log.d(TAG, "Window State Changed - Text: " + windowText);

            if (isUssdWindow(windowText)) {
                processUssdResponse(rootNode, windowText);
            }

            rootNode.recycle();
        } catch (Exception e) {
            Log.e(TAG, "Error in handleWindowStateChange", e);
        }
    }

    /**
     * Handle TYPE_WINDOW_CONTENT_CHANGED events (USSD content updated).
     */
    private void handleWindowContentChange() {
        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode == null) {
                Log.d(TAG, "Root node is null in handleWindowContentChange");
                return;
            }

            String windowText = extractTextFromNode(rootNode);
            Log.d(TAG, "Window Content Changed - Text: " + windowText);

            if (isUssdWindow(windowText)) {
                processUssdResponse(rootNode, windowText);
            }

            rootNode.recycle();
        } catch (Exception e) {
            Log.e(TAG, "Error in handleWindowContentChange", e);
        }
    }

    /**
     * Check if the window content looks like a USSD response.
     */
    private boolean isUssdWindow(String text) {
        if (text == null || text.isEmpty()) return false;
        String lower = text.toLowerCase();
        return lower.contains("balance") || lower.contains("confirm") || 
               lower.contains("enter") || lower.contains("amount") ||
               lower.contains("transfer") || lower.contains("account") ||
               lower.contains("number");
    }

    /**
     * Process USSD response: extract balance, log, and handle auto-fill.
     */
    private void processUssdResponse(AccessibilityNodeInfo rootNode, String ussdText) {
        // Check if this is a PIN entry request
        if (isPinEntryRequest(ussdText)) {
            Log.d(TAG, "PIN entry detected. Learning mode: " + !isAutomatedProcess);
            
            if (!isAutomatedProcess) {
                // Learning mode: capture the user's manual PIN input
                capturePinInput(rootNode);
            } else {
                // Automation mode: retrieve PIN and auto-fill
                autofillPinAndSubmit(rootNode);
            }
            return;
        }

        // Normal balance checking flow
        double balance = extractBalanceFromText(ussdText);
        
        if (balance > 0) {
            Log.d(TAG, "Balance extracted: " + balance);
            
            // Use TransactionEngine if available and in automation mode
            if (transactionEngine != null && isAutomatedProcess) {
                Log.d(TAG, "Processing balance via TransactionEngine");
                transactionEngine.processExtractedBalance(balance);
            } else {
                // Fallback: log and check using TransferLogicHelper
                UssdHelper.logTransaction(database, "BALANCE_CHECK", balance, "SUCCESS");
                TransferLogicHelper.checkAndTriggerTransfer(this, balance, database);
            }
        } else {
            Log.d(TAG, "Could not extract balance from: " + ussdText);
            UssdHelper.logTransaction(database, "BALANCE_CHECK", 0, "FAILED");
        }

        findAndFillEditText(rootNode);
        findAndClickButton(rootNode);
    }

    /**
     * Check if the window contains a PIN entry request.
     */
    /**
     * Check if the USSD screen is requesting a PIN entry.
     * Uses regex pattern to detect "Enter PIN", "Confirm PIN", "PIN code", etc.
     * 
     * @param text The USSD response text
     * @return true if PIN entry is detected, false otherwise
     */
    private boolean isPinEntryRequest(String text) {
        if (text == null || text.isEmpty()) return false;
        
        // Use regex pattern for more reliable detection
        Matcher matcher = PIN_ENTRY_PATTERN.matcher(text);
        boolean pinDetected = matcher.find();
        
        if (pinDetected) {
            Log.d(TAG, "PIN entry detected via regex: '" + matcher.group(0) + "'");
        }
        
        return pinDetected;
    }

    /**
     * Validate PIN format: must be exactly 4 or 5 digits long.
     * 
     * @param pin The PIN to validate
     * @return true if PIN is valid (4-5 digits), false otherwise
     */
    private boolean isValidPin(String pin) {
        if (pin == null || pin.isEmpty()) {
            Log.w(TAG, "PIN is null or empty");
            return false;
        }
        
        // Check if PIN contains only digits
        if (!pin.matches("^[0-9]+$")) {
            Log.w(TAG, "PIN contains non-digit characters: '" + pin + "'");
            return false;
        }
        
        // Check if PIN is 4-5 digits long
        int pinLength = pin.length();
        if (pinLength < 4 || pinLength > 5) {
            Log.w(TAG, "PIN length invalid. Expected 4-5 digits, got: " + pinLength + " (PIN: '" + pin + "')");
            return false;
        }
        
        Log.d(TAG, "PIN validation passed: " + pinLength + " digits");
        return true;
    }

    /**
     * Learning mode: capture the user's manual PIN input from the EditText.
     * Validates PIN is exactly 4-5 digits, then saves securely using SecurityManager.
     * 
     * Only saves the PIN if:
     * - EditText field is found
     * - PIN is entered (not empty)
     * - PIN is exactly 4 or 5 digits
     * - SecurityManager encryption succeeds
     */
    private void capturePinInput(AccessibilityNodeInfo rootNode) {
        try {
            AccessibilityNodeInfo editText = findEditTextNode(rootNode);
            if (editText == null) {
                Log.d(TAG, "No EditText found for PIN capture");
                return;
            }

            CharSequence enteredText = editText.getText();
            if (enteredText == null || enteredText.toString().isEmpty()) {
                Log.d(TAG, "No PIN entered in EditText - skipping capture");
                return;
            }

            String capturedPin = enteredText.toString();
            
            // Validate PIN: must be 4-5 digits
            if (!isValidPin(capturedPin)) {
                Log.w(TAG, "PIN validation failed - will not save. PIN length: " + capturedPin.length());
                UssdHelper.logTransaction(database, "PIN_CAPTURE", 0, "INVALID_FORMAT");
                return;
            }
            
            // Save PIN securely using EncryptedSharedPreferences
            boolean saved = securityManager.saveEncryptedPin(capturedPin);
            if (saved) {
                Log.d(TAG, "PIN captured and saved in learning mode (" + capturedPin.length() + " digits)");
                UssdHelper.logTransaction(database, "PIN_CAPTURE", 0, "SUCCESS");
            } else {
                Log.e(TAG, "Failed to save captured PIN to encrypted storage");
                UssdHelper.logTransaction(database, "PIN_CAPTURE", 0, "FAILED");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error capturing PIN input", e);
            UssdHelper.logTransaction(database, "PIN_CAPTURE", 0, "FAILED");
        }
    }

    /**
     * Automation mode: retrieve the stored PIN and auto-fill the EditText using ACTION_SET_TEXT.
     * Validates PIN is 4-5 digits before attempting auto-fill.
     * Then programmatically clicks the 'Send' or 'OK' button.
     * 
     * If no valid PIN is stored or validation fails, falls back to learning mode.
     */
    private void autofillPinAndSubmit(AccessibilityNodeInfo rootNode) {
        try {
            // Retrieve PIN from SecurityManager (decrypted using EncryptedSharedPreferences)
            String storedPin = securityManager.getDecryptedPin();
            if (storedPin == null || storedPin.isEmpty()) {
                Log.d(TAG, "No stored PIN found. Falling back to learning mode.");
                isAutomatedProcess = false;
                capturePinInput(rootNode);
                return;
            }

            // Validate PIN: must be 4-5 digits
            if (!isValidPin(storedPin)) {
                Log.e(TAG, "Stored PIN validation failed - PIN length: " + storedPin.length() + ", will not auto-fill");
                UssdHelper.logTransaction(database, "PIN_AUTOFILL", 0, "INVALID_PIN");
                return;
            }

            // Find and fill the EditText
            AccessibilityNodeInfo editText = findEditTextNode(rootNode);
            if (editText == null) {
                Log.d(TAG, "No EditText found for PIN autofill");
                UssdHelper.logTransaction(database, "PIN_AUTOFILL", 0, "NO_EDIT_TEXT");
                return;
            }

            // Auto-fill using ACTION_SET_TEXT
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, storedPin);
            
            boolean fillSuccess = editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            if (fillSuccess) {
                Log.d(TAG, "PIN auto-filled successfully (" + storedPin.length() + " digits)");
                UssdHelper.logTransaction(database, "PIN_AUTOFILL", 0, "SUCCESS");

                // Auto-click the "Send" or "OK" button
                new Thread(() -> {
                    try {
                        Thread.sleep(500); // Small delay for UI to update
                        AccessibilityNodeInfo clickNode = getRootInActiveWindow();
                        if (clickNode != null) {
                            findAndClickPinSubmitButton(clickNode);
                            clickNode.recycle();
                        }
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Interrupted while waiting to click button", e);
                    }
                }).start();
            } else {
                Log.e(TAG, "Failed to auto-fill PIN using ACTION_SET_TEXT");
                UssdHelper.logTransaction(database, "PIN_AUTOFILL", 0, "FAILED");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in autofill and submit", e);
            UssdHelper.logTransaction(database, "PIN_AUTOFILL", 0, "FAILED");
        }
    }

    /**
     * Find an EditText node in the accessibility node tree.
     */
    private AccessibilityNodeInfo findEditTextNode(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return null;

        String className = rootNode.getClassName() != null ? rootNode.getClassName().toString() : "";
        if (className.contains("EditText") || className.contains("TextInputEditText")) {
            return rootNode;
        }

        int childCount = rootNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = rootNode.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = findEditTextNode(child);
                child.recycle();
                if (found != null) return found;
            }
        }

        return null;
    }

    /**
     * Find and click the PIN submission button (Send, OK, Confirm).
     */
    private void findAndClickPinSubmitButton(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return;

        performActionOnNode(rootNode, (node) -> {
            String className = node.getClassName() != null ? node.getClassName().toString() : "";
            if (className.contains("Button") || className.contains("ImageButton")) {
                CharSequence text = node.getText();
                String buttonText = text != null ? text.toString().toLowerCase() : "";
                
                if (buttonText.contains("send") || buttonText.contains("ok") || 
                    buttonText.contains("confirm") || buttonText.contains("submit")) {
                    
                    Log.d(TAG, "Found and clicking PIN submit button: '" + buttonText + "'");
                    boolean clickSuccess = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    Log.d(TAG, "Button click result: " + (clickSuccess ? "success" : "failed"));
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * Extract numerical balance from USSD response text using regex pattern.
     * 
     * Matches formats like:
     *   "Balance: 1234.56"
     *   "Your balance is 1000"
     *   "KES 500.00"
     *   "Available amount: 2000"
     * 
     * @param ussdText The USSD response text
     * @return The extracted balance as a double, or -1 if not found
     */
    private double extractBalanceFromText(String ussdText) {
        if (ussdText == null || ussdText.isEmpty()) {
            Log.d(TAG, "USSD text is null or empty");
            return -1;
        }

        Matcher matcher = BALANCE_PATTERN.matcher(ussdText);
        if (matcher.find()) {
            String balanceStr = matcher.group(1);
            try {
                balanceStr = balanceStr.replace(',', '.');
                double balance = Double.parseDouble(balanceStr);
                Log.d(TAG, "Regex matched balance: " + balance + " (raw match: '" + matcher.group(0) + "')");
                return balance;
            } catch (NumberFormatException e) {
                Log.w(TAG, "Failed to parse balance string: " + balanceStr, e);
                return -1;
            }
        }

        Log.d(TAG, "No balance pattern match found in: " + ussdText);
        return -1;
    }

    /**
     * Extract all text from accessibility node tree recursively.
     * Traverses the entire node hierarchy and concatenates text content.
     */
    private String extractTextFromNode(AccessibilityNodeInfo node) {
        StringBuilder sb = new StringBuilder();
        if (node != null) {
            if (node.getText() != null) {
                sb.append(node.getText()).append(" ");
            }
            int childCount = node.getChildCount();
            for (int i = 0; i < childCount; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    sb.append(extractTextFromNode(child));
                    child.recycle();
                }
            }
        }
        return sb.toString().trim();
    }

    /**
     * Find an EditText field in the USSD popup and fill it with PIN/amount using ACTION_SET_TEXT.
     * 
     * This method searches for EditText nodes and attempts to fill them using ACTION_SET_TEXT
     * with the specified PIN or amount string.
     * 
     * @param rootNode The root accessibility node
     * @return true if an EditText was found and filled, false otherwise
     */
    private boolean findAndFillEditText(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) {
            Log.d(TAG, "rootNode is null in findAndFillEditText");
            return false;
        }

        return searchAndFillEditTextByClassName(rootNode);
    }

    /**
     * Recursively search for EditText by class name and attempt to fill it.
     * 
     * Looks for node class names containing "EditText" or "TextInputEditText"
     * and uses ACTION_SET_TEXT to input the PIN/amount.
     * 
     * @param node The current accessibility node
     * @return true if an EditText was found and filled, false otherwise
     */
    private boolean searchAndFillEditTextByClassName(AccessibilityNodeInfo node) {
        if (node == null) return false;

        String className = node.getClassName() != null ? node.getClassName().toString() : "";
        
        if (className.contains("EditText") || className.contains("TextInputEditText")) {
            Log.d(TAG, "Found EditText node: " + className);
            return fillEditTextWithPIN(node);
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                boolean filled = searchAndFillEditTextByClassName(child);
                child.recycle();
                if (filled) return true;
            }
        }

        return false;
    }

    /**
     * Fill an EditText with PIN or amount using ACTION_SET_TEXT.
     * 
     * Uses the Bundle.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE argument to set text
     * on an editable EditText node. This is the recommended method for automating
     * text input on accessibility-enabled views.
     * 
     * @param editTextNode The EditText accessibility node
     * @return true if filled successfully, false otherwise
     */
    private boolean fillEditTextWithPIN(AccessibilityNodeInfo editTextNode) {
        if (editTextNode == null) {
            Log.d(TAG, "EditText node is null");
            return false;
        }

        if (!editTextNode.isEditable()) {
            Log.d(TAG, "EditText node is not editable, attempting click");
            editTextNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            return false;
        }

        try {
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, PIN_TO_INPUT);
            
            boolean success = editTextNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            
            if (success) {
                Log.d(TAG, "Successfully filled EditText with PIN using ACTION_SET_TEXT");
                UssdHelper.logTransaction(database, "AUTOFILL", 0, "SUCCESS");
                return true;
            } else {
                Log.d(TAG, "ACTION_SET_TEXT returned false, trying click");
                editTextNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception while filling EditText with ACTION_SET_TEXT", e);
            UssdHelper.logTransaction(database, "AUTOFILL", 0, "FAILED");
            return false;
        }
    }

    /**
     * Find and click a button (OK, Confirm, Send, Submit, Continue) in the USSD popup.
     * 
     * Recursively searches the node tree for Button nodes and clicks the first one
     * that matches common action button text.
     */
    private void findAndClickButton(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) {
            Log.d(TAG, "rootNode is null in findAndClickButton");
            return;
        }

        performActionOnNode(rootNode, (node) -> {
            String className = node.getClassName() != null ? node.getClassName().toString() : "";
            if (className.contains("Button") || className.contains("ImageButton")) {
                CharSequence text = node.getText();
                String buttonText = text != null ? text.toString().toLowerCase() : "";
                
                if (buttonText.contains("ok") || buttonText.contains("confirm") || 
                    buttonText.contains("send") || buttonText.contains("submit") ||
                    buttonText.contains("continue") || buttonText.contains("yes")) {
                    
                    Log.d(TAG, "Found and clicking button: '" + buttonText + "'");
                    boolean clickSuccess = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    Log.d(TAG, "Button click result: " + (clickSuccess ? "success" : "failed"));
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * Recursively perform an action on nodes matching a condition.
     * Stops searching after the first successful action.
     */
    private void performActionOnNode(AccessibilityNodeInfo node, NodeAction action) {
        if (node == null) return;

        if (action.perform(node)) {
            return;
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                performActionOnNode(child, action);
                child.recycle();
            }
        }
    }

    /**
     * Functional interface for node actions.
     * Implement to define custom behavior when traversing the node tree.
     */
    @FunctionalInterface
    private interface NodeAction {
        /**
         * Perform an action on the given node.
         * @param node The accessibility node
         * @return true if the action was performed successfully, false to continue searching
         */
        boolean perform(AccessibilityNodeInfo node);
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        database = UssdDatabase.getInstance(this);
        securityManager = new SecurityManager(this);
        
        // Check if user has accepted the mandatory security disclosure
        if (!securityManager.hasSecurityConsentGranted()) {
            Log.w(TAG, "Security consent not granted. AccessibilityService will not process events.");
            return; // Service will not process events until consent is given
        }
        
        transactionEngine = new TransactionEngine(this, database, securityManager);
        isAutomatedProcess = !securityManager.isLearningMode();
        Log.d(TAG, "UssdAccessibilityService created - Automated mode: " + isAutomatedProcess);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "UssdAccessibilityService destroyed");
    }

    /**
     * Get encryption information.
     */
    public String getEncryptionInfo() {
        return securityManager.getEncryptionInfo();
    }

    /**
     * Get the TransactionEngine instance.
     * Useful for manually triggering balance checks or transfers.
     * 
     * @return The TransactionEngine instance
     */
    public TransactionEngine getTransactionEngine() {
        return transactionEngine;
    }

    /**
     * Toggle between learning mode and automation mode.
     * 
     * Learning mode: Captures user's manual PIN input
     * Automation mode: Uses stored PIN for auto-fill
     * 
     * @param automationEnabled true for automation mode, false for learning mode
     */
    public void setAutomationMode(boolean automationEnabled) {
        isAutomatedProcess = automationEnabled;
        securityManager.setLearningMode(!automationEnabled);
        Log.d(TAG, "Mode switched to: " + (automationEnabled ? "AUTOMATION" : "LEARNING"));
    }

    /**
     * Check current mode.
     * 
     * @return true if automation mode is active, false if learning mode
     */
    public boolean isAutomationEnabled() {
        return isAutomatedProcess;
    }
}
