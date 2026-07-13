# Vigilant — Referral Fraud Detection Engine

**Version:** v0.1 (initial refined spec)
**Author:** Timothy Njonjo Mwaura
**Purpose:** Design brief / build prompt for a pluggable, graph-based fraud
detection engine for referral programs, with an inbuilt monitoring dashboard.

---

## 1. Problem Statement

Fintechs running referral campaigns (e.g.  Bank's KES 350 per successful
referral) are vulnerable to Sybil-style abuse: users create or control many
"distinct" accounts to farm referral bonuses. Existing systems have no
structural way to detect this because they track referrals as flat
transactional records, not as a graph with identity signals attached.

Vigilant is a standalone service that plugs into any existing referral
program via events and webhooks, models referral activity as a
multi-attribute graph, scores risk in real time, and gates the **payout**
(never the signup) based on that score.

---

## 2. Core Design Principles

1. **Never block signup or account creation.** Blocking tips off fraudsters
   and creates false-positive customer friction. All enforcement happens at
   the payout decision point.
2. **Gate the money, not the user.** The only hard enforcement action
   Vigilant ever recommends is APPROVE / HOLD / REJECT on a bonus payout.
3. **Detection is layered, not just reactive.**
    - Layer 1 — Prevention: economic/product-level disincentives (delayed
      vesting, KYC-tier gating, referral caps) — owned by the host system,
      informed by Vigilant's identity checks.
    - Layer 2 — Real-time graph + identity scoring — Vigilant's core.
    - Layer 3 — Batch/nightly network forensics for ring discovery and
      retroactive clawback flagging.
4. **Multi-attribute graph, not just referral edges.** Fraud rings reveal
   themselves through *overlap* across referral, device, IP, and payment
   edges simultaneously — not any single edge type alone.
5. **Pluggable by contract, not by code integration.** Host systems
   integrate via REST/webhook events. Vigilant has no knowledge of the
   host's domain model (loans, wallets, etc.) — only users, edges, and
   events.
6. **Auditable by design.** Every score must come with reason codes.
   Rules-first approach (not a black-box model) so decisions can be
   explained to compliance/regulators — important in a fintech/AML context.
7. **Feedback loop.** Analyst decisions on flagged cases feed back into
   threshold/rule tuning over time.

---

## 3. User Journey (confirmed)

```
1. User requests referral code
      -> Vigilant: identity pre-check (device/IP/fingerprint uniqueness)
      -> Code is ALWAYS issued; risky identities are tagged, not denied
2. Code generated & returned
      -> Vigilant creates/updates referrer node with fingerprint at issuance time
3. User shares code (out of band — no system event)
4. Referee signs up using the code (REDEMPTION)
      -> Vigilant creates REFERRED edge + SHARES_DEVICE / SHARES_IP edges if matched
      -> Cheap real-time checks run (velocity, collision)
      -> Signup is ALWAYS allowed; elevated risk soft-flags a case, non-blocking
5. Referee completes qualifying action (CONVERSION: deposit / transaction / N-day retention)
      -> Vigilant runs full scoring pass (graph + identity + behavioral + prior flags)
      -> Host calls payout-check decision endpoint
      -> APPROVE / HOLD (manual review, async) / REJECT (bonus cancelled)
6. Ongoing nightly batch re-scan
      -> Whole-graph ring detection, may retroactively flag already-paid bonuses for clawback review
```

---

## 4. Fraud Patterns to Detect (and primary countermeasure)

| Pattern | Description | Primary Signal |
|---|---|---|
| SIM farming | Multiple SIMs/phones per real person | Cross-check identity attributes beyond phone number |
| Device farming | Reinstalls/emulators/cloned APKs, one device many accounts | Device fingerprint clustering |
| Self-referral loops | Small ring refers each other back and forth | Cycle detection in referral graph |
| Bonus-and-bail | Referred account claims bonus, goes dormant | Post-conversion activity monitoring, delayed vesting |
| Agent-assisted farming | Cyber café / agent runs scheme for many "customers" | IP/location clustering, agent-code anomaly detection |
| Bot/API abuse | Scripted signups bypassing the app | Rate limiting, velocity anomaly, behavioral biometrics (host-side) |

---

## 5. Graph Model

**Nodes:** `UserAccount` (tenant-scoped)

