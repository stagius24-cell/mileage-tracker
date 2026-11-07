# Sheets sync fix — v1.0.2

[Download BusinessMileageTracker-1.0.2-sheets-update.apk](https://github.com/stagius24-cell/mileage-tracker/raw/refs/heads/downloads/sheets-sync-v1.0.2/BusinessMileageTracker-1.0.2-sheets-update.apk)

Install this APK over the current app. Do not uninstall or clear app data. Your existing trips and the auto-stop fix are retained; no database schema or migration changes are included.

- Moves manual Sheets and webhook HTTP requests from Android's main thread to the IO dispatcher.
- Keeps the existing synchronization mutex, trip IDs, row matching, and confirmation contracts.
- Keeps failed trips pending and propagates cancellation.
- Sanitizes stored network error messages and explains the spreadsheet ID / Sheet1 checks on HTTP 400.
- Package com.businesstracker.mileage.debug; version 1.0.2 (code 3).
- Signed locally with the verified original key from run #96. No private key is included in this branch.

CI: https://github.com/stagius24-cell/mileage-tracker/actions/runs/42344422801

Automated tests use synthetic trips and intercepted HTTP requests. Live Google account authorization and a real Sheet1 upload must still be checked on the phone. The user's spreadsheet has not been accessed by this work.

After installation, open Settings and tap Sync Sheets again. Verify the trip appears once in Sheet1 and Pending Uploads reaches zero. If it fails, preserve the app data and report the new error.
