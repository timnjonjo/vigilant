-- Synthetic load-test data for the 200+ TPS audit (loob-bank tenant).
-- 4 campaigns matching the graph seed + 80k fraud_case rows.
-- Load: docker exec -i vigilant-postgres psql -U vigilant -d vigilant < postgres-seed.sql

INSERT INTO campaign (campaign_id, tenant_id, name, bonus_amount, status, conversion_criteria, created_at, updated_at)
VALUES
 ('camp-a','loob-bank','Loadtest A',300,'ACTIVE','FIRST_DEPOSIT',now(),now()),
 ('camp-b','loob-bank','Loadtest B',250,'ACTIVE','FIRST_DEPOSIT',now(),now()),
 ('camp-c','loob-bank','Loadtest C',200,'PAUSED','N_DAY_RETENTION',now(),now()),
 ('camp-d','loob-bank','Loadtest D',150,'ENDED','FIRST_DEPOSIT',now(),now())
ON CONFLICT (campaign_id) DO NOTHING;

INSERT INTO fraud_case (tenant_id, referral_code, referee_user_id, decision, score, reason_codes, status, campaign_id, opened_at, resolved_at, resolution)
SELECT 'loob-bank', 'LOOB-'||(g%20000), 'ree-'||(g%20000)||'-0',
       CASE WHEN g%2=0 THEN 'HOLD' ELSE 'REJECT' END,
       0.4 + (g%60)/100.0, 'VELOCITY_BURST',
       CASE WHEN g%4=0 THEN 'RESOLVED' ELSE 'OPEN' END,
       (ARRAY['camp-a','camp-b','camp-c','camp-d'])[1+g%4],
       now() - ((g%30)||' days')::interval,
       CASE WHEN g%4=0 THEN now() END,
       CASE WHEN g%4=0 THEN 'APPROVE' END
FROM generate_series(1,80000) g;
