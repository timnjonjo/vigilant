# Vigilant — 200+ TPS Performance Audit

Single-instance load audit of the host-driven hot endpoints, with the fixes that
took them from ~7 TPS to 200+ TPS. Horizontal scaling is explicitly out of scope —
this establishes the single-instance ceiling first.

## Scope (Phase 1)

"Transaction" = a redemption event, a conversion event, or a payout-check — not a
generic HTTP request. The 200 TPS target applies to the **host-driven** endpoints:

| Endpoint | Role | In scope |
|---|---|---|
| `POST /v1/events/redemption` | Highest volume (fires at every referred signup) | **Primary** |
| `POST /v1/decisions/payout-check` | Highest per-request cost (subgraph pull + scoring) | **Primary** |
| `POST /v1/events/conversion`, `POST /v1/codes/generate` | Moderate | Secondary |
| `GET /v1/cases`, `/v1/monitoring`, `/v1/campaigns`, `/v1/tenants` | Analyst-driven, not host load | **Out** |

## Test environment

- Single app instance, Neo4j 5.26 + Postgres 16 + Keycloak 26 via docker-compose.
- **Synthetic dataset** (`perf/seed/`): ~228k `Account` nodes for `loob-bank`,
  208k `REFERRED` edges across 4 campaigns, 200 dense fraud rings with
  `SHARES_DEVICE`/`SHARES_IP_SUBNET` clusters; 80k `fraud_case` rows in Postgres.
  Sized so the graph query costs are realistic (the demo seed is far too small to
  surface them).
- **Tool: k6** (`perf/k6/load.js`) — low client overhead, first-class latency
  percentiles, realistic auth (client-credentials `host_integration` token fetched
  once in `setup()`).

Run: `k6 run -e BASE_URL=http://localhost:8090 -e SCENARIO=payout -e VUS=50 -e DURATION=30s perf/k6/load.js`

## Results (deliverable #1)

| Endpoint | Baseline TPS | After TPS | p95 before → after | p99 before → after |
|---|---|---|---|---|
| payout-check | 7.0 | **201.8** | 8.86 s → **0.50 s** | 9.27 s → **0.69 s** |
| redemption | 9.0 | **316.8** | 7.67 s → **0.29 s** | 8.22 s → **0.42 s** |
| blended 4:1 | — | **262.7** | — → **0.53 s** | — → **1.23 s** |

50 VUs (60 for blended), 25–30 s each. All in-scope endpoints now exceed 200 TPS.

## Bottlenecks found, by layer (deliverable #2)

**Neo4j — the entire bottleneck. Postgres sat at <2% CPU throughout; Neo4j was pegged at ~570% (5–6 cores).**

1. **Code lookup was a full label scan (dominant).** Every redemption, conversion,
   payout-check and code-generate resolves a referrer from a code. The code lived
   in a **list property** `Account.referralCodes`, matched by `$code IN
   a.referralCodes` — list membership **cannot be indexed**, so the planner ran a
   `NodeByLabelScan` over **all 228,439 accounts** then filtered.
   - `PROFILE` (ring code): first loadNeighbourhood query = **685,401 db-hits**.
   - `EXPLAIN`: `NodeByLabelScan(r:Account)` → `Filter(... IN r.referralCodes)`.

2. **`fanoutBaseline` recomputed the whole campaign per request (dominant once #1
   was fixed).** The per-campaign fan-out baseline is identical for every
   payout-check on a campaign, yet it was recomputed each call, scanning **all**
   of that campaign's `REFERRED` edges.
   - `PROFILE` (camp-a, 52,099 edges): **208,397 db-hits** *per payout-check*.

3. **Unbounded variable-length traversal (latent).** `loadNeighbourhood` runs
   `(r)-[:REFERRED* {campaignId}]-(m)` — `EXPLAIN` shows `REFERRED*..2147483647`,
   an uncapped BFS. Not the dominant cost on this dataset (referral depth is
   shallow) but a real tail risk on deep/dense rings. **Left semantically
   unchanged** (capping depth changes fraud semantics — flagged for a decision).

4. **Dead index.** A stale `account_referral_code` RANGE index on the removed
   scalar property — pure write overhead. Dropped.

**Not bottlenecks (checked, ruled out with evidence):**

- **Auth / Keycloak** — resource server validates JWTs against cached JWKS locally
  (no per-request Keycloak call); 316 TPS sustained *with* auth on. Not a factor.
- **Connection pools** — Neo4j was CPU-bound, not connection-bound (Bolt driver
  default pool 100 > 50 VUs). Hikari (Postgres) default 10 never pressured —
  Postgres idle. *Watch item:* a payout mix that HOLDs/REJECTs (writes a case) on
  most calls could pressure Hikari 10 at much higher throughput; revisit if the
  case-write rate climbs.
- **Sequential rule execution** — the 5 scoring rules run on the already-loaded
  in-memory neighbourhood (micro-seconds). Parallelizing them would add thread
  overhead for ~zero gain. **Deliberately not changed.**
