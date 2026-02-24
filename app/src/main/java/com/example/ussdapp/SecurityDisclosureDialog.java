package com.example.ussdapp;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

/**
 * SecurityDisclosureDialog - Mandatory security consent dialog
 * 
 * Informs the user that the app will:
 * 1. Learn their PIN during manual transactions (Learning Mode)
 * 2. Use the stored PIN for background automation (Automation Mode)
 * 3. Requires accessibility service permissions
 * 
 * User must accept before the app can start the AccessibilityService.
 * Consent is recorded in SharedPreferences via SecurityManager.
 */
public class SecurityDisclosureDialog extends DialogFragment {

    private static final String TAG = "SecurityDisclosureDialog";
    private SecurityManager securityManager;
    private Runnable onAcceptCallback;

    /**
     * Factory method to create the dialog with callback.
     */
    public static SecurityDisclosureDialog newInstance(Runnable onAcceptCallback) {
        SecurityDisclosureDialog dialog = new SecurityDisclosureDialog();
        dialog.onAcceptCallback = onAcceptCallback;
        return dialog;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set dialog style to full-screen modal (non-cancellable)
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_security_disclosure, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize SecurityManager for consent recording
        securityManager = new SecurityManager(requireContext());

        // Set dialog as non-cancellable (must explicitly press Accept)
        setCancelable(false);

        // Setup UI elements
        TextView tvTitle = view.findViewById(R.id.tv_disclosure_title);
        TextView tvDescription = view.findViewById(R.id.tv_disclosure_description);
        TextView tvLearningMode = view.findViewById(R.id.tv_learning_mode);
        TextView tvAutomationMode = view.findViewById(R.id.tv_automation_mode);
        TextView tvWarning = view.findViewById(R.id.tv_disclosure_warning);
        Button btnAccept = view.findViewById(R.id.btn_accept_disclosure);

        // Set text content
        tvTitle.setText("Security & Privacy Disclosure");
        tvDescription.setText("This app requires access to your device's accessibility features to automate USSD transactions.");

        tvLearningMode.setText("• Learning Mode: During your first manual transaction, the app will capture your PIN.");
        tvAutomationMode.setText("• Automation Mode: The stored PIN will be automatically entered in subsequent transactions.");

        tvWarning.setText("⚠️ Your PIN is encrypted using Android's Jetpack Security library. Never share your PIN or provide unauthorized access to this app.");

        // Accept button handler
        btnAccept.setOnClickListener(v -> handleAcceptConsent());
    }

    /**
     * Handle user accepting the security disclosure.
     * 1. Record consent in SecurityManager
     * 2. Navigate to accessibility settings
     * 3. Execute callback if provided
     */
    private void handleAcceptConsent() {
        // Record consent
        securityManager.setSecurityConsentGranted(true);

        // Execute callback if provided (e.g., to enable UI elements)
        if (onAcceptCallback != null) {
            onAcceptCallback.run();
        }

        // Navigate to Accessibility Settings to enable the service
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        // Close the dialog
        dismiss();
    }

    /**
     * Override back button behavior - prevent dismissal without consent
     */
    @Override
    public int show(@NonNull androidx.fragment.app.FragmentTransaction transaction, @Nullable String tag) {
        // Ensure dialog is not cancellable by back button
        setCancelable(false);
        return super.show(transaction, tag);
    }
}
