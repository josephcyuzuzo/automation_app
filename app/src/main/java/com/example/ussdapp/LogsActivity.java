package com.example.ussdapp;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;
import java.util.List;

/**
 * Activity to display transaction logs from the database.
 */
public class LogsActivity extends AppCompatActivity {

    private TextView logsTextView;
    private UssdDatabase database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logs);

        logsTextView = findViewById(R.id.tv_logs);
        database = UssdDatabase.getInstance(this);

        displayLogs();
    }

    private void displayLogs() {
        Thread thread = new Thread(() -> {
            List<TransactionRecord> records = database.transactionRecordDao().getAllRecords();
            StringBuilder sb = new StringBuilder();
            for (TransactionRecord record : records) {
                sb.append(record.toString()).append("\n");
            }

            runOnUiThread(() -> {
                if (records.isEmpty()) {
                    logsTextView.setText("No transaction records yet.");
                } else {
                    logsTextView.setText(sb.toString());
                }
            });
        });
        thread.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        displayLogs();
    }
}
