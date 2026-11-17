# Business Mileage Tracker 1.0.194 (Build 194)

- Compactened the Trip Details More info card while retaining the existing Vehicle row and manual vehicle picker.
- Combined trip category and meaningful custom purpose into one inline value. Default Personal and Business purposes now display only their category; custom purposes are never changed in stored trip data.
- Combined average and maximum speed into a compact Speed value, paired with the trip's stored IRS rate.
- Added friendly tracking method labels and retained sync status, duration, notes, and existing edit/delete actions.

Verification: `testDebugUnitTest` passed 543/543 tests and `testReleaseUnitTest` passed 543/543 tests. The debug APK is Build 194 (`com.businesstracker.mileage.debug`), signed with the existing certificate SHA-256 `43efd34a84878e7aa8290b276f65d3171b79965d05de9223f86d8c4dd6901948`.
