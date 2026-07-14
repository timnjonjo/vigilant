#!/usr/bin/env bash
# Removes the synthetic 200+ TPS load-test data from the shared dev stack, leaving
# the demo seed (DevDataSeeder) untouched. Synthetic data is identifiable by its
# prefixes: Account userId ref-/ree-/lt-/smoke-, ReferralCode LOOB-<n>, campaigns
# camp-a..camp-d. Safe to re-run.
set -euo pipefail
: "${VIGILANT_NEO4J_PASSWORD:?set VIGILANT_NEO4J_PASSWORD from the ignored .env}"

echo "== Neo4j: deleting synthetic accounts, code nodes, edges =="
docker exec -i vigilant-neo4j cypher-shell -u neo4j -p "$VIGILANT_NEO4J_PASSWORD" <<'CYPHER'
MATCH (a:Account) WHERE a.userId =~ '^(ref|ree|lt|smoke).*'
CALL { WITH a DETACH DELETE a } IN TRANSACTIONS OF 5000 ROWS;
MATCH (c:ReferralCode) WHERE c.code =~ '^LOOB-[0-9]+$'
CALL { WITH c DETACH DELETE c } IN TRANSACTIONS OF 5000 ROWS;
CYPHER

echo "== Postgres: deleting synthetic cases + campaigns =="
docker exec -i vigilant-postgres psql -U vigilant -d vigilant <<'SQL'
DELETE FROM fraud_case WHERE campaign_id IN ('camp-a','camp-b','camp-c','camp-d');
DELETE FROM campaign   WHERE campaign_id IN ('camp-a','camp-b','camp-c','camp-d');
SQL

echo "== Done. Tip: for a pristine demo, run the app with vigilant.dev-seed.reset=true =="
