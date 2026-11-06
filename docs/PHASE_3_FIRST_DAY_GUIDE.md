# Phase 3 — First-Day Testing Guide

Welcome to the physical-device testing for **Business Mileage Tracker**! This guide is written specifically for you to test the app on your Samsung Galaxy phone. You do not need to be an Android developer—just follow these 10 step-by-step tests.

---

## Preparation & Prerequisites

1. **Your Phone:** A Samsung Galaxy phone running Android 10 or newer with an internet connection.
2. **Google Account:** A Google account with access to Google Drive / Google Sheets.
3. **Test Spreadsheet:** Go to [Google Sheets](https://sheets.new) on your computer or phone and create an empty spreadsheet named `Mileage Tracker Test`.
   - Copy the Spreadsheet ID from the URL (the long string between `/d/` and `/edit` in your browser address bar):  
     `https://docs.google.com/spreadsheets/d/`**`1BxiMVs0XRX5nZyGTwn...`**`/edit`
4. **Download the APK:**
   - [Direct GitHub Actions Artifact Link](https://github.com/stagius24-cell/mileage-tracker/actions/runs/42282825476/artifacts/13536237443)  
   - Download `BusinessMileageTracker-debug.zip` on your computer or phone, unzip it, and you will get `app-debug.apk`.

---

## How to Install on a Samsung Phone

1. **Transfer the APK to your phone:**
   - Download directly via your phone's browser, OR send `app-debug.apk` to your phone via USB cable, Quick Share, or Google Drive.
2. **Open Samsung "My Files" app:**
   - Tap **Downloads** (or **Installation files**).
   - Tap on `app-debug.apk`.
3. **Allow Installation from Unknown Sources (if prompted):**
   - If Samsung prompts *"For your security, your phone is not allowed to install unknown apps from this source"*, tap **Settings** on the prompt.
   - Toggle **Allow from this source** to ON.
   - Press the Back button.
4. **Complete Installation:**
   - Tap **Install** (or **Update** if you have an older test version installed).
   - If Google Play Protect shows *"Unrecognized app"*, tap **More details** -> **Install anyway** (standard for debug builds).
   - Once finished, tap **Done** (or **Open**).

---

## The 10 Initial First-Day Tests

---

### Test 1: Install the APK
* **What to do:** Install `app-debug.apk` following the Samsung installation steps above.
* **What you should see:** A message saying *"App installed"* with an **Open** button, and the app icon ("Business Mileage Tracker") appearing in your app drawer.
* **What counts as a failure:** An error like *"App not installed"*, *"Package corrupted"*, or installation crash.
* **What to send back:** A screenshot of any error message, or confirm *"Installed successfully"*.

---

### Test 2: Open the Application
* **What to do:** Tap the **Business Mileage Tracker** icon on your home screen or app drawer to launch it.
* **What you should see:** A splash screen briefly appears, followed smoothly by the main application screen without freezing.
* **What counts as a failure:** The app immediately crashes to the home screen (Force Close), goes completely blank/black, or shows an *"Application Not Responding"* (ANR) dialog.
* **What to send back:** Screenshot of the opened home screen, or screenshot of the crash dialog if it occurs.

---

### Test 3: Confirm the Dashboard Loads
* **What to do:** Look at the bottom navigation bar and ensure the **Dashboard** tab is selected. Look at the cards displayed on screen.
* **What you should see:**
  - A card showing mileage totals (e.g., Business Miles, Personal Miles).
  - Estimated tax deduction ($0.00 if brand new, or calculated value).
  - Quick action buttons ("Start Drive", "Review Trips").
* **What counts as a failure:** Broken or missing UI text, numbers displaying as `NaN` or `null`, overlapping buttons, or visual glitches.
* **What to send back:** Screenshot of your Dashboard screen.

---

### Test 4: Confirm Existing Trips & Settings Are Preserved
* **What to do:**
  - If you updated over an earlier build: Tap the **History** tab at the bottom.
  - If this is a fresh install: Verify the History screen shows an empty state message (e.g. *"No trips recorded yet"*).
* **What you should see:**
  - Existing trips from earlier versions are still visible with their dates and distances intact.
  - No database migration crash.
* **What counts as a failure:** Previous trips disappeared, or the app crashed when tapping the History tab.
* **What to send back:** Screenshot of the History screen.

---

### Test 5: Grant Required Permissions
* **What to do:**
  - Tap the **Settings** tab at the bottom.
  - Toggle **Auto-Tracking** to ON.
  - When Android asks for Location, select **"While using the app"**, then when prompted for background access, choose **"Allow all the time"** in system settings.
  - If asked for Notifications, tap **"Allow"**.
* **What you should see:**
  - Clear guidance explaining why background location is needed.
  - Once granted, the settings screen shows green checkmarks or "Ready" for auto-tracking.
* **What counts as a failure:** Endless permission loop where it keeps asking even after you selected "Allow all the time", or a crash when opening system permission settings.
* **What to send back:** Screenshot of Settings showing permissions status.

---

### Test 6: Connect Google Sign-in
* **What to do:**
  - In **Settings**, scroll down to the **Google Sheets Integration** card.
  - Tap **"Connect Google Account"**.
  - A Google account selection dialog will pop up. Tap your Google test account.
  - Read the consent screen and tap **"Continue"** or **"Allow"** to grant Google Sheets permission.
* **What you should see:**
  - The dialog closes, and the card updates to show:
    - Status: **"Connected"** (or a green checkmark).
    - Connected account email: your Google email address.
    - Button changes to **"Disconnect"**.
  - **Crucial:** No raw access token or private key string should be displayed on screen.
* **What counts as a failure:** Sign-in dialog loops or fails silently, displays an authentication error, or does not update the UI to "Connected".
* **What to send back:** Screenshot of the Settings screen showing the "Google Account Connected" card with your email.

---

### Test 7: Configure a Dedicated Test Spreadsheet
* **What to do:**
  - In the same Google Sheets section of **Settings**, find the **Spreadsheet ID** text field.
  - Paste your test Spreadsheet ID (the string copied in the Preparation section).
  - Tap outside the text box or press Enter to save.
* **What you should see:**
  - The ID remains saved in the text field.
  - The sync card indicates it is ready to sync.
* **What counts as a failure:** The text box rejects the paste, clears automatically, or crashes the app.
* **What to send back:** Screenshot of the filled Spreadsheet ID field.

---

### Test 8: Sync One Reviewed Business Trip
* **What to do:**
  - If you have an unreviewed trip in History: Tap it, select **Business**, select **MySucculentCorner**, enter purpose **"Test Delivery"**, and tap **Save**.
  - *(If you have no trips yet, tap "Add Trip Manually" or complete a quick drive, then classify it as Business).*
  - Go to **Settings** and tap the **"Sync Pending Trips"** button (or tap "Sync" on the Dashboard).
  - Wait 3–5 seconds for the sync to finish.
* **What you should see:**
  - A success toast or message saying *"Successfully synced 1 trip"* (or similar).
  - The "Pending Trips" counter drops by 1.
  - "Last Synced" timestamp updates to "Just now".
* **What counts as a failure:** An error banner saying "Sync Failed", or the trip remaining in "Pending" status after sync.
* **What to send back:** Screenshot of the sync completion banner/message in Settings.

---

### Test 9: Verify Row Appears in Google Sheets
* **What to do:**
  - Open your test spreadsheet in a web browser on your computer or phone.
* **What you should see:**
  - Row 1 contains the headers:
    `Trip ID`, `Date`, `Start Time`, `End Time`, `Start Address`, `End Address`, `Total Miles`, `Business Miles`, `Classification`, `Business Entity`, `Purpose`, `Mileage Rate`, `Estimated Deduction`, `Notes`, `Tracking Method`.
  - Row 2 contains your trip's exact details matching the app (e.g. `BUSINESS`, `MySucculentCorner`, correct miles and tax deduction).
* **What counts as a failure:** The sheet is completely empty, headers are missing, or values are placed in the wrong columns.
* **What to send back:** Screenshot or photo of your Google Sheet showing Rows 1 and 2.

---

### Test 10: Sync Again & Verify Duplicate Prevention
* **What to do:**
  - Go back to the app.
  - In **Settings**, tap **"Sync Pending Trips"** again without changing anything.
  - Once finished, refresh your Google Sheet in the browser.
* **What you should see:**
  - The app reports *"0 trips synced"* or *"Already up to date"*.
  - In Google Sheets, there is still **only one data row** (Row 2). No duplicate Row 3 was added.
* **What counts as a failure:** A duplicate row with the same trip was appended to your Google Sheet.
* **What to send back:** Screenshot of your Google Sheet confirming only 1 data row is present.

---

## How to Record Your Results in `docs/PHASE_3_RESULTS.md`

After you finish testing, open [`docs/PHASE_3_RESULTS.md`](PHASE_3_RESULTS.md) in your editor and update the table:

1. **Device Information:** Fill in your Samsung model and Android version at the top.
2. **Status Column:** Change `NOT TESTED` to:
   - `PASS` if the test behaved exactly as expected.
   - `FAIL` if the test failed or gave an error.
   - `BLOCKED` if you could not complete it because of a previous failure.
3. **Evidence Link:** Add a note or link to your screenshot (e.g. `screenshot_test_08.png`).

---

## How to Report a Problem

If any test fails or something unexpected happens, simply reply with:

1. **Test Number:** (e.g., "Test 6 failed")
2. **What happened:** (e.g., "After selecting my account, it showed an error banner saying...")
3. **Screenshot:** Attach the screenshot showing the error.
4. **Phone Model:** (e.g., Samsung Galaxy S23, Android 14).

Codex will inspect the exact issue, explain why it happened, and guide you on the next step!
