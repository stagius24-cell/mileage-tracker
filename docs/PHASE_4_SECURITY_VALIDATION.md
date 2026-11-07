# Phase 4 — Security & Data Protection Validation Plan

**Application:** Business Mileage Tracker (`com.businesstracker.mileage`)  
**Package:** `com.businesstracker.mileage`  
**Target Branch:** `feature/phase-4-security-data-protection`  
**Date:** 2026-11-06  
**Status:** VALIDATION IN PROGRESS  

---

## 1. Test Matrix Overview

| Test ID | Category | Title | Verification Level | Automated Test Name | Status |
| :--- | :--- | :--- | :---: | :--- | :---: |
| **SEC-001** | Component Security | AutoDriveReceiver Unexported in Manifest | Automated Unit Test | `ComponentSecurityTest.autoDriveReceiver_isNotExported_inManifest` | **PASS** |
| **SEC-002** | Component Security | Internal Services & FileProvider Unexported | Automated Unit Test | `ComponentSecurityTest.internalServicesAndReceivers_areNotExported` | **PASS** |
| **SEC-003** | Component Security | Explicit PendingIntent Routing for Transitions | Automated Unit Test | `ComponentSecurityTest.activityTransitionManager_usesExplicitIntent` | **PASS** |
| **SEC-004** | Component Security | Malformed Transition Discard Behavior | Automated Unit Test | `ComponentSecurityTest.autoDriveReceiver_discardsEmptyOrSpoofedIntents_gracefully` | **PASS** |
| **SEC-005** | Credential Security | Webhook URL Round-Trip Keystore AES-256 GCM | Automated Unit Test | `SecureStorageManagerTest.saveSheetsWebhookUrl_and_getSheetsWebhookUrl_roundTrip_success` | **PASS** |
| **SEC-006** | Credential Security | Cloudflare Bearer & Client Secret Keystore AES-GCM | Automated Unit Test | `SecureStorageManagerTest.saveCfBearerToken_and_saveCfClientSecret_roundTrip_success` | **PASS** |
| **SEC-007** | Credential Security | Fail-Closed on Keystore Key Mismatch / Invalidation | Automated Unit Test | `SecureStorageManagerTest.failClosed_whenKeyMismatch_returnsNullWithoutFallback` | **PASS** |
| **SEC-008** | Credential Security | Tampered Ciphertext Bad Tag Rejection | Automated Unit Test | `SecureStorageManagerTest.tamperedCiphertext_failsClosed_returnsNull` | **PASS** |
| **SEC-009** | Credential Security | OAuth Purge Leaves Webhook and Cloudflare Intact | Automated Unit Test | `SecureStorageManagerTest.clearOAuthCredentials_preservesWebhookAndCloudflareCredentials` | **PASS** |
| **SEC-010** | Migration Safety | Preference Migration Round-Trip & Plaintext Deletion | Automated Unit Test | `SensitivePreferencesMigrationTest.migrateSensitivePreferences_successfulRoundTrip_removesPlaintextFromDataStore` | **PASS** |
| **SEC-011** | Migration Safety | Migration Idempotency & Repeated Execution | Automated Unit Test | `SensitivePreferencesMigrationTest.migrateSensitivePreferences_idempotent_repeatedCallsPreserveData` | **PASS** |
| **SEC-012** | Migration Safety | Failure Recovery: Plaintext Preserved on Keystore Fault | Automated Unit Test | `SensitivePreferencesMigrationTest.migrateSensitivePreferences_whenKeystoreFails_preservesPlaintextInStore_andReportsError` | **PASS** |
| **SEC-013** | Migration Safety | Concurrent Migration Synchronization (Mutex) | Automated Unit Test | `SensitivePreferencesMigrationTest.migrateSensitivePreferences_concurrentCalls_doNotCorruptState` | **PASS** |
| **SEC-014** | Backup Hardening | Data Extraction Rules Exclude Credentials & Exports | Automated Unit Test | `BackupConfigurationTest.dataExtractionRules_excludesSecureCredentialsFromCloudAndTransfer` | **PASS** |
| **SEC-015** | Backup Hardening | Full Backup Content Excludes Credentials & Cache | Automated Unit Test | `BackupConfigurationTest.backupRules_excludesSecureCredentialsFromFullBackup` | **PASS** |
| **SEC-016** | Backup Hardening | Database Retained in Backup (Zero Data Loss) | Automated Unit Test | `BackupConfigurationTest.dataExtractionRules_excludesSecureCredentialsFromCloudAndTransfer` | **PASS** |
| **SEC-017** | Data Erasure | Atomic User Purge Wipes All Trip & Session Tables | Automated Unit Test | `UserControlledDeletionTest.deleteAllTripData_atomicallyPurgesAllTripAndSessionTables` | **PASS** |
| **SEC-018** | Data Erasure | User Purge Clears Export PDF/CSV Cache | Automated Unit Test | `UserControlledDeletionTest.deleteAllTripData_atomicallyPurgesAllTripAndSessionTables` | **PASS** |
| **SEC-019** | Data Erasure | User Purge Preserves Credentials & App Settings | Automated Unit Test | `UserControlledDeletionTest.deleteAllTripData_preservesCredentialsAndSettings` | **PASS** |
| **SEC-020** | Data Preservation | Google OAuth Disconnect Preserves Local SQLite Trips | Automated Unit Test | `UserControlledDeletionTest.disconnectingGoogle_doesNotDeleteLocalTripRecords` | **PASS** |
| **SEC-021** | Physical Device | Physical Two-Step Deletion Dialog on Device | Physical Samsung Galaxy | Manual Controlled Test Procedure (Section 2) | **PENDING PHYSICAL TEST** |
| **SEC-022** | Physical Device | Auto-Drive Tracking Delivery with Unexported Receiver | Physical Samsung Galaxy | Controlled Drive Test Procedure (Section 2) | **PENDING PHYSICAL TEST** |

---

## 2. Controlled Physical-Device Test Procedures

### Test Case SEC-021: Two-Stage Confirmation UI & Data Deletion
- **Prerequisites:** Physical device with at least 1 test trip recorded in History, and an exported PDF report generated.
- **Procedure:**
  1. Open app and navigate to **Settings** tab.
  2. Scroll to the new **Data Management & Privacy** section.
  3. Tap **Delete All Trip Data**.
  4. Verify **First Confirmation Dialog** appears with explanation that local records will be deleted while Google Sheets records are untouched.
  5. Tap **Cancel**. Verify nothing is deleted and dialog closes.
  6. Tap **Delete All Trip Data** again; tap **Continue**.
  7. Verify **Second Warning Dialog** appears stating permanent erase warning.
  8. Tap **Permanently Delete**.
  9. Verify spinner shows, success notification appears, and navigate to **History** tab.
- **Expected Results:**
  - History shows 0 trips.
  - Dashboard mileage drops to 0.0 mi.
  - Settings (OAuth connection status, spreadsheet ID, work schedule, default vehicle) remain 100% intact.

### Test Case SEC-022: AutoDriveReceiver Unexported Real Drive Delivery
- **Prerequisites:** Samsung Galaxy device with "Allow all the time" location and physical activity recognition permissions granted.
- **Procedure:**
  1. Ensure app is running or backgrounded.
  2. Perform a vehicle drive exceeding 200 meters.
  3. Observe whether `LocationTrackingService` starts tracking automatically upon Google Play Services activity transition callback.
- **Expected Results:**
  - `AutoDriveReceiver` receives Google Play Services transition callback cleanly through its explicit PendingIntent even with `android:exported="false"`.
  - Drive tracking operates with zero disruption.
