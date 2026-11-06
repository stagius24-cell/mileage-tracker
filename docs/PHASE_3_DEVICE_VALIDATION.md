# Phase 3 — Physical-Device Validation Plan

**Application:** Business Mileage Tracker (`com.businesstracker.mileage`)  
**Baseline Commit:** `e58359a3a1eaf3ef48ed593936292b7503e1edf2`  
**Branch:** `feature/phase-2-dashboard-history-sync`  
**Target Environment:** Physical Android Devices (Android 10 - Android 15 / API 29 - 35)

---

## 1. Overview and Operational Rules

This document governs the empirical physical-device validation of Phase 2 features (Dashboard, Trip History & Review, Reports, Google Sheets Sync, Keystore OAuth Security, and Android Backup Exclusions) alongside Phase 1.1 real-world GPS tracking invariants.

### Safety and Security Constraints
- **Driving Safety:** NEVER interact with or observe the test device while operating a motor vehicle. Place the device securely in a dock or have an assistant operate the application. Observe stationary and stop conditions safely.
- **Credential Protection:** NEVER log, capture, or commit real OAuth access tokens, refresh tokens, Google account passwords, or personal location coordinates.
- **Dedicated Test Environment:** All Google Sheets sync tests MUST use a non-production Google test account and a dedicated test spreadsheet. Never connect production financial spreadsheets.
- **Truth-in-Testing:** Tests not physically executed must remain `NOT TESTED`. Do NOT mark any test `PASS` without empirical evidence.

---

## 2. Test Environment & Prerequisites

1. **Hardware:**
   - Primary test device (running Android 12, 13, 14, or 15).
   - Secondary test device (for upgrade or cross-device backup/restore verification).
   - Test vehicle with functional GPS reception.
2. **Software & Accounts:**
   - Phase 2 Debug APK (`BusinessMileageTracker-debug.apk`, SHA: `e58359a`).
   - Dedicated Google Test Account with Google Play Services.
   - Dedicated Google Sheets spreadsheet with write permissions.
   - USB debugging enabled with `adb` access.

---

## 3. Detailed Physical-Device Test Cases

### Section A: Installation and Upgrade

#### P3-001 — Fresh Installation
- **Purpose:** Verify clean installation, initial database creation, and dashboard launch without crashes.
- **Prerequisites:** Device has no previous version of `com.businesstracker.mileage` installed.
- **Exact Steps:**
  1. Install APK via ADB: `adb install app-debug.apk`.
  2. Launch application from launcher icon.
  3. Observe initial splash, dashboard rendering, navigation bar, and default state.
  4. Navigate between Dashboard, History, Reports, and Settings.
- **Expected Result:** App launches cleanly (<2s), shows zero mileage, default vehicle, and navigation responds without crashes or ANRs.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

#### P3-002 — Upgrade Installation
- **Purpose:** Verify SQLite/Room database schema migration without data loss or crashes during in-place upgrade.
- **Prerequisites:** Device has a previous Phase 1 / Phase 1.1 build installed containing existing trip records.
- **Exact Steps:**
  1. Record existing trip count, dates, and total miles in baseline build.
  2. Install Phase 2 APK over existing build: `adb install -r app-debug.apk`.
  3. Launch application.
  4. Inspect Trip History, Dashboard totals, and Settings.
- **Expected Result:** Database opens seamlessly (`MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4`), all existing trips are preserved, classification statuses remain intact, and no crashes occur.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

---

### Section B: Google Authentication

#### P3-003 — Google Sign-in
- **Purpose:** Verify interactive OAuth 2.0 flow via Google Play Services and secure credential storage in hardware Keystore.
- **Prerequisites:** P3-001 completed; device has active network and Google Play Services.
- **Exact Steps:**
  1. Open Settings -> Google Sheets Integration card.
  2. Tap "Connect Google Account".
  3. Select Google test account from system dialog and grant spreadsheet scope permissions.
  4. Observe UI response upon completion.
- **Expected Result:** System consent dialog completes cleanly, UI transitions to "Google Account Connected" showing user email, token is encrypted into Android Keystore, and NO raw Bearer token is visible in UI or logcat.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

#### P3-004 — Authentication Expiration
- **Purpose:** Verify bounded token refresh (max 1 retry) and fail-closed behavior on recurring 401.
- **Prerequisites:** P3-003 completed; test account authorization revoked remotely via Google Account security settings (`myaccount.google.com/permissions`).
- **Exact Steps:**
  1. Revoke app access in Google Account security dashboard.
  2. In app Settings, tap "Sync Pending Trips".
  3. Inspect logcat and Settings screen banner.
