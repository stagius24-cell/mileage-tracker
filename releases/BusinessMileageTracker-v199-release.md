# Business Mileage Tracker 1.0.199 (Build 199)

## Bluetooth Vehicle Recognition Fix

- **Public Android APIs**: Switched from restricted `BluetoothManager.getConnectedDevices()` to `BluetoothProfile.ServiceListener` via `BluetoothAdapter.getProfileProxy()` for `BluetoothProfile.A2DP` and `BluetoothProfile.HEADSET`, plus standard GATT inspection.
- **Resource Cleanup**: Ensures all acquired profile proxies are closed immediately on result extraction, timeout (2000ms), cancellation, or error.
- **Pre-Connected Bluetooth Support**: Reliably detects car Bluetooth when connected prior to trip start.
- **Event-Driven & Delayed Re-checks**: Retries at bounded intervals (0s, 3s, 10s) upon trip startup if no vehicle was immediately assigned, avoiding persistent polling or battery drain.
- **Strict Disambiguation & Manual Precedence**: Requires an unambiguous match if multiple devices are connected; preserves manually selected vehicles and never overwrites them.
- **Compatibility**: Preserves Build 190 automatic tracking recovery and existing Bluetooth auto-stop triggers.

## Test Results
- **Debug Unit Tests**: 546 passed, 0 failures.
- **Release Unit Tests**: 546 passed, 0 failures.
- **New Regression Suite**: `VehicleBluetoothRecognitionTest` (10 scenarios passing).
- **Signing Key**: Build 96 debug certificate SHA-256 (`43efd34a84878e7aa8290b276f65d3171b79965d05de9223f86d8c4dd6901948`).

> **Note on Verification**: Automated test suites verify Android API adherence, lifecycle state transitions, delayed recognition, and permission handling. Physical confirmation on your Tesla Model Y hardware requires on-device testing.
