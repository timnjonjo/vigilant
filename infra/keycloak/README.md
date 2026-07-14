# Keycloak (dashboard auth)

Single realm **`vigilant`**; tenants are distinguished by a `tenant_id` claim
(spec §10 resolved: single realm + claim, not realm-per-tenant). The realm,
client, roles, groups, and seed users are version-controlled in
[`vigilant-realm.json`](vigilant-realm.json) and **auto-imported** — no console
clicking.

## Stand up

```bash
cp .env.example .env                  # replace every placeholder first
docker compose --profile auth up      # neo4j + postgres + geoip + keycloak
```

Keycloak runs in production mode against the shared Postgres (its own `keycloak`
database, created by `infra/postgres/init` on a fresh volume). Endpoints:

- Issuer: `http://localhost:8081/realms/vigilant`
- Admin console: `http://localhost:8081` (password comes from the ignored `.env`)

> The `keycloak` database is created automatically by the idempotent
> `keycloak-db-init` sidecar (Keycloak waits on it), so it works on both a fresh
> and a pre-existing `postgres-data` volume. (The `infra/postgres/init` SQL only
> runs on a *fresh* volume — the sidecar covers the existing-volume case.)

## Clients

| Client | Type | Grant | Used by |
|---|---|---|---|
| `vigilant-dashboard` | public, PKCE S256 | authorization-code | React analyst console (exact redirect `http://localhost:5173/`) |
| `vigilant-swagger` | public, PKCE S256 | authorization-code | springdoc Swagger UI (redirect `http://localhost:8080/swagger-ui/oauth2-redirect.html`) |
| `loob-bank-host` | **confidential** (secret) | **client-credentials** | Loob Bank host integration (server-to-server: codes/events/decisions) |

`loob-bank-host` is a service-account client with the `host_integration` realm
role and a hardcoded `tenant_id=loob-bank` claim. Its service account is the user
`service-account-loob-bank-host`. Get a machine token:

```bash
curl -s http://localhost:8081/realms/vigilant/protocol/openid-connect/token \
  -d grant_type=client_credentials \
  -d client_id=loob-bank-host -d client_secret="$LOOB_HOST_CLIENT_SECRET" | jq -r .access_token
```

Onboarding a new tenant's host system = one more confidential client like this
(its own secret + hardcoded `tenant_id`). Secrets and seed passwords are
injected from the ignored root `.env`; the realm export contains placeholders.

Both carry the `tenant_id` protocol mapper (tokens must include the claim or the
backend's `TenantAccessGuard` rejects every dashboard call). Kept as **separate
clients** so Swagger access can be revoked independently of the dashboard.

> **Adding a client to an already-imported realm:** `start --import-realm` uses
> `IGNORE_EXISTING`, so editing this file does **not** update a realm that was
> already imported. Either recreate the realm (fresh `docker compose --profile
> auth up` on clean state) or add the client to the running server via the Admin
> API / console. The export is the source of truth for a *fresh* stand-up.

## Roles & seed users

| User | Role | tenant_id |
|---|---|---|
| `analyst-loob` | `fraud_analyst` | loob-bank |
| `ops-loob` | `ops_viewer` | loob-bank |
| `admin-loob` | `tenant_admin` | loob-bank |
| `analyst-acme` | `fraud_analyst` | acme-sacco |

The `tenant_id` claim is emitted from a user attribute (groups organise users per
tenant). Realm roles appear under `realm_access.roles`.

The dashboard client intentionally disables the resource-owner password grant.
Use the browser authorization-code + PKCE login. For command-line regression
checks, paste a short-lived access token into the test shell; do not add a
password grant or store the token in the repository.

Use it against a dashboard endpoint (tenant must match the token's claim):

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/v1/cases?tenantId=loob-bank&status=OPEN"
```

## Onboarding a new tenant (no realm redeploy)

Add a group with its `tenant_id` attribute and create users with that attribute
+ role — via the admin console or Admin API — no realm re-import needed. To make
it reproducible, also add it here and re-export.

## Re-export after admin-console edits

```bash
docker compose exec keycloak /opt/keycloak/bin/kc.sh export \
  --dir /opt/keycloak/data/import --realm vigilant --users realm_file
```

Then copy the file out of the container and commit it.
