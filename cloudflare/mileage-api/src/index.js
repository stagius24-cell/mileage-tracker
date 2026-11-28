const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET, POST, OPTIONS",
  "access-control-allow-headers": "Authorization, Content-Type",
  "access-control-max-age": "86400",
};

const TRIP_COLUMNS = [
  "id", "date", "startTime", "endTime", "startAddress", "endAddress",
  "startLatitude", "startLongitude", "endLatitude", "endLongitude",
  "totalDistanceMiles", "businessMiles", "businessEntity", "purpose",
  "trackingMethod", "classification", "applicableRate", "calculatedDeduction",
  "notes", "waypointsJson", "syncStatus", "syncedAt", "classificationSource",
  "suggestedClassification", "vehicleId",
];

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: JSON_HEADERS });
}

function text(value, max = 4096) {
  if (value == null) return null;
  if (typeof value !== "string" || value.length > max) throw new Error("Invalid text field");
  return value;
}

function finite(value, nullable = false) {
  if (nullable && value == null) return null;
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) throw new Error("Invalid numeric field");
  return parsed;
}

async function authenticate(request) {
  const match = /^Bearer\s+(.+)$/i.exec(request.headers.get("Authorization") || "");
  if (!match || match[1].length > 8192) return null;

  // Google validates its own OAuth token and returns the immutable Google account ID.
  // The token is never logged or stored by this Worker.
  const response = await fetch(
    "https://oauth2.googleapis.com/tokeninfo?access_token=" + encodeURIComponent(match[1]),
    { headers: { "accept": "application/json" } }
  );
  if (!response.ok) return null;

  const tokenInfo = await response.json();
  const expiresIn = Number(tokenInfo.expires_in);
  const userId = typeof tokenInfo.user_id === "string" ? tokenInfo.user_id : "";
  if (!userId || !Number.isFinite(expiresIn) || expiresIn <= 0) return null;
  return { userId, email: typeof tokenInfo.email === "string" ? tokenInfo.email : null };
}

