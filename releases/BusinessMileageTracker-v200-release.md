# Business Mileage Tracker — Build 200 Release Notes

**Version**: `1.0.200` (`versionCode = 200`)  
**Commit**: [`fc58c72`](https://github.com/stagius24-cell/mileage-tracker/commit/fc58c72f079b8931d4cd0455dfc501276ad4a1f0)  
**Git Tag**: [`v1.0.200`](https://github.com/stagius24-cell/mileage-tracker/releases/tag/v1.0.200)  
**Signing Key**: Run 96 / Build 96 stable keystore (`43:EF:D3:4A:84:87:8E:7A:A8:29:0B:27:6F:65:D3:17:1B:79:96:5D:05:DE:92:23:F8:6D:8C:4D:D6:90:19:48`)

---

## 1. Summary of Changes

### A. Settings UI Clean Up
- **Removed specific car picker from "Bluetooth auto-stop"**: In [SettingsScreen.kt](file:///C:/Users/stagi/.gemini/antigravity/scratch/mileage-tracker/app/src/main/java/com/businesstracker/mileage/ui/settings/SettingsScreen.kt#L303-L335), removed the dropdown and subtitle picker from the Bluetooth auto-stop row.
- The row now matches the clean design seen across the app (icon, title `"Bluetooth auto-stop"`, and on/off toggle switch).
- Individual car Bluetooth pairings remain cleanly managed inside the **Vehicle** management directory dialog.

### B. Universal Car Bluetooth Auto-Stop
- In [VehicleBluetoothEventHandler.kt](file:///C:/Users/stagi/.gemini/antigravity/scratch/mileage-tracker/app/src/main/java/com/businesstracker/mileage/receiver/VehicleBluetoothEventHandler.kt#L25-L60), auto-stop is no longer restricted to a single pre-selected device.
- Disconnecting **any saved vehicle's Bluetooth device** (or the active trip's associated vehicle) now immediately triggers the safe auto-stop debounce process.
- Reconnecting any saved vehicle Bluetooth device cancels debounce and keeps the active trip running seamlessly.

---

## 2. Test Verification

- **Full Debug Unit Tests**: Passed (all 548/548 tests passing)
- **Full Release Unit Tests**: Passed (all 548/548 tests passing)
- **New Regression Tests**:
  - `anySavedCarDisconnect_startsSafeStopSignal`: Verified that disconnecting a saved vehicle not set as the default device correctly initiates auto-stop debounce.
  - `anySavedCarReconnect_cancelsPendingStop`: Verified that reconnecting any saved car Bluetooth device cancels debounce and restores the in-vehicle state.
  - `otherDeviceDisconnect_isIgnored`: Verified that non-vehicle Bluetooth devices (headphones, smartwatches, etc.) do not trigger auto-stop.

---

## 3. APK Artifacts & Downloads

- **Direct Download (Build 96 Key)**: [BusinessMileageTracker-v200-build96key.apk](file:///C:/Users/stagi/.gemini/antigravity/brain/767ccd81-a9a9-4b56-9fea-40027e7d8a61/BusinessMileageTracker-v200-build96key.apk)
- **Direct Download (Main Build)**: [BusinessMileageTracker-main-v200.apk](file:///C:/Users/stagi/.gemini/antigravity/brain/767ccd81-a9a9-4b56-9fea-40027e7d8a61/BusinessMileageTracker-main-v200.apk)
- **GitHub Repository**: [stagius24-cell/mileage-tracker](https://github.com/stagius24-cell/mileage-tracker)
- **GitHub Actions Build Run**: [Workflows / Build & Test](https://github.com/stagius24-cell/mileage-tracker/actions)
