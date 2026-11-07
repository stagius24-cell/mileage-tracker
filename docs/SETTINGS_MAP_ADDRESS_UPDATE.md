# Settings, street maps, and endpoint addresses

This update retains the auto-stop and manual Sheets sync fixes. No Room schema,
mileage calculation, stored coordinate, or tracking-threshold changes are included.

## Changes

- Settings removes the Cloudflare card and Apps Script webhook field. Google
  Sheets account controls, spreadsheet ID, sync status, and one sync button remain.
  The battery section becomes a single configuration button. Explanatory panels
  and duplicate disconnect controls are removed.
- Trip Details displays OpenStreetMap street tiles with the recorded polyline,
  start/end markers, zoom/pan, and visible attribution. History list thumbnails
  retain their lightweight route previews. Missing waypoint data shows markers
  rather than inventing a route between endpoints.
- Tiles use HTTPS, an identifying user agent, private app cache storage, server
  cache lifetimes, and viewport-only requests. The custom downloader and cache writer avoid the
  SDK's default tile URL/index logging on network and cache-write failures. No extra Android permissions or API key
  are required. Map requests disclose the viewed tile area to OpenStreetMap.
- Opening Trip Details retries missing endpoint addresses through Android's
  geocoder. Conditional SQL fills only missing address fields if the stored
  endpoints still match; it does not replace other edits or recreate deleted
  trips. Already-synced rows are not automatically sent to Google Sheets again.
- Android 13+ geocoder callbacks have an eight-second timeout, single-completion
  protection, and cancellation handling. Older Android versions use the existing
  platform blocking geocoder on the IO dispatcher; its timeout is platform-bound.

## Verification boundaries

Automated tests exercise actual Android MapView overlays with network disabled,
route parsing, sanitized tile-download failure, Room address preservation under
concurrent edits/deletion, and delayed/duplicate/cancelled geocoder callbacks.
They do not prove live map availability or street-address accuracy on a phone.

Controlled phone checks (NOT RUN):

1. Upgrade without uninstalling; confirm existing history and totals remain.
2. Check the concise Settings screen and battery-settings button.
3. Open an existing GPS trip online; verify streets, route, markers, attribution,
   zoom/pan, and correct camera fit. Cached tile coverage is not an offline map.
4. Disable internet and open an uncached area; verify the unavailable message and
   retained route. Reconnect and reopen Trip Details to retry.
5. Open a trip with a missing start address; verify a street address appears when
   the Android provider can resolve it, persists after reopening, and leaves all
   mileage, notes, classifications, and endpoint coordinates unchanged.
6. Confirm provider failure remains an explicit unavailable state, not a guessed
   address. Address lookup cannot improve the accuracy of the recorded GPS fix.

Settings changes remove the controls; legacy synchronization service contracts
and stored legacy configuration are retained for compatibility.
