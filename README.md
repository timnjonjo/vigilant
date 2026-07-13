# Vigilant

Pluggable, graph-based referral-fraud detection engine. Gates the **payout**,
never the signup. See `Vigilant Engine Spec.md` for the full design.

This repo currently implements the first vertical slice end-to-end:
**code issuance → redemption → conversion → payout decision → case queue**,
with rules-based scoring (no ML yet).

## Layout

Monorepo. The repo root is the orchestration layer (`docker-compose.yml`, this
README, IDE run config); each app lives in its own folder.

- **[`backend/`](backend/)** — Spring Boot fraud engine (Maven).
- **[`frontend/`](frontend/)** — React + TypeScript analyst console (case queue,
  resolution, graph explorer). Talks to the REST API below.

## Stack

- Java 17+ (built/tested on 21), Spring Boot 4.1, Spring Modulith
- **Neo4j** (graph, system of record) via the Neo4j Java driver — hand-written,
  tenant-scoped Cypher for full traversal control
- **Postgres** (case queue / audit trail) via JPA + Flyway
- **JGraphT** for in-process cycle detection over subgraphs pulled from Neo4j
- **MaxMind `maxmind-db`** reader over a local **DB-IP IP-to-ASN Lite** `.mmdb`
  for Tier-1 IP reputation (ASN → datacenter/VPN), zero network calls at runtime

## Module structure (Spring Modulith application modules)

`shared` · `tenant` · `graph` · `scoring` · `codes` · `events` · `decisions` ·
`casequeue` · `webhook` · `ip-reputation` · `web` · `support`

The `ip-reputation` module has its own
[README](src/main/java/com/turing/vigilant/ipreputation/README.md) (data
source, provisioning, config).

Boundaries are enforced by `ModularityTest` (`ApplicationModules.verify()`).

## REST contracts

| Method & path | Purpose |
|---|---|
| `POST /v1/codes/generate` | Issue a referral code (never denied; risky identity tagged) |
| `POST /v1/events/redemption` | Record a redemption (always `ACCEPT`) |
| `POST /v1/events/conversion` | Record a qualifying action |
| `POST /v1/decisions/payout-check` | Score and return `APPROVE` / `HOLD` / `REJECT` + reason codes |
| `GET  /v1/cases` | List cases for the dashboard (tenant-scoped) |
| `GET  /v1/cases/{id}` | Fetch one case |
| `POST /v1/cases/{id}/resolve` | Analyst resolves a case → fires host webhook |

