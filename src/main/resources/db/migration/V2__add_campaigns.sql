-- Campaign model (spec §10a). Campaigns are relational config owned by Vigilant.
CREATE TABLE campaign (
    campaign_id            VARCHAR(64)  PRIMARY KEY,   -- server-generated UUID
    tenant_id              VARCHAR(255) NOT NULL,
    name                   VARCHAR(255) NOT NULL,
    bonus_amount           NUMERIC(12,2) NOT NULL,     -- KES, plain numeric (no multi-currency)
    start_date             DATE,
    end_date               DATE,
    status                 VARCHAR(16)  NOT NULL,      -- DRAFT | ACTIVE | PAUSED | ENDED
    conversion_criteria    VARCHAR(32)  NOT NULL,      -- FIRST_DEPOSIT | N_DAY_RETENTION
    referral_cap_per_user  INTEGER,                    -- optional campaign-level guardrail
    created_at             TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_campaign_tenant_status ON campaign (tenant_id, status);

-- Every case now belongs to a campaign. Soft reference (a bare string, like
-- tenant_id) — validity is enforced in the service layer, not by a DB FK. The
-- DEFAULT-then-drop keeps this migration safe on an already-populated dev table
-- and on a fresh one alike; dev data is reseeded to attach real campaign ids.
ALTER TABLE fraud_case ADD COLUMN campaign_id VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE fraud_case ALTER COLUMN campaign_id DROP DEFAULT;
CREATE INDEX idx_fraud_case_tenant_campaign ON fraud_case (tenant_id, campaign_id);
