# Vigilant API and configuration security review

Date: 2026-07-14  
Scope: this repository, local Docker Compose, `127.0.0.1` services, and
Testcontainers only. No deployed or shared environment was targeted.

## Executive result

Five API flaws and two configuration flaws were confirmed. The two findings
that could directly release or misattribute money were fixed first: payout
decisioning now fails closed unless the exact referral has converted, and a
referral code is immutably campaign-bound at issuance. Request validation,
malformed-IP handling, analyst audit attribution, local port exposure, and
Keycloak client settings were also corrected.

Tenant isolation and role checks held under the attempted IDOR and privilege
tests. Cypher and relational queries use parameters/tenant-scoped repository
methods. CORS did not authorize a hostile origin, and both Swagger routes
returned 404 from a running `prod`-profile process.

## Findings

| ID | Flaw | Severity | Affected surface | Before-fix evidence / PoC | Fix | Verification |
|---|---|---:|---|---|---|---|
| SECURITY-001 | Invalid payout subjects failed open | **High** | `POST /v1/decisions/payout-check` | Unknown code and unconverted referee returned HTTP 200, `APPROVE`, score `0.0`, no reasons. `payout_fail_open.sh`. | Require an exact converted `(tenant, campaign, code, referee)` graph edge before scoring; generic HTTP 409 otherwise. | `PayoutDecisionServiceTest`, controller HTTP 409 test, local PoC. |
| SECURITY-002 | Referral code not bound to issuance campaign | **High** | code issuance, redemption, conversion, payout | A code issued for `camp-a` redeemed in `camp-b`; Neo4j persisted a `REFERRED {campaignId:'camp-b'}` edge. `cross_campaign_code.sh`. | Store `campaignId` on `:ReferralCode`; include it in every lookup/write; reject invalid redemption/conversion tuples. | `Neo4jGraphStoreIT.bindsAReferralCodeToItsIssuanceCampaign`, service regression, local PoC. |
| SECURITY-003 | Bean Validation annotations were inactive | **Medium** | every JSON controller | Blank `userId` returned HTTP 200 and created a blank account. | Add `@Valid` to all request bodies and bounds matching the existing 255-character schema (IP max 45). | Controller tests return 400 before calling services; `input_validation.sh`. |
| SECURITY-004 | Malformed IP text polluted graph identity data | **Medium** | code issuance, redemption | `not-an-ip` was stored as both `ipAddress` and `ipSubnet`, allowing false collision edges. | Validate without DNS, canonicalize valid literals, and convert missing/malformed input to `null` so signup/code issuance remains non-blocking. | Service tests assert null address; `input_validation.sh` queries Neo4j. |
| SECURITY-005 | Analyst could forge audit identity | **Medium** | `POST /v1/cases/{id}/resolve` | Submitted `resolvedBy:'forged-super-admin'`; HTTP 200 persisted exactly that actor. `forged_audit_actor.sh`. | Remove actor from the request contract; derive `preferred_username`, falling back to signed JWT `sub`. Frontend no longer sends it. | `CaseSecurityTest.resolutionAuditActorComesFromTheValidatedToken`, local PoC. |
| SECURITY-006 | Known local credentials combined with all-interface database bindings | **Medium** | Docker Compose Neo4j/Postgres/Keycloak | Docker reported `0.0.0.0`/`::` bindings for 5432, 7474, 7687, 8081, and 9000 while known dev credentials were in source. | Bind all published ports to `127.0.0.1`; inject passwords/secrets from ignored `.env`; keep placeholders in tracked files. | `docker compose config` and `config_hardening.sh`. Existing containers must be recreated to adopt new bindings. |
| SECURITY-007 | Dashboard client allowed password grant and wildcard redirect | **Medium** | Keycloak realm export | Public PKCE client also had direct grant enabled and `http://localhost:5173/*`. | Disable direct grant; use exact login/logout redirect and exact web origin; keep public client secret-free. | Realm JSON assertion in `config_hardening.sh`. Fresh import or explicit realm update is required for an existing Keycloak volume. |

Severity is specific to Vigilant: payout manipulation and cross-tenant leakage
drive High/Critical ratings; local-only developer exposure is rated lower.

## Endpoint authorization review