- **Expected Result:** App attempts exactly ONE refresh; on persistent 401, it halts immediately (no infinite loop), returns `TokenExpired`, updates status to "Auth Expired" in red, prompts reconnect, and all un-synced trips remain pending.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

---

### Section C: Trip Review and Classification

#### P3-005 — Manual Trip Review
- **Purpose:** Verify swipe/detail review flow, classification persistence, and business entity association.
- **Prerequisites:** At least one unreviewed trip present in History.
- **Exact Steps:**
  1. Open History tab.
  2. Select unreviewed trip card to open Trip Detail screen.
  3. Select classification: "Business".
  4. Select business entity: "MySucculentCorner".
  5. Enter purpose: "Client Delivery", add note: "Delivered plant order".
  6. Tap "Save Classification".
  7. Force-stop app via `adb shell am force-stop com.businesstracker.mileage` and relaunch.
- **Expected Result:** Trip persists with `BUSINESS`, `MySucculentCorner`, updated tax deduction, and displays in History as reviewed.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

#### P3-006 — Personal Classification
- **Purpose:** Verify personal classification excludes miles from business totals and tax deductions.
- **Prerequisites:** An unreviewed trip in History.
- **Exact Steps:**
  1. Swipe card left or open details and select "Personal".
  2. Confirm classification.
  3. Inspect Dashboard summary and IRS deduction metrics.
- **Expected Result:** Trip classification updates to `PERSONAL`; business miles and calculated deduction for this trip remain 0.00; trip count under Personal increments by 1.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

#### P3-007 — Unreviewed Trip Exclusion
- **Purpose:** Ensure strict business rule that unreviewed trips are never synced to Google Sheets.
- **Prerequisites:** Valid Google Sheets connection, at least 1 `UNREVIEWED` trip and 1 reviewed `BUSINESS` trip.
- **Exact Steps:**
  1. Trigger "Sync Pending Trips".
  2. Inspect sync result toast/banner and test Google Sheet rows.
- **Expected Result:** Only the reviewed `BUSINESS` trip is submitted. The `UNREVIEWED` trip is ignored, remains pending in app, and does not appear on Google Sheets.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

---

### Section D: Google Sheets Synchronization

#### P3-008 — Successful Synchronization
- **Purpose:** Verify end-to-end sync appends all 16 canonical columns with precise data formatting.
- **Prerequisites:** Test Google account connected; valid test Spreadsheet ID configured.
- **Exact Steps:**
  1. Review a Business trip (e.g. 15.2 miles, MySucculentCorner).
  2. Tap "Sync Pending Trips".
  3. Open Google Spreadsheet in browser.
- **Expected Result:** Header row (Trip ID, Date, Start Time, End Time, Start Address, End Address, Total Miles, Business Miles, Classification, Business Entity, Purpose, Mileage Rate, Estimated Deduction, Notes, Tracking Method) is present; trip data row appears with exact matching values; trip marked `SYNCED` locally.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

#### P3-009 — Duplicate Prevention
- **Purpose:** Verify idempotency and row-update logic when sync is re-triggered.
- **Prerequisites:** P3-008 completed (trip already marked synced or updated).
- **Exact Steps:**
  1. Edit notes on previously synced trip in app.
  2. Trigger "Sync Pending Trips".
  3. Inspect Google Sheet.
- **Expected Result:** The existing row is updated in-place via PUT `Sheet1!A{row}:P{row}`. No duplicate row is appended. Row count remains identical.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

#### P3-010 — Partial Failure
- **Purpose:** Verify partial sync failure handling and pending preservation.
- **Prerequisites:** 2 pending trips; Sheet permissions or simulated cell constraint causing secondary failure.
- **Exact Steps:**
  1. Queue 2 eligible trips for sync.
  2. Execute sync under condition where 1 row update succeeds and 1 fails.
- **Expected Result:** Result displays `PartialSuccess (1 succeeded, 1 failed)`. Successful trip marked `SYNCED`; failed trip remains `PENDING`.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

#### P3-011 — Network Failure
- **Purpose:** Verify offline resilience and zero false sync confirmations.
- **Prerequisites:** Pending reviewed trips present.
- **Exact Steps:**
  1. Enable Airplane mode on test device.
  2. Tap "Sync Pending Trips".
  3. Verify error presentation.
  4. Disable Airplane mode (reconnect Wi-Fi/cellular) and tap "Sync Pending Trips".
- **Expected Result:** In offline mode, app displays `NetworkError`, 0 trips marked synced. Upon reconnection, sync succeeds and all pending trips are uploaded.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

