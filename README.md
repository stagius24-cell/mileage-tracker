# Business Mileage Tracker v1.0.63

### Release Notes (v1.0.63):
- **Single-Line "Create report" Button**:
  - Adjusted button padding, icon size, and typography with strict `maxLines = 1` so the button and text stay neatly on a single line on all screen widths.
- **2-Line Origin & Destination in PDF Reports**:
  - In the generated PDF table, addresses are split cleanly across 2 separate lines (`From: ...` and `To: ...`), preventing address clipping and ensuring beginning and ending destinations are clearly legible.
- **Zoom In / Zoom Out in PDF Preview**:
  - Added dedicated Zoom In and Zoom Out controls in the preview header.
  - Added multi-touch pinch-to-zoom support (`detectTransformGestures`) allowing flexible scaling from 0.75x up to 3.5x.

- **Source commit:** `139a346429c29e4c00aea867a0a892a81e601b02`
- **CI run:** https://github.com/stagius24-cell/mileage-tracker/actions/runs/42629087579
- **Package:** `com.businesstracker.mileage.debug`
- **Version:** `1.0.63` (Code `64`)
- **APK SHA-256:** `395b83fe73f7d052af2ed8376c247e6917535ad1eacc6778f095617587d26787`

Direct download:
[BusinessMileageTracker-v1.0.63.apk](https://raw.githubusercontent.com/stagius24-cell/mileage-tracker/downloads/v1.0.63-release/BusinessMileageTracker-v1.0.63.apk)
