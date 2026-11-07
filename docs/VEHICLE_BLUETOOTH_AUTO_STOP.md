# Vehicle Bluetooth Auto-stop

Version 1.0.4 adds an optional secondary stop signal for automatically detected drives.

## Behavior

- The option is off by default.
- The user enables **Stop Using Vehicle Bluetooth** and selects one paired vehicle.
- A disconnect from that exact device starts the existing four-minute stop debounce.
- Reconnecting the selected vehicle before the debounce finishes cancels the stop.
- Disconnects from other paired devices are ignored.
- Manually started drives are not affected.
- Disabling the option invalidates a pending Bluetooth-triggered stop.

The existing 200-meter drive confirmation, GPS tracking, activity-transition detection, and four-minute debounce are unchanged.

## Device validation

1. Pair the phone with the vehicle before opening Settings.
2. Enable the option and select the vehicle from the paired-device list.
3. Start an automatically detected drive and travel more than 200 meters.
4. Turn off the vehicle and verify the Bluetooth connection disconnects.
5. Verify the drive remains active during the debounce and then finalizes automatically.
6. Repeat, reconnecting before four minutes, and verify the drive continues.
7. Disconnect headphones or another paired device and verify the drive continues.

Physical-device validation is required because Bluetooth broadcast timing and vehicle head-unit behavior vary by manufacturer.