All calls are tenant-scoped and authenticated with a **Keycloak token** carrying a
`tenant_id` claim that must match the request's `tenantId` (see
[Auth](#auth-keycloak)). Tenants and their `callbackUrl` are configured under
`vigilant.tenants.*`.

**Interactive docs:** Swagger UI at `/swagger-ui.html`, raw OpenAPI spec at
`/v3/api-docs`. Use the *Authorize* button to log in via Keycloak. Disable in
production with `springdoc.api-docs.enabled=false`.

### Auth (Keycloak)

Everything is on one Keycloak model — no API keys:

| Plane | Endpoints | Grant | Role |
|---|---|---|---|
| Host integration (server-to-server) | `/v1/codes`, `/v1/events`, `/v1/decisions` | client-credentials (confidential service-account client) | `host_integration` |
| Dashboard (analyst) | `/v1/cases`, `/v1/monitoring`, `/v1/tenants` | authorization-code + PKCE | `fraud_analyst` / `ops_viewer` / `tenant_admin` |

Host systems fetch a token, then call the endpoint:

```bash
TOKEN=$(curl -s http://localhost:8081/realms/vigilant/protocol/openid-connect/token \
  -d grant_type=client_credentials \
  -d client_id=loob-bank-host -d client_secret=loob-host-dev-secret | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  http://localhost:8080/v1/decisions/payout-check \
  -d '{"tenantId":"loob-bank","referralCode":"LOOB-…","refereeUserId":"u2"}'
```

The token's `tenant_id` claim is enforced against the body's `tenantId` on every
call (`TenantAccessGuard`), so a token for one tenant can't act on another.

## Scoring v1 (weighted rules, auditable)

`VELOCITY_BURST` (fan-out z-score) · `DEVICE_COLLISION` · `IP_SUBNET_COLLISION` ·
`CYCLE_DETECTED` (JGraphT) · `DATACENTER_OR_VPN_IP` (local ASN lookup).
Bands: `< 0.40` APPROVE, `< 0.75` HOLD, `>= 0.75` REJECT.

The datacenter check runs at redemption (soft-flag, stored on the node) and
contributes to the score at payout — a datacenter IP alone lands in HOLD.

## Running

```bash
# Run the backend from its module. Spring Boot's docker-compose support starts the
# backends (Neo4j + Postgres + the geoip ASN provisioner) via the repo-root
# docker-compose.yml, waits until they're healthy, and connects automatically.
# Same for the "VigilantApplication" IDE run config.
cd backend && ./mvnw spring-boot:run

# Everything as containers instead — from the repo root (backends + geoip + service)
docker compose --profile full up --build

# Backends only (e.g. a fast TDD loop without the docker-compose module)
docker compose up neo4j postgres geoip
```

The `geoip` service downloads DB-IP's IP-to-ASN Lite database into `./.local/geoip`
(git-ignored, at the repo root) and is health-checked, so the app — which **fails
fast** without the database — only starts once it's present. Docker Compose
management is auto-skipped under tests. See the
[ip-reputation README](src/main/java/com/turing/vigilant/ipreputation/README.md)
for manual provisioning and the data-source rationale.

## Full stack — dashboard + seeded demo data

To run the whole system end-to-end (login → populated queue → graph explorer →
ops metrics) against realistic seeded data, in three steps:

```bash
# 1. Infra + Keycloak (Neo4j, Postgres, geoip, Keycloak w/ realm + test users)
docker compose --profile auth up -d

# 2. Backend on the `dev` profile — seeds Postgres + Neo4j on boot (DevDataSeeder)
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
#    ↳ re-seed from scratch (wipes this tenant's cases + graph):
#      SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run \
#        -Dspring-boot.run.jvmArguments="-Dvigilant.dev-seed.reset=true"

# 3. Dashboard against the real backend (not the mock layer)
cd frontend && VITE_USE_MOCK=false npm run dev   # http://localhost:5173
```

Log in at http://localhost:5173 with a **test-only** Keycloak user (dev
passwords, not real accounts — see [infra/keycloak](infra/keycloak/README.md)):

| User | Role | Sees |
|---|---|---|
| `analyst-loob` / `password` | `fraud_analyst` | case queue + actions + monitoring |
| `ops-loob` / `password` | `ops_viewer` | monitoring only (no case actions) |

**Seed data** (`DevDataSeeder`, `dev` profile only — never runs in prod): 7
`loob-bank` cases — 5 open (organic, fan-out burst, shared-device cluster, cycle,
and a multi-edge-type overlap showcase at the top score) + 2 resolved (one
false-positive, one confirmed fraud) — each with a matching Neo4j subgraph, so
every case's graph explorer renders. Written through the real graph write path
and case entity, so shapes match production. Idempotent: check-and-skip unless
`vigilant.dev-seed.reset=true`.

> **Known gap — resolution webhook:** taking a decision POSTs a decision webhook
> to the tenant's `callback-url` **synchronously inside the resolution
> transaction** (`WebhookDispatcher`). If the host callback is unreachable, the
> resolve request fails with 500 and the decision is **rolled back**. In dev
> either run a listener on the callback port (`http://localhost:9090`) or treat a
> 500-on-resolve as "no callback host running", not a queue bug. Making the
> webhook async / best-effort (so a down host doesn't reverse the analyst's
> decision) is a follow-up.

## Tests

```bash
cd backend && ./mvnw test
```

Integration tests use Testcontainers (real Neo4j + Postgres), so Docker must be
running. The surefire config pins `-Dapi.version=1.44` for compatibility with
Docker Engine 29.
