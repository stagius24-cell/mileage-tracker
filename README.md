# Business Mileage Tracker 1.0.13

This update adds a reliable automatic-drive exit fallback:

- Stops a confirmed automatic drive after ten continuous minutes of GPS stationarity.
- Resets the timer when meaningful speed or displacement is detected.
- Ignores inaccurate GPS fixes.
- Leaves manual trips unchanged.
- Preserves the existing activity-transition, Bluetooth, 200 m confirmation, and four-minute debounce behavior.

Install over the existing app. Do not uninstall the app or clear its data before updating.