**Edge types:**
- `REFERRED` (directed, referrer -> referee, weighted by time-to-conversion)
- `SHARES_DEVICE` (undirected, device fingerprint match)
- `SHARES_IP_SUBNET` (undirected, IP/ASN match within a time window)
- `SHARES_PAYMENT_METHOD` (undirected, same M-Pesa till/paybill/number funding the account)
- `SHARES_IDENTITY_ATTRIBUTE` (undirected, e.g. same next-of-kin/KRA PIN, if available)

**Key detection primitives, ranked by signal strength:**
1. Multi-edge-type overlap (referral + device + payment coincide) — strongest
2. Temporal fan-out burst vs. rolling population baseline (z-score, not fixed threshold)
3. Dormancy after conversion (no activity N days post-bonus)
4. Structural anomalies (cycles, near-star topology, shallow-wide trees)
5. Cohort/statistical outlier comparison across all referral codes

---

## 6. Scoring & Decisioning

- Weighted rule-based scorer (v1), interpretable and auditable.
- Every score returns `reasonCodes` (e.g. `DEVICE_COLLISION`, `VELOCITY_BURST`, `CYCLE_DETECTED`).
- Score bands drive **graduated actions**, not a single cutoff:
    - Low → APPROVE, pay immediately
    - Medium → HOLD, async manual review queue, referrer sees "pending review"
    - High → REJECT, bonus cancelled, case logged for audit
- Reviewed cases (confirmed fraud / confirmed legitimate) become labeled data for recalibrating thresholds and rules over time — leaves room for an ML layer later without requiring one at v1.

---

## 7. Integration Contract (Pluggability)

Host systems never touch Vigilant's internal graph/scoring model — only
these HTTP contracts:

**Code issuance**
```
POST /v1/codes/generate
{ "tenantId", "userId", "deviceId", "ipAddress" }
-> { "status": "ISSUED", "referralCode", "riskFlag" }
```
(Never returns DENIED — risk is tagged on the node, not blocked.)

**Redemption event (async, fire-and-forget)**
```
POST /v1/events/redemption
{ "tenantId", "referralCode", "newUserId", "deviceId", "ipAddress", "timestamp" }
-> { "action": "ACCEPT" }  // always ACCEPT; signup is never gated
```

**Conversion event**
```
POST /v1/events/conversion
{ "tenantId", "referralCode", "refereeUserId", "conversionType", "timestamp" }
```

**Payout decision (synchronous-ish, host calls before releasing funds)**
```
POST /v1/decisions/payout-check
{ "tenantId", "referralCode", "refereeUserId" }
-> { "action": "APPROVE" | "HOLD" | "REJECT", "score", "reasonCodes" }
```

**Async decision webhook (for HOLD cases resolved later by an analyst)**
```
Vigilant -> Host: POST {hostCallbackUrl}
{ "referralCode", "refereeUserId", "finalAction": "APPROVE" | "REJECT", "caseId" }
```

**Auth:** per-tenant API key or mTLS; every node/edge/query scoped by `tenantId`.

**Optional SDK:** thin Java client wrapping the above — convenience only, not the source of truth. The HTTP contract *is* the integration surface.

---

## 8. Dashboard (Inbuilt Monitoring)

Separate application surface, not embedded in the host system's UI. Two audiences:

**Fraud Analyst view (case management):**
- Queue of flagged cases sorted by score / age
- Interactive graph explorer for a flagged cluster (Cytoscape.js / vis.js) — visualize the actual subgraph, not just a score
- Approve / Hold / Reject / Escalate actions, feeding back into the scoring loop
- Audit trail per case (who decided, when, why)

**Product/Ops view (aggregate monitoring):**
- Fraud rate over time, KES value of blocked/held payouts
- False-positive rate from reviewed cases
- Top referral codes by volume and by risk
- Alerting on pattern spikes or baseline shifts

---

## 9. Architecture

```
Host System(s)
     |
     v
Event Ingestion API (REST, tenant-scoped)
     |
     v
Graph Store (Neo4j — durable, queryable independent of scoring process)
     |
     v
Scoring Engine (rules-based v1; JGraphT for in-process algorithms
                 like cycle detection on pulled subgraphs)
     |
     +--> Case Queue (Postgres) --> Dashboard (separate app, React/Next.js)
     |
     +--> Decision Webhook --> back to Host System
```

