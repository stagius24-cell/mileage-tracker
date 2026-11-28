-- Dedicated, per-Google-account mileage backup database.
-- Local Room remains the app's offline source of truth.
CREATE TABLE IF NOT EXISTS mileage_trips (
  user_id TEXT NOT NULL,
  id TEXT NOT NULL,
  date TEXT NOT NULL,
  start_time INTEGER NOT NULL,
  end_time INTEGER NOT NULL,
  start_address TEXT,
  end_address TEXT,
  start_latitude REAL NOT NULL,
  start_longitude REAL NOT NULL,
  end_latitude REAL NOT NULL,
  end_longitude REAL NOT NULL,
  total_distance_miles REAL NOT NULL,
  business_miles REAL NOT NULL,
  business_entity TEXT NOT NULL,
  purpose TEXT NOT NULL,
  tracking_method TEXT NOT NULL,
  classification TEXT NOT NULL,
  applicable_rate REAL NOT NULL,
  calculated_deduction REAL NOT NULL,
  notes TEXT,
  waypoints_json TEXT NOT NULL,
  sync_status TEXT NOT NULL,
  synced_at INTEGER,
  classification_source TEXT NOT NULL DEFAULT 'UNCLASSIFIED',
  suggested_classification TEXT,
  vehicle_id TEXT,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (user_id, id)
);
CREATE INDEX IF NOT EXISTS idx_mileage_trips_user_date
  ON mileage_trips(user_id, date, start_time);
CREATE INDEX IF NOT EXISTS idx_mileage_trips_user_updated
  ON mileage_trips(user_id, updated_at);

CREATE TABLE IF NOT EXISTS mileage_vehicles (
  user_id TEXT NOT NULL,
  id TEXT NOT NULL,
  name TEXT NOT NULL,
  bluetooth_address TEXT,
  bluetooth_device_name TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (user_id, id)
);

CREATE TABLE IF NOT EXISTS mileage_saved_locations (
  user_id TEXT NOT NULL,
  id TEXT NOT NULL,
  name TEXT NOT NULL,
  latitude REAL NOT NULL,
  longitude REAL NOT NULL,
  address TEXT NOT NULL,
  radius_meters INTEGER NOT NULL,
  icon TEXT NOT NULL,
  classification_preference TEXT,
  business_entity TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (user_id, id)
);

CREATE TABLE IF NOT EXISTS mileage_sync_state (
  user_id TEXT PRIMARY KEY NOT NULL,
  last_sync_at INTEGER NOT NULL,
  schema_version INTEGER NOT NULL DEFAULT 1
);