#### P3-012 — Invalid Configuration
- **Purpose:** Verify pre-flight validation of malformed spreadsheet IDs and webhook URLs.
- **Prerequisites:** Settings screen open.
- **Exact Steps:**
  1. Enter invalid webhook URL: `http://insecure.example.com` or `https:///no-host`.
  2. Attempt sync.
  3. Enter valid HTTPS webhook URL and verify normal pre-flight.
- **Expected Result:** Invalid URLs rejected immediately with `InvalidConfiguration` before any network connection is attempted. Error banner displayed to user.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

---

### Section E: GPS and Background Tracking

#### P3-013 — Permission Setup
- **Purpose:** Verify fine location, background location ("Allow all the time"), and notification permissions flow.
- **Prerequisites:** Fresh install (P3-001).
- **Exact Steps:**
  1. Launch app and toggle "Auto-Tracking".
  2. Step through permission dialogs: Fine Location -> Allow while using app -> Background Location -> Allow all the time -> Post Notifications.
- **Expected Result:** UI guidance clearly directs user to system settings for background location; permissions granted enable the Foreground Service.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

#### P3-014 — Real Vehicle Trip
- **Purpose:** Verify end-to-end auto-tracking, GPS waypoint ingestion, distance calculation, and trip finalization.
- **Prerequisites:** Permissions granted; Auto-tracking enabled; vehicle stationary.
- **Exact Steps:**
  1. Place device in car dock.
  2. Drive a known 2-mile route at standard driving speeds (>15 mph).
  3. Park vehicle and remain stationary for >4 minutes.
  4. Inspect app History tab.
- **Expected Result:** Tracking notification activates during driving; trip finalizes automatically after exit debounce; route distance matches odometer within 5%; polyline and addresses display accurately.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

#### P3-015 — Screen-Off Tracking
- **Purpose:** Verify foreground location service continues undisturbed by Android Doze mode.
- **Prerequisites:** P3-013 completed.
- **Exact Steps:**
  1. Begin drive with screen off (device locked in pocket or dock).
  2. Drive for 10 minutes across multiple turns.
  3. Stop, unlock device, and view trip record.
- **Expected Result:** Waypoint stream is continuous without gaps; screen-off status did not cause location throttling or premature trip termination.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

#### P3-016 — Short Movement (<200m)
- **Purpose:** Verify the 200m continuous GPS confirmation rule rejects false driving starts.
- **Prerequisites:** Device stationary.
- **Exact Steps:**
  1. Walk or reposition vehicle under 100 meters (e.g. moving across a parking lot or driveway).
  2. Stop and remain stationary.
- **Expected Result:** No trip is created or finalized in database. Inbox fixes below confirmation threshold are safely discarded or kept unconfirmed.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

#### P3-017 — Continuous Movement Confirmation (>200m)
- **Purpose:** Verify trip is confirmed only after continuous displacement exceeding 200m.
- **Prerequisites:** Stationary vehicle.
- **Exact Steps:**
  1. Accelerate and drive continuously beyond 250 meters.
  2. Monitor notification status.
- **Expected Result:** Notification updates to "Tracking Drive"; tracking state transitions to `RECORDING`; initial 200m is retroactively included in trip trajectory.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

#### P3-018 — STILL_ENTER Behavior
- **Purpose:** Verify temporary stop (red light, stop sign, traffic jam) does not kill active trip.
- **Prerequisites:** Active trip in progress.
- **Exact Steps:**
  1. Stop at a red light for 90 seconds.
  2. Resume driving.
- **Expected Result:** Foreground service remains active; Activity Recognition `STILL_ENTER` does not abort trip; drive continues seamlessly as one single trip.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

#### P3-019 — Four-Minute Exit Debounce
- **Purpose:** Verify the 4-minute debounce timer before vehicle-exit trip finalization.
- **Prerequisites:** Active trip in progress.
- **Exact Steps:**
  1. Stop vehicle for 2 minutes (e.g. drive-through or quick pickup).
  2. Resume driving before 4 minutes elapsed.
- **Expected Result:** Trip does NOT finalize; debounce timer resets upon resumed movement; trip continues recorded as a single continuous trip.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

#### P3-020 — Confirmed Trip End
- **Purpose:** Verify trip finalizes exactly once when stationary period exceeds 4 minutes.
- **Prerequisites:** Active trip completed.
- **Exact Steps:**
  1. Park vehicle, turn off engine, remain stationary for 4 minutes 30 seconds.
  2. Observe notification and app state.
- **Expected Result:** Foreground notification dismisses; trip finalizes into Room database with end timestamp and reverse-geocoded end address; appears in Trip History exactly once.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

