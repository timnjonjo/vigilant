# Backend logging

The goal: logs you can *follow*. Every request is correlated end to end, and the
decisions that matter are logged as structured events — readable in dev, queryable
JSON in prod — with no secrets or PII.

## Correlation id

`RequestCorrelationFilter` (runs first, before security) gives every request an
`X-Request-Id`:

- an inbound `X-Request-Id` header is honoured when well-formed (`[A-Za-z0-9._-]{1,64}`),
  so a host can stitch our logs to theirs; anything missing or unsafe gets a fresh
  UUID (unsafe values are never echoed — no log injection).
- it is bound to the log `MDC` as `requestId` for the whole request and echoed on
  the response, then cleared (never leaks onto the next request on a pooled thread).

Because the filter runs before the security chain, even 401/403s are correlated.

## Business events

Business events are logged through `DomainEvent` (SLF4J key/value pairs), not
string concatenation:

```java
DomainEvent.of(log, "payout_decision")
    .field("tenantId", tenantId.value())
    .field("action", action)
    .field("caseId", caseId)
    .log();
```

Instrumented so far: `code_issued` (CodeIssuanceService) and `payout_decision`
(PayoutDecisionService).

### Redaction

Never logged at INFO: tokens/credentials, raw device ids, raw IP addresses, or
**referee user ids** (PII). A HOLD/REJECT's `caseId` links to the referee in
Postgres when a review genuinely needs it. Safe to log: tenant/campaign ids,
referral codes, decisions, scores, reason codes, case ids, the risk flag. These
rules are pinned by tests (`PayoutDecisionServiceTest`, `CodeIssuanceServiceTest`).

## Output — dev vs prod

**Dev console** (`application.yaml`): a readable line with the request id and the
event fields inline (`%kvp`). Real output from the E2E slice:

```
18:44:14.671 INFO  [7aa91f6a-534e-4660-8cc0-3687fe21632e] c.t.v.decisions.PayoutDecisionService : payout_decision event="payout_decision" tenantId="loob-bank" campaignId="e2e-…" referralCode="LOOB-BANK-4C7505A7" action="HOLD" score="0.45" reasonCodes="[DEVICE_COLLISION]" caseId="1"
```

**Prod** (`application-prod.yaml`: `logging.structured.format.console=ecs`): the same
event as one Elastic Common Schema JSON object per line — `requestId` and every
field become discrete, queryable fields (Boot's built-in structured logging, no
extra dependency). The approved `payout_decision` shape:

```json
{"@timestamp":"2026-07-14T18:44:14.671Z","log.level":"INFO","message":"payout_decision","requestId":"7aa91f6a-534e-4660-8cc0-3687fe21632e","event":"payout_decision","tenantId":"loob-bank","campaignId":"e2e-…","referralCode":"LOOB-BANK-4C7505A7","action":"HOLD","score":0.45,"reasonCodes":"[DEVICE_COLLISION]","caseId":"1"}
```

## Tests

- `RequestCorrelationFilterTest` — id generated/honoured/sanitised, exposed for the
  request, cleared after.
- `PayoutDecisionServiceTest#logsAStructuredPayoutDecisionEventWithoutRefereePii`
- `CodeIssuanceServiceTest#logsAStructuredCodeIssuedEventWithoutIdentityFields`

## Not yet instrumented (same pattern applies)

Redemption/conversion ingestion, case resolution, auth 401/403 details, and webhook
dispatch latency/failure — each a `DomainEvent` at its service, following the
redaction rules above.
