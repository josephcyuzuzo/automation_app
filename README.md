# USSD Mobile Money Automation Android App

## Overview
A Java-based Android application that automates USSD transactions for Mobile Money using **AccessibilityService**, **Room Database**, and **Regex**.

### Key Features
- **AccessibilityService Integration**: Detects USSD windows, reads USSD responses, and extracts transaction data
- **Regex-based Balance Extraction**: Parses balance from USSD pop-ups
- **Room Database**: Records transaction logs (Amount, Type, Status, Timestamp)
- **Version 2 Logic**: Automatically triggers a second USSD transfer code if balance meets a condition
- **Auto-fill & Auto-click**: Optional feature to automatically fill form fields and click buttons
- **Permissions**: `CALL_PHONE`, `READ_SMS` declared in manifest

## Project Structure

```
app/
├── src/main/
│   ├── AndroidManifest.xml                 // Permissions & AccessibilityService declaration
│   ├── java/com/example/ussdapp/
│   │   ├── MainActivity.java              // Main UI to enable accessibility service
│   │   ├── LogsActivity.java              // Display transaction logs
│   │   ├── UssdAccessibilityService.java  // Core accessibility service for USSD monitoring
│   │   ├── UssdHelper.java                // Regex & utility functions
│   │   ├── TransactionLog.java            // Room entity
│   │   ├── TransactionLogDao.java         // Room DAO
│   │   └── AppDatabase.java               // Room database
│   ├── res/
│   │   ├── xml/
│   │   │   └── accessibility_service_config.xml  // Accessibility service config
│   │   └── layout/
│   │       ├── activity_main.xml
│   │       └── activity_logs.xml
├── build.gradle                            // App dependencies & Android configuration
├── settings.gradle
└── build.gradle (root)

```

## System Requirements

### Permissions
- `CALL_PHONE`: Required to dial USSD codes programmatically
- `READ_SMS`: Optional, for reading SMS responses (future feature)
- `BIND_ACCESSIBILITY_SERVICE`: Required in service declaration

### Minimum API Level
- **minSdk**: 21 (Android 5.0 Lollipop)
- **targetSdk**: 34 (Android 14)

## Configuration

### 1. AccessibilityService Configuration (`accessibility_service_config.xml`)
The service listens for:
- `typeWindowStateChanged`: Fires when USSD pop-up appears
- `typeWindowContentChanged`: Fires when content updates
- `canRetrieveWindowContent="true"`: Allows reading text from nodes

### 2. Manifest Declaration
The service is bound to the app via:
```xml
<service
    android:name=".UssdAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="false">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

## Usage

### Step 1: Build and Install
```bash
./gradlew build
./gradlew installDebug
```

### Step 2: Enable AccessibilityService
1. Open the app
2. Tap **"Enable Accessibility Service"**
3. Navigate to **Settings > Accessibility > (App Name)**
4. Toggle the service ON

### Step 3: Initiate USSD
- Use your phone's dial app or another method to initiate a USSD balance check (e.g., `*123#`)
- The AccessibilityService will:
  1. Detect the USSD pop-up window
  2. Extract the balance using regex
  3. Log the transaction to the Room database
  4. Check if balance meets the transfer condition (default: > 100)
  5. If yes, trigger V2 USSD transfer code (default: `*123*1#`)

### Step 4: View Logs
1. Tap **"View Transaction Logs"** in MainActivity
2. All transactions are displayed with: ID, Amount, Type, Status, Timestamp

## Core Components

### UssdAccessibilityService
**Key Methods:**
- `onAccessibilityEvent()`: Listens for window events
- `extractTextFromNode()`: Recursively extracts text from nodes
- `handleAutoFill()`: Auto-clicks buttons and fills fields (optional)
- `triggerSecondUssd()`: Dials the V2 USSD code if balance meets condition

### UssdHelper
**Key Methods:**
- `extractBalance(String ussdText)`: Uses regex to parse balance
  - Pattern: `(?:balance|amount|available)\s*(?:is|:)?\s*(?:KES|USD)?\s*([0-9]+(?:\.[0-9]{2})?)`
- `shouldTriggerTransfer(double balance, double threshold)`: Condition check
- `logTransaction()`: Records transaction to database

### Room Database
**Entity: TransactionLog**
- `id` (primary key, auto-generated)
- `amount` (double)
- `type` (String): "BALANCE_CHECK", "TRANSFER", etc.
- `status` (String): "SUCCESS", "FAILED", "PENDING", "TRIGGERED"
- `timestamp` (long): milliseconds since epoch

**DAO: TransactionLogDao**
- `insert()`: Add a log
- `getAllLogs()`: Fetch all logs ordered by timestamp DESC
- `getLogsByType()`: Filter logs by type
- `deleteAllLogs()`: Clear database

## Customization

### Adjust Balance Extraction Regex
Edit `UssdHelper.extractBalance()` if your provider uses a different format:
```java
Pattern pattern = Pattern.compile("Balance[:\\s]*([0-9]+(?:\\.[0-9]{2})?)");
```

### Change V2 Trigger Threshold
Edit `UssdAccessibilityService`:
```java
private static final double BALANCE_THRESHOLD = 100.0;  // Modify this
```

### Change V2 USSD Code
Edit `UssdAccessibilityService`:
```java
private static final String V2_USSD_CODE = "*123*1#";  // Replace with your transfer code
```

## Dependencies
- **androidx.appcompat:appcompat:1.6.1**
- **androidx.room:room-runtime:2.5.2**
- **androidx.room:room-compiler:2.5.2** (annotation processor)
- **junit:junit:4.13.2** (testing)

## Testing
Run unit tests:
```bash
./gradlew test
```

Run instrumented tests:
```bash
./gradlew connectedAndroidTest
```

## Notes
- **Permissions**: Request runtime permissions for `CALL_PHONE` on Android 6.0+ (API 23+)
- **Database**: Uses `allowMainThreadQueries()` for simplicity; use background threads for production
- **Accessibility Service**: Must be manually enabled by the user in system settings
- **USSD Hijacking**: Be aware of provider-specific behaviors and security implications

## License
This is a reference implementation. Use responsibly and comply with local laws and regulations.
