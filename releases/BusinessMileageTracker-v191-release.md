# Business Mileage Tracker 1.0.191 (Build 191)

## Bluetooth-based vehicle identification

- Added a saved vehicle directory with optional paired Bluetooth device association by stable device address.
- Matches connected devices asynchronously after tracking starts and on Bluetooth connection events; vehicle lookup does not delay GPS startup.
- Persists a nullable vehicle ID on active and completed trips. Existing trips remain unassigned unless the user manually selects a vehicle.
- Shows the identified vehicle in active tracking and adds an editable vehicle row to Trip Details.
- Handles unknown or ambiguous devices without guessing and preserves a trip's vehicle assignment after Bluetooth disconnects.
- Adds a non-destructive Room 8→9 migration.

## Verification

- Debug unit suite: 535 tests, 0 failures.
- Release unit suite: 535 tests, 0 failures.
- Debug APK: signed with the existing certificate; package `com.businesstracker.mileage.debug`, versionCode 191.
- Release variant assembly succeeded with lint-vital excluded because the local environment ran out of disk while fetching lint's compiler dependency. The release variant is unsigned under the current local Gradle configuration; the signed debug APK is the update-compatible artifact used by the project workflow.
- Physical-device installation and Bluetooth behavior were not tested in this environment.
