CREATE TABLE IF NOT EXISTS building_finance_settings (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  initial_balance INTEGER NOT NULL DEFAULT 0,
  owner_monthly_charge INTEGER NOT NULL DEFAULT 0,
  tenant_monthly_charge INTEGER NOT NULL DEFAULT 0,
  auto_billing_enabled INTEGER NOT NULL DEFAULT 1,
  billing_day INTEGER NOT NULL DEFAULT 1,
  due_day INTEGER NOT NULL DEFAULT 10,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT OR IGNORE INTO building_finance_settings
  (
    id,
    initial_balance,
    owner_monthly_charge,
    tenant_monthly_charge,
    auto_billing_enabled,
    billing_day,
    due_day
  )
VALUES (1, 0, 0, 0, 1, 1, 10);

CREATE TABLE IF NOT EXISTS monthly_billing_runs (
  period_key TEXT PRIMARY KEY,
  jalali_year INTEGER NOT NULL,
  jalali_month INTEGER NOT NULL,
  owner_amount INTEGER NOT NULL,
  tenant_amount INTEGER NOT NULL,
  units_billed INTEGER NOT NULL DEFAULT 0,
  units_skipped INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'success',
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS charge_billing_meta (
  charge_id INTEGER PRIMARY KEY,
  payer_member_id INTEGER,
  payer_relation TEXT,
  source TEXT NOT NULL DEFAULT 'manual',
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(charge_id) REFERENCES charges(id),
  FOREIGN KEY(payer_member_id) REFERENCES members(id)
);

CREATE INDEX IF NOT EXISTS idx_charge_billing_payer
  ON charge_billing_meta(payer_member_id, payer_relation);

CREATE INDEX IF NOT EXISTS idx_charge_billing_source
  ON charge_billing_meta(source);