#### P3-021 — Process Interruption & Recovery
- **Purpose:** Verify durable GPS inbox and pending queue recovery after sudden process death.
- **Prerequisites:** Active drive in progress.
- **Exact Steps:**
  1. While driving, terminate app process via `adb shell am kill com.businesstracker.mileage`.
  2. Relaunch app.
- **Expected Result:** `GpsInboxProcessor` drains pending uncommitted fixes from Room table; trip state recovers or safely finalizes without database corruption or duplicate trip IDs.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

---

### Section F: Backup and Credential Security

#### P3-022 — Backup Exclusion Verification
- **Purpose:** Verify `mileage_secure_credentials.xml` is excluded from Android system backup.
- **Prerequisites:** Device connected to ADB; app installed and Google account connected (P3-003).
- **Exact Steps:**
  1. Force system backup via ADB:
     ```bash
     adb shell bmgr run
     adb shell bmgr backupnow com.businesstracker.mileage
     ```
  2. Inspect backup package contents or test device extraction rules.
- **Expected Result:** Backup succeeds for Room database and general preferences, but `mileage_secure_credentials.xml` is strictly omitted from cloud backup archive.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

#### P3-023 — Restore Behavior & Clean Reconnect State
- **Purpose:** Verify restored app on a new device without original Keystore key requires clean reconnect.
- **Prerequisites:** Backup generated (P3-022) or simulated restore.
- **Exact Steps:**
  1. Restore app data onto secondary device: `adb shell bmgr restore com.businesstracker.mileage`.
  2. Launch app on secondary device.
  3. Inspect History and Settings screen.
- **Expected Result:** Previous trips and vehicle settings are restored intact. Google Sheets integration displays "Not Connected" / "Connect Google Account"; no crashes occur from missing hardware keys; user can cleanly re-authenticate.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

---

### Section G: Reports and Calculations

#### P3-024 — Mileage Calculations & IRS Rate Accuracy
- **Purpose:** Verify accurate distance aggregation, Business vs. Personal separation, and 2026 standard mileage rate calculation ($0.76/mile).
- **Prerequisites:** Minimum 2 Business trips (e.g. 10.0 mi, 20.0 mi) and 1 Personal trip (15.0 mi) in History.
- **Exact Steps:**
  1. Open Dashboard screen.
  2. Inspect total business miles, personal miles, and tax deduction calculation.
- **Expected Result:** Business miles = 30.0 mi; Personal miles = 15.0 mi; IRS deduction = $22.80 ($30.0 * $0.760); Personal miles are 100% excluded from deduction.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

#### P3-025 — Reports & PDF Export
- **Purpose:** Verify date filtering, entity grouping, and PDF export generation.
- **Prerequisites:** P3-024 completed.
- **Exact Steps:**
  1. Open Reports & Export tab.
  2. Select date range (This Month), Entity filter ("MySucculentCorner").
  3. Tap "Export PDF Report".
  4. Open generated PDF document in system viewer.
- **Expected Result:** PDF generates cleanly in external storage / downloads; document contains accurate header, business entity name, trip count, itemized rows, and matching mileage/deduction totals.
- **Actual Result:** 
- **Status:** NOT TESTED
- **Evidence Reference:** 
- **Notes:** 

---

## 4. Release-Readiness Gate Checklist

Before Phase 3 can be marked `PASSED`, all 11 release gates must be fulfilled by physical-device test evidence:

- [ ] Gate 1: P3-001 Fresh Install & P3-002 Upgrade pass with zero crashes.
- [ ] Gate 2: P3-003 Real Google OAuth flow completes and stores keys in Keystore.
- [ ] Gate 3: P3-008 Reviewed Business trips sync correctly to Google Sheets.
- [ ] Gate 4: P3-009 Duplicate sync prevention confirmed via in-place update.
- [ ] Gate 5: P3-006 & P3-007 Unreviewed and Personal trips strictly excluded from sync.
- [ ] Gate 6: P3-015 Screen-off GPS tracking records continuous coordinates.
- [ ] Gate 7: P3-016 & P3-017 200m continuous GPS confirmation validated in vehicle.
- [ ] Gate 8: P3-019 & P3-020 4-minute exit debounce and single trip finalization verified.
- [ ] Gate 9: P3-021 Process interruption recovers pending GPS inbox records.
- [ ] Gate 10: P3-022 & P3-023 Backup exclusion and clean reconnect-required verified.
- [ ] Gate 11: P3-024 & P3-025 Mileage math ($0.76/mi) and PDF report generation confirmed.
