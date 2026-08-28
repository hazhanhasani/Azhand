CREATE TABLE IF NOT EXISTS payment_submissions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  unit_id INTEGER NOT NULL,
  member_id INTEGER NOT NULL,
  charge_id INTEGER NOT NULL,
  amount INTEGER NOT NULL,
  reference_id TEXT NOT NULL,
  note TEXT,
  status TEXT NOT NULL DEFAULT 'pending',
  reviewer_note TEXT,
  reviewed_at TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(unit_id) REFERENCES units(id),
  FOREIGN KEY(member_id) REFERENCES members(id),
  FOREIGN KEY(charge_id) REFERENCES charges(id)
);

CREATE INDEX IF NOT EXISTS idx_payment_submissions_unit
  ON payment_submissions(unit_id, created_at);

CREATE INDEX IF NOT EXISTS idx_payment_submissions_status
  ON payment_submissions(status, created_at);

CREATE TABLE IF NOT EXISTS service_request_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  request_id INTEGER NOT NULL,
  status TEXT NOT NULL,
  note TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(request_id) REFERENCES service_requests(id)
);

CREATE INDEX IF NOT EXISTS idx_service_request_events_request
  ON service_request_events(request_id, created_at);
