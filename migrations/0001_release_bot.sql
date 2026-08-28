CREATE TABLE IF NOT EXISTS release_uploads (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  version TEXT NOT NULL,
  file_name TEXT NOT NULL,
  storage_key TEXT NOT NULL UNIQUE,
  storage_type TEXT NOT NULL DEFAULT 'kv',
  sha256 TEXT NOT NULL,
  file_size INTEGER NOT NULL,
  telegram_user_id TEXT NOT NULL,
  github_event_type TEXT,
  status TEXT NOT NULL DEFAULT 'stored',
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  dispatched_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_release_uploads_version
  ON release_uploads(version);

CREATE INDEX IF NOT EXISTS idx_release_uploads_created_at
  ON release_uploads(created_at DESC);
