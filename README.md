# Business Mileage Tracker 1.0.26

### Dashboard Compactness & UX Improvements:
- **Removed Dim Button:** Removed the Dim screen button from the card header so the title and GPS accuracy badge fit cleanly on a single line.
- **Removed Waypoints Under Speed:** Cleaned up the metrics bar to display only current speed without waypoint count.
- **Compacted Tracking Table Footprint:** Minimized all inner padding, card vertical spacing, metrics height, and action buttons (`40.dp`) for an ultra-compact table footprint.
- **Secondary Metrics Moved to Collapsible Sort Header:** Moved Total, Personal, and Unreviewed mileage into the collapsible sort drawer so the main view only shows the primary 3-stat bar (`DRIVES | BUSINESS MI | DEDUCTION`).
- **Signed with Persistent Key:** Signed with the persistent Run #96 / Run #99 debug certificate (`43efd34a...`) to allow seamless in-place updates over existing installations without Room database / trip record loss.

Install this APK over the existing app. Do not uninstall the app or clear its data if you want to preserve local trip history.

- **Source branch:** `main`
- **Source commit:** `6ccbee3bfe5a57088b93ea30232c4d9e03073041`
- **CI run:** https://github.com/stagius24-cell/mileage-tracker/actions/runs/42440232062
- **Package:** `com.businesstracker.mileage.debug`
- **Version:** `1.0.26` (Code `27`)
- **APK SHA-256:** `6581b2a0ed7694d8ad8ba8028bc87ceacc5bcd135525e02655a11b70425a3f2e`
- **Signer Certificate SHA-256:** `43efd34a84878e7aa8290b276f65d3171b79965d05de9223f86d8c4dd6901948`
- **Signer Certificate SHA-1:** `32d6afa108beed313e181a08176ded0fd960af43`

Direct download:
[BusinessMileageTracker-1.0.26-compact-dashboard.apk](https://github.com/stagius24-cell/mileage-tracker/raw/refs/heads/downloads/compact-dashboard-v1.0.26/BusinessMileageTracker-1.0.26-compact-dashboard.apk)
