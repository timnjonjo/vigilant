#!/usr/bin/env bash
# Regression for SECURITY-001: an unknown/unconverted payout subject must not APPROVE.
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_env HOST_TOKEN

CAMPAIGN_ID="${CAMPAIGN_ID:-camp-a}"
stamp="$(date +%s)"
body="$(printf '{"tenantId":"loob-bank","campaignId":"%s","referralCode":"SECURITY-UNKNOWN-%s","refereeUserId":"security-unknown-%s"}' "$CAMPAIGN_ID" "$stamp" "$stamp")"
response="$(mktemp)"
trap 'rm -f "$response"' EXIT

status="$(curl -sS -o "$response" -w '%{http_code}' \
  -H "Authorization: Bearer $HOST_TOKEN" -H 'Content-Type: application/json' \
  "$BASE_URL/v1/decisions/payout-check" -d "$body")"

echo "HTTP $status"
if [[ "$status" == "200" && "$(jq -r '.action // empty' "$response")" == "APPROVE" ]]; then
  echo "VULNERABLE: invalid payout subject was approved" >&2
  exit 1
fi
[[ "$status" == "409" ]] || { echo "Expected fail-closed HTTP 409" >&2; exit 1; }
echo "BLOCKED: invalid payout subject was not scored"
