# Settings, street maps, and addresses — v1.0.3

[Download BusinessMileageTracker-1.0.3-update.apk](https://github.com/stagius24-cell/mileage-tracker/raw/refs/heads/downloads/settings-map-address-v1.0.3/BusinessMileageTracker-1.0.3-update.apk)

Install over your current app. Do not uninstall or clear data.

- Concise Settings: Google Sheets controls remain; Cloudflare and Apps Script webhook controls and descriptions are removed. Battery settings becomes one configuration button.
- Trip Details now shows OpenStreetMap street tiles beneath the saved GPS route, with start/end markers, zoom/pan, and attribution. Map tiles need internet or existing cached coverage; failures display a clear message. History thumbnails remain lightweight route previews.
- Opening Trip Details retries missing start/destination addresses through Android's geocoder. Successful lookups fill only missing address fields. Existing addresses, notes, coordinates, mileage, rates, and trip dates are preserved. If lookup is unavailable, the UI reports it and retains the recorded GPS fallback. Geocoding cannot correct an inaccurate original GPS fix.
- Existing auto-stop and manual Google Sheets threading fixes remain included.

Package: com.businesstracker.mileage.debug. Version: 1.0.3 (code 4).
Signed locally with the original run #96 key; no private key is included.
CI: https://github.com/stagius24-cell/mileage-tracker/actions/runs/42346672553

Automated verification uses synthetic trips, real Room in-memory databases, Android MapView overlays with network disabled, intercepted tile requests, and controlled Android geocoder callbacks. A single world-tile HTTPS probe checked provider reachability without disclosing any trip location. Live phone rendering, address accuracy, and upgrade behavior still require device validation.

After updating, confirm your history and totals remain intact, open Trip Details online, and verify streets and start address. If a lookup fails, reconnect and reopen Trip Details to retry.

Settings controls are removed; stored legacy configuration and legacy internal sync contracts remain for compatibility. No remote records were deleted or edited by this work.
