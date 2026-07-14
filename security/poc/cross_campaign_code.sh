#!/usr/bin/env bash
# Regression for SECURITY-002: a code issued for campaign A cannot redeem in B.
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_env HOST_TOKEN
require_env CAMPAIGN_A
require_env CAMPAIGN_B

stamp="$(date +%s)"
user="security-campaign-ref-$stamp"
referee="security-campaign-referee-$stamp"
issued="$(curl -fsS -H "Authorization: Bearer $HOST_TOKEN" -H 'Content-Type: application/json' \
  "$BASE_URL/v1/codes/generate" \
  -d "{\"tenantId\":\"loob-bank\",\"campaignId\":\"$CAMPAIGN_A\",\"userId\":\"$user\",\"deviceId\":\"security-device-$stamp\",\"ipAddress\":\"192.0.2.10\"}")"
code="$(jq -er '.referralCode' <<<"$issued")"

status="$(curl -sS -o /dev/null -w '%{http_code}' \
  -H "Authorization: Bearer $HOST_TOKEN" -H 'Content-Type: application/json' \
  "$BASE_URL/v1/events/redemption" \
  -d "{\"tenantId\":\"loob-bank\",\"campaignId\":\"$CAMPAIGN_B\",\"referralCode\":\"$code\",\"newUserId\":\"$referee\",\"deviceId\":\"security-referee-device-$stamp\",\"ipAddress\":\"192.0.2.11\"}")"

echo "issued=$code cross_campaign_http=$status"
[[ "$status" == "404" ]] || { echo "VULNERABLE: cross-campaign redemption was accepted" >&2; exit 1; }
echo "BLOCKED: code remains bound to campaign A"