async function sync(request, env, identity) {
  const length = Number(request.headers.get("content-length") || 0);
  if (length > 2_000_000) return json({ error: "Payload too large" }, 413);

  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: "Invalid JSON" }, 400);
  }

  const trips = Array.isArray(body.trips) ? body.trips : [];
  const vehicles = Array.isArray(body.vehicles) ? body.vehicles : [];
  const savedLocations = Array.isArray(body.savedLocations) ? body.savedLocations : [];
  if (trips.length > 50 || vehicles.length > 100 || savedLocations.length > 100) {
    return json({ error: "Batch limit exceeded" }, 413);
  }

  const now = Date.now();
  const statements = [];

  for (const trip of trips) {
    const id = text(trip.id, 128);
    if (!id || !/^\d{4}-\d{2}-\d{2}$/.test(text(trip.date, 10) || "")) {
      return json({ error: "Invalid trip identity or date" }, 400);
    }
    statements.push(env.DB.prepare(`
      INSERT INTO mileage_trips (
        user_id, id, date, start_time, end_time, start_address, end_address,
        start_latitude, start_longitude, end_latitude, end_longitude,
        total_distance_miles, business_miles, business_entity, purpose,
        tracking_method, classification, applicable_rate, calculated_deduction,
        notes, waypoints_json, sync_status, synced_at, classification_source,
        suggested_classification, vehicle_id, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(user_id, id) DO UPDATE SET
        date=excluded.date, start_time=excluded.start_time, end_time=excluded.end_time,
        start_address=excluded.start_address, end_address=excluded.end_address,
        start_latitude=excluded.start_latitude, start_longitude=excluded.start_longitude,
        end_latitude=excluded.end_latitude, end_longitude=excluded.end_longitude,
        total_distance_miles=excluded.total_distance_miles, business_miles=excluded.business_miles,
        business_entity=excluded.business_entity, purpose=excluded.purpose,
        tracking_method=excluded.tracking_method, classification=excluded.classification,
        applicable_rate=excluded.applicable_rate, calculated_deduction=excluded.calculated_deduction,
        notes=excluded.notes, waypoints_json=excluded.waypoints_json,
        sync_status=excluded.sync_status, synced_at=excluded.synced_at,
        classification_source=excluded.classification_source,
        suggested_classification=excluded.suggested_classification,
        vehicle_id=excluded.vehicle_id, updated_at=excluded.updated_at
    `).bind(
      identity.userId, id, trip.date,
      finite(trip.startTime), finite(trip.endTime),
      text(trip.startAddress), text(trip.endAddress),
      finite(trip.startLatitude), finite(trip.startLongitude),
      finite(trip.endLatitude), finite(trip.endLongitude),
      finite(trip.totalDistanceMiles), finite(trip.businessMiles),
      text(trip.businessEntity, 256), text(trip.purpose, 1024),
      text(trip.trackingMethod, 64), text(trip.classification, 64),
      finite(trip.applicableRate), finite(trip.calculatedDeduction),
      text(trip.notes, 8192), text(trip.waypointsJson, 1_000_000) || "[]",
      text(trip.syncStatus, 32) || "PENDING", finite(trip.syncedAt, true),
      text(trip.classificationSource, 64) || "UNCLASSIFIED",
      text(trip.suggestedClassification, 64), text(trip.vehicleId, 128), now
    ));
  }

  for (const vehicle of vehicles) {
    const id = text(vehicle.id, 128);
    const name = text(vehicle.name, 256);
    if (!id || !name) return json({ error: "Invalid vehicle" }, 400);
    statements.push(env.DB.prepare(`
      INSERT INTO mileage_vehicles
        (user_id,id,name,bluetooth_address,bluetooth_device_name,created_at,updated_at)
      VALUES (?,?,?,?,?,?,?)
      ON CONFLICT(user_id,id) DO UPDATE SET name=excluded.name,
        bluetooth_address=excluded.bluetooth_address,
        bluetooth_device_name=excluded.bluetooth_device_name,
        updated_at=excluded.updated_at
    `).bind(identity.userId, id, name, text(vehicle.bluetoothAddress, 128),
      text(vehicle.bluetoothDeviceName, 256), finite(vehicle.createdAt), finite(vehicle.updatedAt)));
  }

  for (const location of savedLocations) {
    const id = text(location.id, 128);
    const name = text(location.name, 256);
    if (!id || !name) return json({ error: "Invalid saved location" }, 400);
    statements.push(env.DB.prepare(`
      INSERT INTO mileage_saved_locations
        (user_id,id,name,latitude,longitude,address,radius_meters,icon,
         classification_preference,business_entity,created_at,updated_at)
      VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
      ON CONFLICT(user_id,id) DO UPDATE SET name=excluded.name,
        latitude=excluded.latitude, longitude=excluded.longitude, address=excluded.address,
        radius_meters=excluded.radius_meters, icon=excluded.icon,
        classification_preference=excluded.classification_preference,
        business_entity=excluded.business_entity, updated_at=excluded.updated_at
    `).bind(identity.userId, id, name, finite(location.latitude), finite(location.longitude),
      text(location.address, 2048), finite(location.radiusMeters), text(location.icon, 64),
      text(location.classificationPreference, 64), text(location.businessEntity, 256),
      finite(location.createdAt), finite(location.updatedAt)));
  }

  statements.push(env.DB.prepare(`
    INSERT INTO mileage_sync_state(user_id,last_sync_at,schema_version) VALUES(?,?,1)
    ON CONFLICT(user_id) DO UPDATE SET last_sync_at=excluded.last_sync_at,schema_version=1
  `).bind(identity.userId, now));

  try {
    for (let offset = 0; offset < statements.length; offset += 100) {
      const results = await env.DB.batch(statements.slice(offset, offset + 100));
      if (results.some(result => !result.success)) throw new Error("D1 batch failed");
    }
    return json({
      syncedTripIds: trips.map(row => row.id),
      syncedVehicleIds: vehicles.map(row => row.id),
      syncedSavedLocationIds: savedLocations.map(row => row.id),
      syncedAt: now,
    });
  } catch {
    return json({ error: "Sync failed; retry is safe" }, 503);
  }
}