**Why Neo4j over in-memory JGraphT as system of record:** a standalone
pluggable service with its own dashboard needs durability across restarts
and independent queryability by the dashboard — can't hold every tenant's
whole graph in memory. JGraphT remains useful for in-process algorithms run
against subgraphs pulled from Neo4j (cycle detection, bounded traversal),
not as the store itself.

---

## 10. Open Decisions (to refine next)

- [ ] Multi-tenancy: build in from day one, or single-client (Loob Bank) with room to grow later?
- [ ] Dashboard write actions: does Approve/Hold/Reject in the dashboard call back into the host system to actually release/withhold funds, or is it advisory only?
- [ ] Deployment target: shared EKS cluster vs. standalone package deployable on a client's own infra?
- [ ] Clawback support: can already-paid M-Pesa bonuses be reversed, or is post-payout detection purely for banning future codes / reporting?
- [ ] HOLD case SLA: what's the expected analyst review turnaround, and does the referrer need a visible "pending" state in the host app in the meantime?

---

## 10a. Campaign Model (added v0.2)

**Decision:** Vigilant owns campaign creation/management. Tenants create
and configure campaigns through the Vigilant dashboard (not through the
host system); referral codes are generated against a specific tenant
campaign, not just a tenant.

**Rationale:** bonus amount, conversion criteria, and campaign duration
vary per campaign and directly drive scoring baselines (fan-out velocity,
cohort comparison). Centralizing campaign config in Vigilant means the
scoring engine always has authoritative context for what "normal" looks
like for that specific campaign, and removes the need for the host system
to duplicate or resync this config elsewhere.

**Entity:**
```
Campaign {
  campaignId
  tenantId            // owning tenant
  name
  bonusAmount          // e.g. KES 350 - no longer hardcoded anywhere
  startDate / endDate
  status               // DRAFT | ACTIVE | PAUSED | ENDED
  conversionCriteria   // e.g. FIRST_DEPOSIT, N_DAY_RETENTION - per campaign, not global
  referralCapPerUser   // optional campaign-level abuse guardrail
}
```

**Graph model change:** campaign identity lives on the `REFERRED` edge, not
the `UserAccount` node, since a user can participate in multiple campaigns
over time:
```
(:UserAccount)-[:REFERRED {campaignId, timestamp}]->(:UserAccount)
```
`SHARES_DEVICE` / `SHARES_IP_SUBNET` edges remain campaign-agnostic —
identity overlap across campaigns is a valid, even stronger, fraud signal.

**Scoring impact:** velocity/fan-out baselines and cohort/statistical
outlier comparisons (section 5, primitives #2 and #5) are computed
per-`(referrerId, campaignId)` and per-campaign respectively, not
tenant-wide — a high-incentive campaign naturally draws more aggressive
(not necessarily fraudulent) referral activity than a low-incentive one.

**API contract changes (supersedes section 7's code/event payloads):**
```
POST /v1/campaigns                      # Vigilant dashboard -> Vigilant backend
GET  /v1/campaigns/{campaignId}
POST /v1/codes/generate
  { tenantId, campaignId, userId, deviceId, ipAddress }
POST /v1/events/redemption
  { tenantId, campaignId, referralCode, newUserId, deviceId, ipAddress, timestamp }
POST /v1/events/conversion
  { tenantId, campaignId, referralCode, refereeUserId, conversionType, timestamp }
POST /v1/decisions/payout-check
  { tenantId, campaignId, referralCode, refereeUserId }
```

**Host system relationship:** since Vigilant now owns campaign
definitions, the host system needs a way to know which campaign a given
signup/redemption belongs to (so it can pass `campaignId` on events) and
what the current `bonusAmount`/`conversionCriteria` are (so it knows what
to actually pay out and when). Two realistic options, to be decided when
this is built:
- Host system calls `GET /v1/campaigns?tenantId=...` to read active
  campaign config from Vigilant directly, or
- Vigilant pushes campaign create/update events to a host-provided webhook
  **Dashboard impact:** case queue and ops/monitoring views gain a campaign
  filter alongside the tenant filter; a new Campaign Management view is
  needed for tenant admins to create/edit/pause campaigns (likely gated to
  the `tenant_admin` role from the Keycloak work).

---

## 11. Non-Goals (v1)

- Not a general-purpose AML/transaction-monitoring system (though it borrows structurally from one).
- Not an ML-first system — rules-based scoring is the v1 target; ML is a future layer once labeled case data accumulates.
- Not responsible for KYC/identity verification itself — consumes identity signals from the host system, doesn't perform verification.