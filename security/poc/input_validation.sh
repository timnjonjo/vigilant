#!/usr/bin/env bash
# Regressions for SECURITY-003/004: required fields validate and bad IP text is discarded.
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_env HOST_TOKEN
require_env CAMPAIGN_ID
require_env VIGILANT_NEO4J_PASSWORD

blank_status="$(curl -sS -o /dev/null -w '%{http_code}' \
  -H "Authorization: Bearer $HOST_TOKEN" -H 'Content-Type: application/json' \
  "$BASE_URL/v1/codes/generate" \
  -d "{\"tenantId\":\"loob-bank\",\"campaignId\":\"$CAMPAIGN_ID\",\"userId\":\" \"}")"
[[ "$blank_status" == "400" ]] || { echo "Blank userId was not rejected: HTTP $blank_status" >&2; exit 1; }

stamp="$(date +%s)"
user="security-malformed-ip-$stamp"
curl -fsS -o /dev/null -H "Authorization: Bearer $HOST_TOKEN" -H 'Content-Type: application/json' \
  "$BASE_URL/v1/codes/generate" \
  -d "{\"tenantId\":\"loob-bank\",\"campaignId\":\"$CAMPAIGN_ID\",\"userId\":\"$user\",\"deviceId\":\"security-device-$stamp\",\"ipAddress\":\"not-an-ip\"}"

stored="$(docker exec vigilant-neo4j cypher-shell -u neo4j -p "$VIGILANT_NEO4J_PASSWORD" --format plain \
  "MATCH (a:Account {tenantId:'loob-bank', userId:'$user'}) RETURN coalesce(a.ipAddress,'<null>') AS ip, coalesce(a.ipSubnet,'<null>') AS subnet" \
  | tail -n 1 | tr -d '"')"
echo "blank_http=$blank_status stored_ip_and_subnet=$stored"
[[ "$stored" == *"<null>"* ]] || { echo "VULNERABLE: malformed IP reached the graph" >&2; exit 1; }
