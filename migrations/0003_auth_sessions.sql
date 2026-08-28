CREATE TABLE IF NOT EXISTS member_access_codes (
  member_id INTEGER PRIMARY KEY,
  salt TEXT NOT NULL,
  code_hash TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_used_at TEXT,
  FOREIGN KEY(member_id) REFERENCES members(id)
);

CREATE TABLE IF NOT EXISTS app_sessions (
  token_hash TEXT PRIMARY KEY,
  member_id INTEGER NOT NULL,
  expires_at TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen_at TEXT,
  user_agent TEXT,
  FOREIGN KEY(member_id) REFERENCES members(id)
);

CREATE INDEX IF NOT EXISTS idx_app_sessions_member
  ON app_sessions(member_id);

CREATE INDEX IF NOT EXISTS idx_app_sessions_expiry
  ON app_sessions(expires_at);

CREATE UNIQUE INDEX IF NOT EXISTS idx_unit_members_unique
  ON unit_members(unit_id, member_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_charges_period_unit
  ON charges(period_id, unit_id);
