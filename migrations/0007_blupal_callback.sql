CREATE TABLE IF NOT EXISTS payment_callback_sessions (
  token_hash TEXT PRIMARY KEY,
  provider_invoice_id INTEGER NOT NULL,
  member_id INTEGER NOT NULL,
  expires_at TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payment_callback_invoice
  ON payment_callback_sessions(provider_invoice_id);

CREATE INDEX IF NOT EXISTS idx_payment_callback_expiry
  ON payment_callback_sessions(expires_at);
