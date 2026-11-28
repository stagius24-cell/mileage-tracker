# Cloudflare D1 mileage API

This Worker uses the dedicated `business-mileage-tracker` D1 database. It does not use the Expense Manager database.

## Data model and privacy

- Room remains the Android offline source of truth.
- The Worker scopes every row by the immutable Google account ID returned by Google's OAuth token introspection endpoint.
- It stores full trip fields (including route waypoints and location coordinates), saved locations, and vehicles.
- It never accepts a user ID from the client and never logs OAuth bearer tokens.
- Google sign-in is used for identity only; Google Sheets remains a separate legacy sync until cutover is verified.

## Deploy

From this directory, with Wrangler authenticated to the intended Cloudflare account:

```sh
npx wrangler d1 migrations apply business-mileage-tracker --remote
npx wrangler deploy
npx wrangler subdomain get
```

The initial SQL migration has already been applied to the dedicated database created for this project. Keep the migration in source control so new environments can be initialized reproducibly.

## API

- `GET /health` is public and returns service status only.
- `POST /v1/sync` accepts up to 50 full trips, 100 vehicles, and 100 saved locations. Retries are idempotent by (Google account ID, local record ID).
- `GET /v1/backup?cursor=0` returns trips in pages of 200; the first page also contains vehicles and saved locations. Follow `nextCursor` until null.
- All data APIs require a valid Google OAuth bearer token. Data is partitioned by the Google account ID verified by Google.

Do not put a Cloudflare Access service token in the Android APK. The user OAuth token is held by Android Keystore on the device and is sent only over HTTPS.