| Surface | Actual endpoints | Enforcement result |
|---|---|---|
| Host integration | `/v1/codes/generate`, `/v1/events/redemption`, `/v1/events/conversion`, `/v1/decisions/payout-check` | JWT required; `host_integration` required; `tenant_id` claim must exactly match body tenant. No token → 401, analyst → 403, other-tenant service token → 403. |
| Campaigns | `POST/GET /v1/campaigns`, `GET/PATCH /v1/campaigns/{campaignId}` | Writes require `tenant_admin`; reads require authentication; every operation checks the token tenant. Code issuance additionally requires `ACTIVE`; PAUSED/ENDED returned 409. |
| Cases | list, detail, graph, audit, `/v1/cases/{id}/resolve` | `fraud_analyst` or `tenant_admin`; tenant-scoped repository lookup prevents IDOR. Tenant A token against tenant B ID returned 403/404. `ops_viewer` resolve returned 403. |
| Monitoring and tenant context | `/v1/monitoring`, `/v1/tenants` | Authorized dashboard roles only; tenant guard applied. Tenant endpoint returns only the claim-selected tenant. |

The actual analyst mutation route is `/resolve`, not the `/decision` spelling in
the review prompt.

## Injection, validation, and response review

- All Neo4j statements pass external values as driver parameters. No input is
  concatenated into Cypher.
- Postgres access uses JPA derived queries that include tenant ID; no raw SQL
  constructed from request values was found.
- IP parsing rejects hostnames and cannot trigger DNS. Malformed values no
  longer become graph/subnet properties.
- Default error responses did not expose stack traces or query text. The tested
  hostile origin received no `Access-Control-Allow-Origin` header.
- Host payout responses still expose score and reason codes. That is a product
  contract decision, listed below, not silently changed in this audit.

## Configuration review

- Dashboard and Swagger clients are public PKCE/S256 clients with no embedded
  client secret. Swagger redirect is exact. Dashboard is now exact as well.
- Current tracked files contain environment placeholders, not live credential
  values. The initial Git commit still contains old **development-only** values;
  any realm imported from that revision must rotate its host-client secret.
- `prod` profile runtime check: `/swagger-ui.html` → 404,
  `/v3/api-docs` → 404, `/actuator/health` → 200.
- `npm audit --json`: 0 vulnerabilities. Maven OWASP Dependency-Check result is
  recorded below once the NVD update completes.
- Plain HTTP/Bolt/JDBC defaults are acceptable only for loopback development.
  There is no production deployment manifest here proving TLS to Keycloak,
  Neo4j, or Postgres.

## Confirmed/open control gaps not fixed

| Gap | Risk | Why it was not guessed at |
|---|---|---|
| No rate limiting on code generation, redemption, or payout probes | **Medium**; 30 rapid payout probes produced 30 responses and no 429. Enables graph pollution and threshold probing after credential compromise. | Production limits must be per tenant/endpoint and sized to host TPS. Implement at the authenticated gateway with application backstop after capacity/SLO approval. `rate_limit_probe.sh` intentionally remains red. |
| Resolution webhook has no signature/authentication and defaults to HTTP | **Potential High** if the host treats any callback as an authoritative payout decision. | Requires confirmation of the host callback trust contract and coordinated key/signature rollout. Add HMAC/mTLS and replay protection; do not invent one side of the protocol here. |
| Host response includes exact score/reason codes | **Potential Medium** information oracle if host credentials or responses leak. | Decide whether host integration needs explanations or only action/case ID. Analysts still need full reasons. |
| Non-local TLS requirements are not enforced in repository config | **Medium** deployment risk for sensitive graph/case/token traffic. | Requires production endpoints/certificates or a platform service-mesh contract. Local plaintext is intentionally loopback-only. |
| Git history contains former dev credentials | **Low** after rotation; values were explicitly local, but remain discoverable. | History rewriting is destructive and was not authorized. Rotate imported realm secrets; consider a coordinated history purge separately. |

## Operational migration note

At startup, legacy referral codes are campaign-backfilled only when their
existing referral edges identify exactly one campaign. Codes seen in multiple
campaigns, or unused legacy codes with no campaign evidence, deliberately remain
unbound and fail closed. Tenants should reissue those codes rather than infer a
campaign and risk recreating the vulnerability.

## Regression assets

Automated JUnit/AssertJ tests live with the affected modules. Re-runnable local
requests and configuration assertions are in [`security/poc`](poc/README.md).
