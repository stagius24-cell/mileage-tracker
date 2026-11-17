# Business Mileage Tracker 1.0.193 (Build 193)

- Moved the Vehicle field from its separate Trip Details card into the top of the existing More info card.
- Kept existing two-column trip fields and route/More info selector unchanged.
- The row resolves the current friendly vehicle name by saved trip vehicle ID and its pencil opens the existing manual vehicle picker.
- Unknown trips remain labeled `Unknown vehicle`; manual assignment updates only that trip's vehicle ID.

Verification: focused `VehicleBluetoothMatchPolicyTest` passed 5/5 tests; debug APK assembled and its signature, package ID, and versionCode were verified. Full debug/release test suites and APK checks are running in GitHub Actions.
