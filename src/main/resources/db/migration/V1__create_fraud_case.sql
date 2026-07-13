-- Case queue / audit trail for HOLD and REJECT payout decisions (spec section 6).
CREATE TABLE fraud_case (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(255) NOT NULL,
    referral_code   VARCHAR(255) NOT NULL,
    referee_user_id VARCHAR(255) NOT NULL,
    decision        VARCHAR(32)  NOT NULL,
    score           DOUBLE PRECISION NOT NULL,
    reason_codes    TEXT         NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    resolution      VARCHAR(32),
    resolved_by     VARCHAR(255),
    opened_at       TIMESTAMPTZ  NOT NULL,
    resolved_at     TIMESTAMPTZ
);

CREATE INDEX idx_fraud_case_tenant_status ON fraud_case (tenant_id, status);