function tripFromDb(row) {
  return {
    id: row.id, date: row.date, startTime: row.start_time, endTime: row.end_time,
    startAddress: row.start_address, endAddress: row.end_address,
    startLatitude: row.start_latitude, startLongitude: row.start_longitude,
    endLatitude: row.end_latitude, endLongitude: row.end_longitude,
    totalDistanceMiles: row.total_distance_miles, businessMiles: row.business_miles,
    businessEntity: row.business_entity, purpose: row.purpose,
    trackingMethod: row.tracking_method, classification: row.classification,
    applicableRate: row.applicable_rate, calculatedDeduction: row.calculated_deduction,
    notes: row.notes, waypointsJson: row.waypoints_json, syncStatus: row.sync_status,
    syncedAt: row.synced_at, classificationSource: row.classification_source,
    suggestedClassification: row.suggested_classification, vehicleId: row.vehicle_id,
  };
}

async function backup(request, env, identity) {
  const url = new URL(request.url);
  const offset = Math.max(0, Math.min(1_000_000, Number(url.searchParams.get("cursor") || 0) || 0));
  const limit = 200;
  const [tripResult, countResult] = await Promise.all([
    env.DB.prepare(`SELECT * FROM mileage_trips WHERE user_id=? ORDER BY date DESC,start_time DESC LIMIT ? OFFSET ?`)
      .bind(identity.userId, limit, offset).all(),
    env.DB.prepare(`SELECT COUNT(*) AS count FROM mileage_trips WHERE user_id=?`)
      .bind(identity.userId).first(),
  ]);
  const tripRows = tripResult.results || [];
  const count = Number(countResult?.count || 0);
  const firstPage = offset === 0;
  const [vehicleRows, locationRows] = firstPage ? await Promise.all([
    env.DB.prepare("SELECT * FROM mileage_vehicles WHERE user_id=? ORDER BY name,id")
      .bind(identity.userId).all(),
    env.DB.prepare("SELECT * FROM mileage_saved_locations WHERE user_id=? ORDER BY name,id")
      .bind(identity.userId).all(),
  ]) : [{ results: [] }, { results: [] }];

  return json({
    schemaVersion: 1,
    trips: tripRows.map(tripFromDb),
    vehicles: (vehicleRows.results || []).map(row => ({
      id: row.id, name: row.name, bluetoothAddress: row.bluetooth_address,
      bluetoothDeviceName: row.bluetooth_device_name, createdAt: row.created_at, updatedAt: row.updated_at,
    })),
    savedLocations: (locationRows.results || []).map(row => ({
      id: row.id, name: row.name, latitude: row.latitude, longitude: row.longitude,
      address: row.address, radiusMeters: row.radius_meters, icon: row.icon,
      classificationPreference: row.classification_preference, businessEntity: row.business_entity,
      createdAt: row.created_at, updatedAt: row.updated_at,
    })),
    nextCursor: offset + tripRows.length < count ? offset + tripRows.length : null,
  });
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: JSON_HEADERS });
    const url = new URL(request.url);
    if (url.pathname === "/health" && request.method === "GET") return json({ ok: true, service: "business-mileage-api" });
    if (url.pathname !== "/v1/sync" && url.pathname !== "/v1/backup") return json({ error: "Not found" }, 404);

    let identity;
    try {
      identity = await authenticate(request);
    } catch {
      return json({ error: "Authentication service unavailable" }, 503);
    }
    if (!identity) return json({ error: "Valid Google sign-in required" }, 401);

    try {
      if (url.pathname === "/v1/sync" && request.method === "POST") return await sync(request, env, identity);
      if (url.pathname === "/v1/backup" && request.method === "GET") return await backup(request, env, identity);
      return json({ error: "Method not allowed" }, 405);
    } catch {
      return json({ error: "Request could not be completed" }, 400);
    }
  },
};
