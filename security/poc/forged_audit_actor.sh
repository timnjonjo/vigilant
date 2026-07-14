#!/usr/bin/env bash
# Regression for SECURITY-005. Resolves one local OPEN case; use disposable dev data.
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_env ANALYST_TOKEN

case_id="${CASE_ID:-$(curl -fsS -H "Authorization: Bearer $ANALYST_TOKEN" \
  "$BASE_URL/v1/cases?tenantId=loob-bank&status=OPEN" | jq -er '.[0].id')}"
response="$(mktemp)"
trap 'rm -f "$response"' EXIT

status="$(curl -sS -o "$response" -w '%{http_code}' \
  -H "Authorization: Bearer $ANALYST_TOKEN" -H 'Content-Type: application/json' \
  "$BASE_URL/v1/cases/$case_id/resolve" \
  -d '{"tenantId":"loob-bank","resolution":"APPROVE","resolvedBy":"forged-super-admin"}')"
actor="$(jq -r '.resolvedBy // empty' "$response")"
echo "case=$case_id http=$status persisted_actor=$actor"
[[ "$status" == "200" ]] || { echo "Resolution failed; ensure the local callback sink is available" >&2; exit 2; }
[[ "$actor" != "forged-super-admin" ]] || { echo "VULNERABLE: client forged the audit actor" >&2; exit 1; }
echo "BLOCKED: actor came from the validated token"
