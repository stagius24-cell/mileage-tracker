# Business Mileage Tracker 1.0.29

### Search Box & Collapsible Sort/Filter Redesign:
- **Search Box Text Clipping Fix:** Replaced rigid OutlinedTextField with a centered custom Search surface using BasicTextField, fixing vertical clipping and font padding so placeholder and query text render cleanly without cut-off.
- **Collapsible Sort Header Button:** Placed a clean 44dp sort button with `Icons.Default.Sort` next to the search bar to toggle the filter and sorting drawer.
- **2-Row Collapsible Menu:**
  - **Row 1 (Classification):** Swipeable horizontal row with boxes for **All**, **Business**, **Personal**, and **Unreviewed**.
  - **Row 2 (Sort Order):** Swipeable horizontal row with boxes for **Newest**, **Oldest**, **Highest**, and **Lowest**.
- **Interactive Box Styling:** Each option is enclosed in a rounded box with an icon next to the text, highlighting in vibrant green when selected and clean neutral grey when unselected.
- **Signed with Persistent Key:** Signed with the persistent Run #96 / Run #99 debug certificate (`43efd34a...`) to allow seamless in-place updates over existing installations without Room database / trip record loss.

Install this APK over the existing app. Do not uninstall the app or clear its data if you want to preserve local trip history.

- **Source branch:** `main`
- **Source commit:** `b79670c6b323538e600118f391796a6ec905dead`
- **CI run:** https://github.com/stagius24-cell/mileage-tracker/actions/runs/42445431973
- **Package:** `com.businesstracker.mileage.debug`
- **Version:** `1.0.29` (Code `30`)
- **APK SHA-256:** `e49ac58e193e8da79f12f8f6db8e853bc87aeca19a9b0c0a177e6f0ba48295a6`
- **Signer Certificate SHA-256:** `43efd34a84878e7aa8290b276f65d3171b79965d05de9223f86d8c4dd6901948`
- **Signer Certificate SHA-1:** `32d6afa108beed313e181a08176ded0fd960af43`

Direct download:
[BusinessMileageTracker-1.0.29-history-sort.apk](https://github.com/stagius24-cell/mileage-tracker/raw/refs/heads/downloads/history-sort-v1.0.29/BusinessMileageTracker-1.0.29-history-sort.apk)
