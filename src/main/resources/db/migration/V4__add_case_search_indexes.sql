-- Case-queue free-text search (referral code / referee user id). The analyst search
-- box does case-insensitive substring matching (lower(col) LIKE '%term%'), which a
-- btree index can't serve, so it fell back to a full tenant scan. Trigram GIN
-- indexes make the substring match index-backed instead. pg_trgm ships with the
-- standard postgres:16 image.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_fraud_case_referral_code_trgm
    ON fraud_case USING GIN (lower(referral_code) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_fraud_case_referee_user_id_trgm
    ON fraud_case USING GIN (lower(referee_user_id) gin_trgm_ops);
