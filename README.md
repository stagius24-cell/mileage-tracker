# Business Mileage Tracker 1.0.16

This update compacts Trip Details to fit the summary, map, locations, and tax details on a normal phone screen with much less scrolling. The duplicate bottom edit button was removed because the pencil action remains in the header.

It also repairs missing start locations. When automatic tracking begins before Android provides an initial GPS fix, the app now uses the first valid recorded route waypoint as the origin. Existing trips with a `0,0` origin can use their recorded route to resolve an approximate start address without changing mileage or deleting trip data. Raw coordinates are no longer shown in the Trip Details fallback message.

Install this APK over the existing app. Do not uninstall the app or clear its data if you want to preserve local trip history.

- Source branch: `feature/compact-trip-details-start-address`
- Source commit: `318af79208214fbc57968bcb99db8d1548c1e15b`
- CI run: https://github.com/stagius24-cell/mileage-tracker/actions/runs/42407787440
- Package: `com.businesstracker.mileage.debug`
- Version: `1.0.16` (`17`)
- SHA-256: `a2bed840f8e733b4da7d8cd69a59e3d86cfaecb2feec8e7a20d881b30f2c31e8`
