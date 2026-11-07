Update for the APK from GitHub Actions run #96.

- Fixes WorkManager startup so the Hilt-injected four-minute vehicle-exit worker can be created.
- Based on run #96 commit 3da7b1b45280c75a23192e2ca8eeddd45a2c4cc5, with no Room schema or data migration changes.
- Package: com.businesstracker.mileage.debug; version 1.0.1 (code 2).
- Final APK signed locally with run #96's original debug signing key and verified using Google's APK signature library. No private signing key was uploaded.
- Adds emulated application shutdown cleanup so startup work cannot outlive the Robolectric context.
- Debug/release unit tests and debug assembly are checked in the linked CI run. Physical-device automatic-stop validation is still required.

Download BusinessMileageTracker-1.0.1-run96-update.apk from Assets and install it over your existing run #96 app. Do not uninstall or clear app data. If installation is still rejected, keep the original app installed and report the error.

CI: https://github.com/stagius24-cell/mileage-tracker/actions/runs/42343661557

The CI intermediate APK is not the signed update; use the APK attached here.
