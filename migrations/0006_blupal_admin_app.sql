CREATE TABLE IF NOT EXISTS admin_sessions (
  token_hash TEXT PRIMARY KEY,
  expires_at TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen_at TEXT,
  user_agent TEXT
);

CREATE INDEX IF NOT EXISTS idx_admin_sessions_expiry
  ON admin_sessions(expires_at);

CREATE TABLE IF NOT EXISTS payment_gateway_invoices (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  provider TEXT NOT NULL DEFAULT 'blupal',
  provider_invoice_id INTEGER NOT NULL UNIQUE,
  member_id INTEGER NOT NULL,
  unit_id INTEGER NOT NULL,
  charge_id INTEGER NOT NULL,
  amount_toman INTEGER NOT NULL,
  amount_rial INTEGER NOT NULL,
  final_amount_rial INTEGER NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING',
  transaction_id TEXT,
  payment_link TEXT,
  card_number TEXT,
  mode TEXT,
  expires_at TEXT,
  payer_name TEXT,
  payer_card TEXT,
  payer_bank_name TEXT,
  receipt_no TEXT UNIQUE,
  credited_at TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(member_id) REFERENCES members(id),
  FOREIGN KEY(unit_id) REFERENCES units(id),
  FOREIGN KEY(charge_id) REFERENCES charges(id)
);

CREATE INDEX IF NOT EXISTS idx_gateway_invoices_member
  ON payment_gateway_invoices(member_id, created_at);
CREATE INDEX IF NOT EXISTS idx_gateway_invoices_status
  ON payment_gateway_invoices(status, created_at);

CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_gateway_reference
  ON payments(gateway, reference_id)
  WHERE gateway IS NOT NULL AND reference_id IS NOT NULL;

CREATE TRIGGER IF NOT EXISTS trg_blupal_credit_after_payment
AFTER INSERT ON payments
WHEN NEW.gateway = 'blupal'
  AND NEW.status = 'paid'
  AND NEW.charge_id IS NOT NULL
BEGIN
  UPDATE charges
  SET
    paid_amount = MIN(amount, paid_amount + NEW.amount),
    status = CASE
      WHEN MIN(amount, paid_amount + NEW.amount) >= amount THEN 'paid'
      ELSE 'unpaid'
    END
  WHERE id = NEW.charge_id;
END;
