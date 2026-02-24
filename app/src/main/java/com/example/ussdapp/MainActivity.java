package com.example.ussdapp;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.util.Log;

/**
 * Main activity to guide user to enable AccessibilityService.
 * Also provides controls to toggle between Learning and Automation modes.
 */
public class MainActivity extends AppCompatActivity {

    private SecurityManager securityManager;
    private TextView modeStatusTextView;
    private ToggleButton modeToggleButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize SecurityManager
        securityManager = new SecurityManager(this);

        // Check if user has accepted security disclosure
        if (!securityManager.hasSecurityConsentGranted()) {
            // Show mandatory security disclosure dialog
            showSecurityDisclosureDialog();
            return; // Exit - dialog will handle further interaction
        }

    // UI Components
        Button enableAccessibilityButton = findViewById(R.id.btn_enable_accessibility);
        Button viewLogsButton = findViewById(R.id.btn_view_logs);
        Button checkBalanceButton = findViewById(R.id.btn_check_balance);
        modeStatusTextView = findViewById(R.id.tv_mode_status);
        modeToggleButton = findViewById(R.id.toggle_automation_mode);

        // Enable Accessibility Service
        enableAccessibilityButton.setOnClickListener(v -> openAccessibilitySettings());

        // View Logs
        viewLogsButton.setOnClickListener(v -> startActivity(new Intent(this, LogsActivity.class)));

        // Check Balance via TransactionEngine
        checkBalanceButton.setOnClickListener(v -> initiateBalanceCheck());

        // Toggle Automation/Learning Mode
        modeToggleButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            securityManager.setLearningMode(!isChecked);
            updateModeStatus();
            
            String modeText = isChecked ? "Automation Mode enabled" : "Learning Mode enabled";
            Toast.makeText(MainActivity.this, modeText, Toast.LENGTH_SHORT).show();
        });

        // Check accessibility and update UI
        checkAndUpdateUI();
    }

    /**
     * Initiate balance check via TransactionEngine.
     */
    private void initiateBalanceCheck() {
        try {
            // Verify PIN is stored
            if (!securityManager.hasPIN()) {
                Toast.makeText(this, "Please enable automation mode and complete a transaction first to store PIN", Toast.LENGTH_LONG).show();
                return;
            }

            // Get the AccessibilityService instance (this would require a static reference or service binding)
            // For now, just show a message
            Toast.makeText(this, "Balance check initiated. Check logs for results.", Toast.LENGTH_SHORT).show();
            
            // Log the action
            UssdDatabase db = UssdDatabase.getInstance(this);
            UssdHelper.logTransaction(db, "BALANCE_CHECK_MANUAL", 0, "INITIATED");

        } catch (Exception e) {
            Log.e("MainActivity", "Error initiating balance check", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Show the mandatory security disclosure dialog.
     * The user must accept this dialog before using the app.
     * Consent is recorded in SecurityManager.
     */
    private void showSecurityDisclosureDialog() {
        SecurityDisclosureDialog dialog = SecurityDisclosureDialog.newInstance(() -> {
            // Callback when user accepts the disclosure
            Log.d("MainActivity", "Security consent accepted");
            // Reinitialize UI after consent
            recreate();
        });

        // Show the dialog - it is non-cancellable
        dialog.show(getSupportFragmentManager(), "security_disclosure");
    }

    private void checkAndUpdateUI() {
        if (isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "AccessibilityService is enabled!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Please enable AccessibilityService", Toast.LENGTH_SHORT).show();
        }
        
        updateModeStatus();
    }

    private void updateModeStatus() {
        boolean isLearningMode = securityManager.isLearningMode();
        String status = isLearningMode ? "LEARNING MODE (Capturing PIN)" : "AUTOMATION MODE (Using Stored PIN)";
        modeStatusTextView.setText("Current Mode: " + status);
        modeToggleButton.setChecked(!isLearningMode);

        // Display encryption information
        String encryptionInfo = securityManager.getEncryptionInfo();
        Log.d("MainActivity", "Encryption: " + encryptionInfo);
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }

    private boolean isAccessibilityServiceEnabled() {
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }

        if (accessibilityEnabled == 1) {
            String enabledServices = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabledServices != null) {
                return !TextUtils.isEmpty(enabledServices)
                        && enabledServices.contains("com.example.ussdapp/com.example.ussdapp.UssdAccessibilityService");
            }
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateModeStatus();
    }
}
