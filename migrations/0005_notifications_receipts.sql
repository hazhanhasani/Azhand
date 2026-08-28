CREATE TABLE IF NOT EXISTS app_notifications (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  member_id INTEGER NOT NULL,
  unit_id INTEGER,
  type TEXT NOT NULL DEFAULT 'info',
  title TEXT NOT NULL,
  body TEXT NOT NULL,
  entity_type TEXT,
  entity_id TEXT,
  read_at TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(member_id) REFERENCES members(id),
  FOREIGN KEY(unit_id) REFERENCES units(id)
);

CREATE INDEX IF NOT EXISTS idx_app_notifications_member
  ON app_notifications(member_id, read_at, created_at);

CREATE TABLE IF NOT EXISTS payment_receipts (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  submission_id INTEGER NOT NULL UNIQUE,
  payment_id INTEGER,
  receipt_no TEXT NOT NULL UNIQUE,
  amount INTEGER NOT NULL,
  reference_id TEXT,
  issued_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(submission_id) REFERENCES payment_submissions(id),
  FOREIGN KEY(payment_id) REFERENCES payments(id)
);

CREATE INDEX IF NOT EXISTS idx_payment_receipts_submission
  ON payment_receipts(submission_id);
