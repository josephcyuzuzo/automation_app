package com.example.ussdapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * SecurityManager handles secure storage and retrieval of sensitive data (PIN, recipient phone).
 * 
 * Uses Android Jetpack Security Library:
 * - androidx.security:security-crypto
 * - EncryptedSharedPreferences with MasterKey from Android Keystore
 * - AES-256-GCM encryption
 * 
 * Features:
 * - MasterKey stored in Android Keystore
 * - AES256_GCM symmetric encryption for all data
 * - EncryptedSharedPreferences for encrypted storage
 * - No plain-text storage of sensitive information
 */
public class SecurityManager {

    private static final String TAG = "SecurityManager";
    private static final String MASTER_KEY_ALIAS = "ussd_app_master_key";
    private static final String ENCRYPTED_PREFS_NAME = "ussd_encrypted_prefs";
    private static final String PIN_KEY = "encrypted_pin";
    private static final String RECIPIENT_KEY = "encrypted_recipient";
    private static final String LEARNING_MODE_KEY = "learning_mode_enabled";
    private static final String SECURITY_CONSENT_KEY = "security_consent_granted";

    private EncryptedSharedPreferences encryptedSharedPreferences;
    private MasterKey masterKey;

    /**
     * Initialize the SecurityManager with EncryptedSharedPreferences.
     * 
     * Creates a MasterKey in the Android Keystore if one doesn't exist,
     * then initializes EncryptedSharedPreferences for encrypted data storage.
     * 
     * @param context The Android application context
     */
    public SecurityManager(Context context) {
        try {
            // Create or retrieve MasterKey from Android Keystore
            this.masterKey = new MasterKey.Builder(context, MASTER_KEY_ALIAS)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            Log.d(TAG, "MasterKey created/retrieved with AES256_GCM scheme");

            // Initialize EncryptedSharedPreferences
            this.encryptedSharedPreferences = EncryptedSharedPreferences.create(
                    context,
                    ENCRYPTED_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            Log.d(TAG, "SecurityManager initialized with EncryptedSharedPreferences");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing SecurityManager", e);
            throw new RuntimeException("Failed to initialize SecurityManager", e);
        }
    }

    /**
     * Save PIN securely using EncryptedSharedPreferences with AES256-GCM encryption.
     * 
     * The PIN is encrypted before being stored in SharedPreferences using the
     * AES256_GCM cipher suite provided by the Android Jetpack Security library.
     * 
     * @param pin The PIN to store
     * @return true if saved successfully, false otherwise
     */
    public boolean saveEncryptedPin(String pin) {
        try {
            if (pin == null || pin.isEmpty()) {
                Log.w(TAG, "Attempted to save empty PIN");
                return false;
            }

            SharedPreferences.Editor editor = encryptedSharedPreferences.edit();
            editor.putString(PIN_KEY, pin);
            editor.apply();

            Log.d(TAG, "PIN saved securely using EncryptedSharedPreferences (length: " + pin.length() + " chars)");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error saving encrypted PIN", e);
            return false;
        }
    }

    /**
     * Retrieve and decrypt the stored PIN.
     * 
     * The PIN is automatically decrypted by EncryptedSharedPreferences
     * using the MasterKey and AES256-GCM cipher.
     * 
     * @return The decrypted PIN, or null if not found
     */
    public String getDecryptedPin() {
        try {
            String decryptedPin = encryptedSharedPreferences.getString(PIN_KEY, null);
            if (decryptedPin == null) {
                Log.d(TAG, "No PIN found in encrypted storage");
                return null;
            }

            Log.d(TAG, "PIN retrieved and decrypted successfully");
            return decryptedPin;
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving decrypted PIN", e);
            return null;
        }
    }

    /**
     * Check if a PIN is stored in encrypted storage.
     * 
     * @return true if PIN exists, false otherwise
     */
    public boolean hasPIN() {
        return encryptedSharedPreferences.contains(PIN_KEY);
    }

    /**
     * Delete the stored PIN.
     * 
     * @return true if deleted successfully, false otherwise
     */
    public boolean deletePIN() {
        try {
            SharedPreferences.Editor editor = encryptedSharedPreferences.edit();
            editor.remove(PIN_KEY);
            editor.apply();

            Log.d(TAG, "PIN deleted successfully from encrypted storage");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting PIN", e);
            return false;
        }
    }

    /**
     * Save the recipient phone number securely using EncryptedSharedPreferences.
     * 
     * @param recipient The recipient phone number or account ID
     * @return true if saved successfully, false otherwise
     */
    public boolean saveEncryptedRecipient(String recipient) {
        try {
            if (recipient == null || recipient.isEmpty()) {
                Log.w(TAG, "Attempted to save empty recipient");
                return false;
            }

            SharedPreferences.Editor editor = encryptedSharedPreferences.edit();
            editor.putString(RECIPIENT_KEY, recipient);
            editor.apply();

            Log.d(TAG, "Recipient saved securely using EncryptedSharedPreferences");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error saving encrypted recipient", e);
            return false;
        }
    }

    /**
     * Retrieve and decrypt the stored recipient.
     * 
     * @return The decrypted recipient, or null if not found
     */
    public String getDecryptedRecipient() {
        try {
            String decryptedRecipient = encryptedSharedPreferences.getString(RECIPIENT_KEY, null);
            if (decryptedRecipient == null) {
                Log.d(TAG, "No recipient found in encrypted storage");
                return null;
            }

            Log.d(TAG, "Recipient retrieved and decrypted successfully");
            return decryptedRecipient;
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving decrypted recipient", e);
            return null;
        }
    }

    /**
     * Check if a recipient is stored in encrypted storage.
     * 
     * @return true if recipient exists, false otherwise
     */
    public boolean hasRecipient() {
        return encryptedSharedPreferences.contains(RECIPIENT_KEY);
    }

    /**
     * Delete the stored recipient.
     * 
     * @return true if deleted successfully, false otherwise
     */
    public boolean deleteRecipient() {
        try {
            SharedPreferences.Editor editor = encryptedSharedPreferences.edit();
            editor.remove(RECIPIENT_KEY);
            editor.apply();

            Log.d(TAG, "Recipient deleted successfully from encrypted storage");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting recipient", e);
            return false;
        }
    }

    /**
     * Set learning mode flag.
     * When true, the app captures user input instead of automating.
     * 
     * @param enabled true to enable learning mode, false for automation mode
     */
    public void setLearningMode(boolean enabled) {
        try {
            SharedPreferences.Editor editor = encryptedSharedPreferences.edit();
            editor.putBoolean(LEARNING_MODE_KEY, enabled);
            editor.apply();

            Log.d(TAG, "Learning mode set to: " + enabled);
        } catch (Exception e) {
            Log.e(TAG, "Error setting learning mode", e);
        }
    }

    /**
     * Check if learning mode is enabled.
     * 
     * @return true if learning mode is enabled, false for automation mode
     */
    public boolean isLearningMode() {
        try {
            boolean learningMode = encryptedSharedPreferences.getBoolean(LEARNING_MODE_KEY, true);
            Log.d(TAG, "Learning mode status: " + learningMode);
            return learningMode;
        } catch (Exception e) {
            Log.e(TAG, "Error checking learning mode", e);
            return true; // Default to learning mode for safety
        }
    }

    /**
     * Clear all encrypted data (PIN, recipient, learning mode).
     * Use with caution.
     * 
     * @return true if cleared successfully, false otherwise
     */
    public boolean clearAllData() {
        try {
            SharedPreferences.Editor editor = encryptedSharedPreferences.edit();
            editor.remove(PIN_KEY);
            editor.remove(RECIPIENT_KEY);
            editor.remove(LEARNING_MODE_KEY);
            editor.apply();

            Log.d(TAG, "All encrypted data cleared");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error clearing all data", e);
            return false;
        }
    }

    /**
     * Record that the user has accepted the security disclosure and consented to PIN capture.
     * The AccessibilityService will not start until this flag is set to true.
     * 
     * @param granted true if user accepts the security disclosure, false otherwise
     * @return true if saved successfully, false otherwise
     */
    public boolean setSecurityConsentGranted(boolean granted) {
        try {
            SharedPreferences.Editor editor = encryptedSharedPreferences.edit();
            editor.putBoolean(SECURITY_CONSENT_KEY, granted);
            editor.apply();

            Log.d(TAG, "Security consent recorded: " + granted);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error setting security consent", e);
            return false;
        }
    }

    /**
     * Check if the user has accepted the security disclosure.
     * Returns false by default (consent not yet given).
     * 
     * @return true if user has accepted the disclosure, false otherwise
     */
    public boolean hasSecurityConsentGranted() {
        try {
            boolean consent = encryptedSharedPreferences.getBoolean(SECURITY_CONSENT_KEY, false);
            Log.d(TAG, "Security consent status: " + consent);
            return consent;
        } catch (Exception e) {
            Log.e(TAG, "Error checking security consent", e);
            return false;
        }
    }

    /**
     * Reset the security consent flag (for testing or user request to revoke consent).
     * 
     * @return true if reset successfully, false otherwise
     */
    public boolean resetSecurityConsent() {
        try {
            SharedPreferences.Editor editor = encryptedSharedPreferences.edit();
            editor.remove(SECURITY_CONSENT_KEY);
            editor.apply();

            Log.d(TAG, "Security consent reset");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error resetting security consent", e);
            return false;
        }
    }

    /**
     * Get information about the encryption method.
     * 
     * @return String describing the encryption scheme
     */
    public String getEncryptionInfo() {
        return "Master Key: " + MASTER_KEY_ALIAS + 
               " | Key Scheme: AES256_GCM" +
               " | Pref Key Encryption: AES256_SIV" +
               " | Pref Value Encryption: AES256_GCM" +
               " | Storage: EncryptedSharedPreferences";
    }
}
