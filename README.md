# Business Mileage Tracker 1.0.22

### Dashboard Compactness & UX Improvements:
- **Compact Live Tracking Table:**
  - Removed empty white space: reduced card padding (`18.dp` -> `horizontal = 14.dp, vertical = 12.dp`), spacing (`14.dp` -> `10.dp`), and action button heights (`50.dp` -> `44.dp`).
  - Simplified tracking header: retains clean "Active Drive Tracking" title without redundant multi-line waypoint subtitle (waypoint count is already cleanly displayed in the metrics bar under SPEED).
  - Metrics bar tightened (`vertical = 8.dp`, LIVE MILES at 28sp).
- **Collapsible Time Filter Chips:**
  - Removed static `Today | This Week | This Month` chips from the permanent header view to maximize vertical space.
  - Replaced the duplicate reports icon with a dedicated **Sort/Filter** toggle button in the top right corner.
  - Tapping the sort button expands the time filter chips with smooth animation, and collapses them when not needed.
- **Trips to Review Hidden When Empty:**
  - The "Trips to Review" section and placeholder card are hidden by default when there are 0 unreviewed drives.
  - The section only appears when there are actual unreviewed drives requiring swipe categorization.
- **Signed with Persistent Key:** Signed with the persistent Run #96 / Run #99 debug certificate (`43efd34a...`) to allow seamless in-place updates over existing installations without Room database / trip record loss.

Install this APK over the existing app. Do not uninstall the app or clear its data if you want to preserve local trip history.

- **Source branch:** `main`
- **Source commit:** `a94bcb06e000412a8069566ce0d8d75a6cb4ff5c`
- **CI run:** https://github.com/stagius24-cell/mileage-tracker/actions/runs/42426854916
- **Package:** `com.businesstracker.mileage.debug`
- **Version:** `1.0.22` (Code `23`)
- **APK SHA-256:** `769edf4a2fce91bbd184a02f4f211f2a6b9b8f5710637f8fdebeb9b6e80648e5`
- **Signer Certificate SHA-256:** `43efd34a84878e7aa8290b276f65d3171b79965d05de9223f86d8c4dd6901948`
- **Signer Certificate SHA-1:** `32d6afa108beed313e181a08176ded0fd960af43`

Direct download:
[BusinessMileageTracker-1.0.22-compact-dashboard.apk](https://github.com/stagius24-cell/mileage-tracker/raw/refs/heads/downloads/compact-dashboard-v1.0.22/BusinessMileageTracker-1.0.22-compact-dashboard.apk)
