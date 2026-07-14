-- Keyset pagination indexes. Every ordering ends in the unique primary key so
-- concurrent rows with identical timestamps/scores have a stable position.
CREATE INDEX idx_fraud_case_tenant_status_score_cursor
    ON fraud_case (tenant_id, status, score DESC, opened_at DESC, id DESC);

CREATE INDEX idx_fraud_case_tenant_campaign_status_score_cursor
    ON fraud_case (tenant_id, campaign_id, status, score DESC, opened_at DESC, id DESC);

CREATE INDEX idx_fraud_case_tenant_status_age_cursor
    ON fraud_case (tenant_id, status, opened_at ASC, id ASC);

CREATE INDEX idx_fraud_case_tenant_campaign_status_age_cursor
    ON fraud_case (tenant_id, campaign_id, status, opened_at ASC, id ASC);

-- Supports calls that deliberately omit status while preserving keyset order.
CREATE INDEX idx_fraud_case_tenant_score_cursor
    ON fraud_case (tenant_id, score DESC, opened_at DESC, id DESC);

CREATE INDEX idx_fraud_case_tenant_age_cursor
    ON fraud_case (tenant_id, opened_at ASC, id ASC);

CREATE INDEX idx_fraud_case_reason_codes
    ON fraud_case USING GIN (string_to_array(reason_codes, ','));

CREATE INDEX idx_fraud_case_audit_opened_cursor
    ON fraud_case (tenant_id, referral_code, opened_at ASC, id ASC);

CREATE INDEX idx_fraud_case_audit_resolved_cursor
    ON fraud_case (tenant_id, referral_code, resolved_at ASC, id ASC)
    WHERE resolved_at IS NOT NULL;

CREATE INDEX idx_campaign_tenant_created_cursor
    ON campaign (tenant_id, created_at DESC, campaign_id DESC);
