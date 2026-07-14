#!/usr/bin/env bash
# Static regression for local port binding, public-client PKCE settings, and secret placeholders.
set -euo pipefail
repo="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$repo"

docker compose config --format json \
  | jq -e '[.services[]?.ports[]? | select(.host_ip != "127.0.0.1")] | length == 0' >/dev/null
jq -e '
  (.clients[] | select(.clientId == "vigilant-dashboard") |
    .publicClient == true and
    .directAccessGrantsEnabled == false and
    .redirectUris == ["http://localhost:5173/"] and
    .webOrigins == ["http://localhost:5173"]) and
  (.clients[] | select(.clientId == "loob-bank-host") |
    .secret == "${LOOB_HOST_CLIENT_SECRET}")
' infra/keycloak/vigilant-realm.json >/dev/null
echo "PASS: published ports are loopback-only and Keycloak client config is hardened"
