# Business Mileage Tracker v1.0.55

### Release Notes (v1.0.55):
- **Floating Version History Table Dialog**:
  - Tapping the app version row in Settings opens a floating modal table dialog.
  - Features 80% opacity surface (`alpha = 0.80f`), blurred background (`blur(8.dp)`), and dimmed backdrop (`Color.Black.copy(alpha = 0.55f)`).
  - Displays the last 5 app versions with clean 1-line update summaries that truncate cleanly without wrapping.
- **Dashboard Review Card Redesign**:
  - Removed top car icon.
  - Organized data into a clean 3-column layout matching Trip Detail: Date, Miles, Potential deduction.
  - Moved start and destination timestamps to the bottom on the far right of each address row with dividers.
- **Google Sheets Sync & ID Enhancements**:
  - Instant positive confirmation banner upon sync.
  - Sync timestamp refreshes even on zero pending trips.
  - Spreadsheet ID input includes a one-tap quick-clear button.

- **Source commit:** `3359eab227a3ada0e794a43cd2db57683c1b09c8`
- **CI run:** https://github.com/stagius24-cell/mileage-tracker/actions/runs/42538040526
- **Package:** `com.businesstracker.mileage.debug`
- **Version:** `1.0.55` (Code `56`)
- **APK SHA-256:** `5f6ae2a94a557a79865c2606e87f2890214798aeeb2145ab8493aadab6ba3898`

Direct download:
[BusinessMileageTracker-v1.0.55.apk](https://raw.githubusercontent.com/stagius24-cell/mileage-tracker/downloads/v1.0.55-release/BusinessMileageTracker-v1.0.55.apk)
