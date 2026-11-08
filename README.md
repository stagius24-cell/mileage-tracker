# Business Mileage Tracker v1.0.54

### Release Notes (v1.0.54):
- **Dashboard Review Card Redesign**:
  - Removed top car icon.
  - Organized data into a clean 3-column layout matching Trip Detail:
    - **Column 1:** Date (`NOV 8` / `2026`).
    - **Column 2:** Miles (`35.7` / `MILES`).
    - **Column 3:** Potential deduction (`$27.10` / `POTENTIAL` in green).
  - Moved start and destination timestamps to the bottom on the far right of each address row with dividers so they never overlap.
- **Google Sheets Sync Confirmation**: Surfaces an instant confirmation banner under the sync controls showing clear status (e.g., *Synced X trip(s) successfully!* or *All trips are up to date.*).
- **Sync Timestamp Refresh on Zero Pending Trips**: Updates "Last sync" to current timestamp on manual sync even when 0 trips are pending.
- **Spreadsheet ID Quick-Clear Button**: One-tap clear button inside the Spreadsheet ID text field without backspace jumping.
- **Continuous Signing Identity**: Signed with persistent debug keystore (SHA-256: `43efd34a...`) for seamless in-place updates.

- **Source commit:** `e4b407367552668d6c0a27a107fd392337222660`
- **CI run:** https://github.com/stagius24-cell/mileage-tracker/actions/runs/42537146245
- **Package:** `com.businesstracker.mileage.debug`
- **Version:** `1.0.54` (Code `55`)
- **APK SHA-256:** `9046c748050d60297aa488573ede19e9ca22813664832ff9d1af163e6f5babf1`

Direct download:
[BusinessMileageTracker-v1.0.54.apk](https://github.com/stagius24-cell/mileage-tracker/raw/refs/heads/downloads/v1.0.54-release/BusinessMileageTracker-v1.0.54.apk)
