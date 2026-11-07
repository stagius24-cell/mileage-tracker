# Business Mileage Tracker 1.0.21

### Updates & Layout Improvements:
- **Trip Details 3-Column Top Stats:**
  - Divided the top summary card into 3 balanced columns with vertical divider lines in between.
  - **Left column:** Drive Category chip (`PERSONAL` / `BUSINESS`) and Sync status (`SYNCED` / `PENDING` with cloud icon).
  - **Middle column:** Total Distance (`TOTAL DISTANCE` label, prominent mileage value, and `miles` unit).
  - **Right column:** Timestamp details (Formatted Date, Start–End Time range, and Drive Duration in minutes).
- **Background Contrast Refined:** Lightened the light theme background by 20% (`#E2E8F0` -> `#E8EDF4`, borders `#D5DEE7`), giving a clean, crisp, and comfortable contrast against white cards.
- **Signed with Persistent Key:** Signed with the persistent Run #96 / Run #99 debug certificate (`43efd34a...`) to allow seamless in-place updates over existing installations without Room database / trip record loss.

Install this APK over the existing app. Do not uninstall the app or clear its data if you want to preserve local trip history.

- **Source branch:** `main`
- **Source commit:** `d97cdde3e47e85c88e7a685cb5d9adbc95e7c37e`
- **CI run:** https://github.com/stagius24-cell/mileage-tracker/actions/runs/42425801845
- **Package:** `com.businesstracker.mileage.debug`
- **Version:** `1.0.21` (Code `22`)
- **APK SHA-256:** `c2495c917804d77febf85604853c9b6f89cf59521055f664515f08b1bbef1da1`
- **Signer Certificate SHA-256:** `43efd34a84878e7aa8290b276f65d3171b79965d05de9223f86d8c4dd6901948`
- **Signer Certificate SHA-1:** `32d6afa108beed313e181a08176ded0fd960af43`

Direct download:
[BusinessMileageTracker-1.0.21-trip-details-3col.apk](https://github.com/stagius24-cell/mileage-tracker/raw/refs/heads/downloads/trip-details-3col-v1.0.21/BusinessMileageTracker-1.0.21-trip-details-3col.apk)
