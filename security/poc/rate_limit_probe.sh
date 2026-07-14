#!/usr/bin/env bash
# Characterises the unresolved rate-limit control gap; sends read-like invalid payout probes only.
set -euo pipefail
source "$(dirname "$0")/common.sh"
require_env HOST_TOKEN
CAMPAIGN_ID="${CAMPAIGN_ID:-camp-a}"
REQUESTS="${REQUESTS:-30}"

limited=0
for n in $(seq 1 "$REQUESTS"); do
  status="$(curl -sS -o /dev/null -w '%{http_code}' \
    -H "Authorization: Bearer $HOST_TOKEN" -H 'Content-Type: application/json' \
    "$BASE_URL/v1/decisions/payout-check" \
    -d "{\"tenantId\":\"loob-bank\",\"campaignId\":\"$CAMPAIGN_ID\",\"referralCode\":\"RATE-PROBE-$n\",\"refereeUserId\":\"rate-probe-$n\"}")"
  [[ "$status" == "429" ]] && limited=$((limited + 1))
done
echo "requests=$REQUESTS rate_limited=$limited"
[[ "$limited" -gt 0 ]] || exit 1
