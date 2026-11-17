# Build 198 — Compact Vehicle row

- Removed the extra vertical padding from the Vehicle row.
- The row now sizes to its 48dp accessible edit control, reducing unused blank space.
- Vehicle selection, text truncation, and other Trip Details content remain unchanged.

## Verification

- Debug unit tests: 543 passed, 0 failed.
- Release unit tests: 543 passed, 0 failed.
- Debug APK built successfully: package `com.businesstracker.mileage.debug`, version 1.0.198 / code 198.
- Signing certificate SHA-256: `43efd34a84878e7aa8290b276f65d3171b79965d05de9223f86d8c4dd6901948`.

Physical-device visual verification was not available.
