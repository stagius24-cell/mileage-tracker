# Business Mileage Tracker 1.0.192 (Build 192)

- Trip Details now resolves a trip's current friendly vehicle name from the persisted vehicle ID, so renaming a vehicle updates its displayed name without changing historical trip associations.
- Active tracking and Trip Details consistently display `Unknown vehicle` when identification is unavailable or unmatched.
- Added focused coverage for rename-by-ID resolution and unknown/missing vehicle IDs.

Verification: the focused `VehicleBluetoothMatchPolicyTest` suite passed 5/5 tests. The Build 191 full debug and release suites each passed 535/535 tests; GitHub Actions will repeat full validation for this update.

The debug APK is signed with the established certificate and retains package `com.businesstracker.mileage.debug` for an in-place update over Build 191.
