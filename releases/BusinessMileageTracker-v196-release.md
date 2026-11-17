# Business Mileage Tracker 1.0.196 (Build 196)

- Put Vehicle, its friendly name, and the edit action on a single horizontal row. Long names truncate before the accessible edit button.
- Increased the three two-column information rows' vertical padding from 5dp to 6dp.
- Kept average and maximum speed together inline, with two-line wrapping on narrow layouts.
- Removed redundant bottom padding and the gap between edit/delete actions; both retain 48dp touch targets.
- Preserved vehicle selection, trip data, classification, tracking, and synchronization behavior.

Verification: `testDebugUnitTest` passed 543/543 tests and `testReleaseUnitTest` passed 543/543 tests. The debug APK is Build 196 (`com.businesstracker.mileage.debug`), signed with the existing certificate SHA-256 `43efd34a84878e7aa8290b276f65d3171b79965d05de9223f86d8c4dd6901948`.