- **IP reputation** — Tier-1 local mmdb ASN lookup only; no external Tier-2 call
  exists in the codebase, so there is no external timeout to audit. Fast under
  load (it's on the 316 TPS redemption path).

## Changes made (deliverable #3)

1. **Referral codes are now indexed `:ReferralCode` nodes** (`{tenantId, code}`
   unique constraint) linked to the referrer by `ISSUED_TO`, replacing the
   `Account.referralCodes` list. The four hot lookups became `NodeUniqueIndexSeek`
   — **685,401 → 89 db-hits** for the same ring code. (`Neo4jGraphStore`)
2. **`FanoutBaselineCache`** — short-TTL (30 s, configurable via
   `vigilant.scoring.fanout-baseline-ttl`) per-`(tenant, campaign)` memoisation of
   the baseline, single-flight to avoid a TTL-expiry stampede. Turns an
   O(campaign edges) scan into an O(1) map read on the hot path. Cold call 1.12 s
   → cache hit **30 ms**. (`PayoutDecisionService`)
3. **Dropped the dead `account_referral_code` index.**

## Case-detail referral cluster (analyst UI, follow-on fix)

Separate from the host-driven TPS work: the case-detail graph explorer
(`GET /v1/cases/{id}/graph`) reused the scoring path `loadNeighbourhood`, which
runs an **unbounded** `REFERRED*` traversal, widens the cluster by a
shared-attribute hop, and fetches **both** `SHARES_DEVICE` and `SHARES_IP_SUBNET`
regardless of what the case actually flagged. On a dense ring that made the
cluster slow to load and noisier than the reason codes justify.

Fix: a dedicated, bounded visualization query (`loadCaseVisualization`) that caps
depth (`REFERRED*1..3`, directed) and node count (250), drops the widening hop,
and fetches only the overlap edge type behind the case's `reasonCodes` — device
edges for `DEVICE_COLLISION`, subnet edges for `IP_SUBNET_COLLISION`, **neither**
for velocity/cycle/datacenter-only cases. Scoring's `loadNeighbourhood` is
unchanged (full graph semantics preserved). The frontend legend now renders only
the edge types actually present.

Before/after — total Neo4j DB accesses for the endpoint's Cypher on the densest
seeded ring (`LOOB-0`, 150-node ring with device + subnet clusters):

| Graph-endpoint Cypher | DB accesses |
|---|---|
| OLD — unbounded traversal + expansion hop + both shared-edge fetches | 63,512 |
| NEW — device-collision case (bounded, device edges only) | 7,387 (8.6× fewer) |
| NEW — velocity/cycle-only case (no overlap edges fetched) | 4,042 (15.7× fewer) |

Covered by `Neo4jGraphStoreIT` (device-only / subnet-only / none / unknown-code)
and `CaseVisualEdgeSelectionTest` (reason-code → edge-type mapping).

## Cursor (keyset) pagination — verification

The case queue, campaign list and case-audit stream page with signed keyset
cursors (no `OFFSET`, no `COUNT`). Verified as working as intended:

- **Correctness (`CasePageServiceIT`, Testcontainers Postgres):** a full walk over
  a seeded set with many tied scores/timestamps covers every tenant row exactly
  once — no duplicates, no gaps — in the exact `score DESC, opened_at DESC, id DESC`
  (or `opened_at ASC, id ASC`) order, the chain is tenant- and campaign-scoped, and
  the final page returns a null cursor.
- **Cursor integrity (`CursorCodecTest` + service-level):** cursors are HMAC-signed
  and bound to `(resource, filter, sort)`. Tampering the payload or signature, a
  wrong resource, a **stale filter/sort** (e.g. a camp-1 cursor replayed against
  camp-2, or a score cursor replayed under age sort), a foreign signing key, and a
  malformed token shape are all rejected with `400 InvalidCursorException`.
- **Limits:** `limit` outside 1..100 and an unknown `sortBy` are rejected.
- **Query plans (live 82k-row `fraud_case`, `EXPLAIN ANALYZE`):** every page is an
  `Index Scan` on the matching `*_cursor` index with the keyset predicate pushed
  into the `Index Cond` (`ROW(score,opened_at,id) < ROW(...)`) — **no Sort, no Seq
  Scan, no OFFSET.** Depth-independent:

  | Deep page (offset-40000 position) | Time | Rows read |
  |---|---|---|
  | Keyset (`ROW(...) < ROW(...)`) | **0.12 ms** | 26 |
  | `LIMIT 26 OFFSET 40000` (same order) | 18.97 ms | 40,026 |

  ~150× faster and flat with depth — OFFSET rereads and discards everything before
  the page. Note: the audit stream is a `UNION ALL` of opened/resolved events per
  referral code (small per code), so it sorts the union rather than index-walking;
  acceptable at that cardinality.

## Still open / follow-ups (deliverable #4)

All in-scope endpoints hit 200+ TPS single-instance, so no architectural rewrite
was forced. Remaining items, with evidence:

- **Unbounded `REFERRED*` traversal** — cap depth (e.g. `*1..3`) once the intended
  fraud semantics are confirmed. Latent tail risk; not triggered by this dataset.
- **`loadNeighbourhood` is 5 sequential Bolt round-trips** — fine at current
  latency (payout p95 0.5 s), but combinable if the target rises.
- **Baseline cache is per-instance** — when the app scales out (out of scope),
  each instance keeps its own cache; acceptable (each still bounded to 1
  recompute/TTL) but note it for the scaling phase.
- **Hikari pool = 10 (default)** — not a limit today; revisit with the case-write
  rate if payout HOLD/REJECT volume grows.
