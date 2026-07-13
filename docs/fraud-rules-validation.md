# Vigilant fraud-rule validation report

**Validated:** 2026-07-13  
**Specification:** `Vigilant Engine Spec.md`, read in full; sections 4–6 and 10a treated as normative  
**Scope:** current v1 payout scorer, graph queries, score bands, IP reputation, and campaign scoping

## Executive result

The current scorer has five rules. All five fire on their clear positive cases and stay silent on structurally clean controls. The principal risk is not failure to detect the canonical patterns; it is that four rules cannot distinguish the explicitly requested legitimate lookalikes from fraud:

- a viral business receives the same 0.40/HOLD as a bot burst;
- siblings sharing a phone receive the same 0.45/HOLD as a device farm, and sharing the home router as well reaches 0.75/REJECT;
- a two-friend cycle receives the same 0.50/HOLD as a deliberate ring;
- a corporate VPN receives the same 0.70/HOLD as a fraud proxy.

An ordinary shared-router collision emits a 0.30 risk reason but remains APPROVE. That limits payout friction, although the rule has still failed the stricter “stay silent” requirement.

Two provable implementation defects were fixed during this validation:

1. The velocity observation used 24 hours but its population baseline used all historical referrals. Both now use the same rolling window.
2. Cross-campaign device/IP edges existed in Neo4j but accounts found only through those edges were excluded from the scoring neighbourhood. The loader now expands the campaign referral component by one shared-attribute hop; referral traversal remains campaign-scoped.

No ambiguous rule qualifier or threshold was changed.

## 1. Current rule inventory

The scorer adds each fired rule's weight once and clamps the sum to 1.0. Reason order follows rule order.

| Rule / reason code | Actual check | Current weight / threshold | Implementation |
|---|---|---:|---|
| `VELOCITY_BURST` | Direct fan-out of the selected referrer/campaign inside `[evaluatedAt - 24h, evaluatedAt]`; fires when z-score ≥ 3.0 or count ≥ 20 | 0.40 | `VelocityBurstRule`, defaults in `ScoringWeights`, population query in `Neo4jGraphStore` |
| `DEVICE_COLLISION` | Any `SHARES_DEVICE` edge in the loaded neighbourhood | 0.45 | `DeviceCollisionRule` |
| `IP_SUBNET_COLLISION` | Any `SHARES_IP_SUBNET` edge in the loaded neighbourhood | 0.30 | `IpSubnetCollisionRule` |
| `CYCLE_DETECTED` | Any directed cycle in the campaign referral neighbourhood, including a two-node cycle | 0.50 | `CycleDetectionRule` |
| `DATACENTER_OR_VPN_IP` | Any account in the loaded neighbourhood whose last stored IP type is `DATACENTER` | 0.70 | `DatacenterIpRule`; classification is stored during issuance/redemption |

Decision bands are configured as follows:

| Score | Decision | Boundary behavior |
|---:|---|---|
| `< 0.40` | APPROVE | 0.30 IP-only collision approves |
| `0.40` to `< 0.75` | HOLD | equality at 0.40 holds |
| `≥ 0.75` | REJECT | equality at 0.75 rejects |

The numerical boundaries appear in configuration/code, but the spec defines only Low/Medium/High and does not justify 0.40 or 0.75.

### Spec primitive cross-check

| Section 5 primitive / graph feature | Coverage | Finding |
|---|---|---|
| Multi-edge-type overlap | Partial | Device and IP rules add independently. There is no explicit interaction/overlap rule, referral coincidence adds no weight, and payment/identity-attribute edges are absent. |
| Temporal fan-out burst vs rolling baseline | Implemented after fix | Observation and per-campaign population baseline now share the same 24-hour window. Cold-start fallback remains count ≥ 20. |
| Dormancy after conversion | Missing | Conversion is stored, but post-conversion activity is not modelled and there is no dormancy reason/rule. |
| Structural anomalies | Partial | Cycles are implemented. Near-star and shallow-wide topology rules are absent. |
| Cohort/statistical comparison | Partial | The velocity rule uses a per-campaign fan-out mean/stddev. There is no separate conversion-quality or referral-code cohort rule. Codes with zero redemptions cannot join the graph baseline. |
| `SHARES_PAYMENT_METHOD` | Missing | No graph edge or scoring rule. |
| `SHARES_IDENTITY_ATTRIBUTE` | Missing | No graph edge or scoring rule. |

`DATACENTER_OR_VPN_IP` is an implemented extension beyond the five ranked primitives. It is consistent with section 4's agent/IP-abuse pattern but its 0.70 weight is not specified in the design brief.

The IP edge's documented “within a time window” qualifier is not implemented: shared-subnet edges have no timestamp and are not expired. A window cannot be added responsibly until product supplies its duration and semantics (first match, last match, or concurrent use).

## 2. Per-rule validation

“Pass/fail” compares the requested behavioral expectation with actual behavior, not whether the test process is green. Known limitations are encoded as characterization tests that assert the actual score.

| Rule | Scenario | Expected | Actual score / decision / reasons | Result |
|---|---|---|---|---|
| Velocity | Bot drives 10 distinct signups in 30 minutes against mean 2, σ 1 | Velocity only; HOLD | **0.40 / HOLD** / `VELOCITY_BURST` | PASS |
| Velocity | 10 steady referrals outside 24h | Silent; APPROVE | **0.00 / APPROVE** / none | PASS |
| Velocity | Popular local business drives the same 10-customer burst with diverse devices/IPs | Silent; APPROVE | **0.40 / HOLD** / `VELOCITY_BURST` | **FAIL — indistinguishable** |
| Device | Four farm accounts share one fingerprint, with IP isolated | Device only; HOLD | **0.45 / HOLD** / `DEVICE_COLLISION` | PASS |
| Device | Two siblings share one phone, with IP isolated | Silent; APPROVE | **0.45 / HOLD** / `DEVICE_COLLISION` | **FAIL — indistinguishable** |
| Device | Similar handset model but distinct fingerprints | Silent | **0.00 / APPROVE** / none | PASS |
| IP subnet | Four farm accounts use one subnet with distinct devices | IP reason | **0.30 / APPROVE** / `IP_SUBNET_COLLISION` | PASS for firing; weight intentionally below HOLD |
| IP subnet | Household shares Safaricom home-fibre subnet with distinct devices | Silent; APPROVE | **0.30 / APPROVE** / `IP_SUBNET_COLLISION` | **FAIL for silence; action remains APPROVE** |
| Device + IP lookalike | Siblings share both one phone and one home-fibre router | Silent; APPROVE | **0.75 / REJECT** / `DEVICE_COLLISION`, `IP_SUBNET_COLLISION` | **FAIL — ordinary household reaches hard rejection** |
| Cycle | Directed three-account ring | Cycle reason; at least HOLD | **0.50 / HOLD** / `CYCLE_DETECTED` | PASS |
| Cycle | Two real friends refer each other once | Silent; APPROVE | **0.50 / HOLD** / `CYCLE_DETECTED` | **FAIL — indistinguishable** |
| Cycle | Acyclic referral chain | Silent | **0.00 / APPROVE** / none | PASS |
| IP reputation | Referee connects through a cloud proxy | Datacenter reason; HOLD | **0.70 / HOLD** / `DATACENTER_OR_VPN_IP` | PASS |
| IP reputation | Legitimate employee uses corporate VPN | Silent; APPROVE | **0.70 / HOLD** / `DATACENTER_OR_VPN_IP` | **FAIL — indistinguishable** |
| IP reputation | Traveller uses a non-cloud residential ASN | Silent | **0.00 / APPROVE** / none | PASS |
| IP reputation | Kenyan mobile carrier ASN | `MOBILE`, zero risk | **0.00 / APPROVE** / none | PASS |
| Dormancy gap | Converted user takes bonus and performs no later activity | Dormancy signal | **0.00 / APPROVE** / none | **FAIL — rule/data absent** |
| Dormancy gap | Converted user genuinely churns | Silent | **0.00 / APPROVE** / none | PASS in isolation, but identical to bonus-and-bail |

### Kenyan carrier allowlist re-verification

The automated test binds the actual `application.yaml` rather than recreating the list in test code. All configured entries are checked before datacenter classification and return `MOBILE` with risk 0.0:

| Carrier | Configured ASN | Result |
|---|---:|---|
| Safaricom | AS33771 | MOBILE / 0.0 |
| Airtel Networks Kenya | AS37287 | MOBILE / 0.0 |
| Telkom Kenya | AS12455 | MOBILE / 0.0 |

This verifies configuration and precedence, not the continuing accuracy of the ASN ownership. The production comment correctly requires current BGP verification before launch.

## 3. Combined-rule validation

| Scenario | Expected | Actual | Result / interpretation |
|---|---|---|---|
| Referral plus device plus IP coincide on the same accounts | Higher than every single-rule case; high band | **0.75 / REJECT** (`DEVICE_COLLISION`, `IP_SUBNET_COLLISION`) | Passes the band requirement, but is only **0.05** above the strongest single signal (datacenter 0.70). Referral coincidence has no contribution and there is no interaction bonus. |
| Legitimate household device + IP overlap | Should not hard-reject without another distinguishing signal | **0.75 / REJECT** (`DEVICE_COLLISION`, `IP_SUBNET_COLLISION`) | **FAIL. The fraud overlap and household overlap are structurally identical to the scorer.** |
| Fan-out z = 2.5 plus three same-subnet collision edges | Weak facts should not reject by accidental addition | **0.30 / APPROVE** (`IP_SUBNET_COLLISION`) | PASS. Subthreshold velocity contributes zero and repeated instances of one rule count once. This protects households but also makes collision-cluster size irrelevant. |
| Exact score-band boundary | Deterministic and documented | **0.70 = HOLD; 0.75 = REJECT** | Mechanical behavior PASS; threshold rationale needs product confirmation. |
| Device + IP + velocity + datacenter | Highest band, bounded score | **1.00 / REJECT** after clamping | PASS; clamping hides how far above 1.0 the raw evidence total is. |

The implementation is additive by rule type, not by edge count. That prevents a large household subnet from accumulating dozens of identical weights, but a two-account collision and a 100-account cluster receive the same contribution.

## 4. Adversarial/evasion limitations

| Rule | How a ring can evade it | Executable evidence / hardening candidate |
|---|---|---|
| Velocity | Pace referrals outside the 24-hour window or hold each identity below the absolute 20 count; distribute referrals across codes/campaigns/referrers | 25 referrals older than 24h score 0.0. Consider multiple windows and ring-level aggregate velocity, subject to campaign semantics. |
| Device | Rotate/emulate fingerprints or reset the stored last-known device | Distinct devices leave only the 0.30 IP signal; distinct devices plus rotating subnets score 0.0. Device attestation/history would be required. |
| IP subnet | Rotate residential/mobile proxies across subnets | Rotating subnets leave only the 0.45 device signal. Timestamped concurrent-use history and ASN/network features would improve this. |
| Cycle | Keep the referral graph as an open chain/tree, delay the closing edge, or close a loop in another campaign | An open four-node chain scores 0.0. Near-star/shallow-wide primitives are specified but absent. |
| Datacenter/VPN | Use residential proxies, compromised routers, or a hosting ASN absent from the configured list | A residential classification scores 0.0. ASN lists require maintenance; classification is only last-known state. |
| Dormancy | No evasion is currently needed; no rule exists. Once implemented, generate minimal periodic activity to stay just above the dormancy definition | Requires host activity events and a product-defined meaningful-activity threshold, not merely “any event.” |

Additional limitations:

- Scoring is referral-code/cluster-wide, not referee-specific: `ScoringRequest` does not contain the payout's referee ID. One risky neighbour can affect every payout for that code.
- Device/IP rules test existence only; cluster size, recurrence, and timing do not alter weight.
- Stored device/IP values are last-known, while already materialised overlap edges are not removed when attributes change.
- The population z-score includes the evaluated referrer. This is conventional but dilutes the outlier in very small cohorts; the absolute fallback then dominates.
- Score clamping discards raw severity above 1.0, reducing analyst visibility and recalibration data.

## 5. Campaign-scoping validation

| Requirement | Seeded scenario and actual evidence | Result |
|---|---|---|
| Honest cross-campaign activity must not combine into velocity | Same user makes 12 referrals in campaign A and 12 in campaign B. Each loaded fan-out is 12 and each score is **0.00**, rather than combining to the absolute threshold of 20. | PASS |
| Statistical comparison must be per campaign | Neo4j query filters `campaignId`; campaign A and B produce independent samples/means. A separate rolling-window test excludes an old 10-referral referrer and yields recent sample `{2,1}`: mean **1.5**, σ **0.5**, n **2**. | PASS after rolling-window fix |
| Cross-campaign device/IP farming must remain visible | A redeems in campaign A and B in campaign B using the same device and subnet. Campaign-A referral edges exclude B, while shared edges include A–B device and IP. Score is **0.75 / REJECT**. | PASS after neighbourhood-expansion fix |
| General cohort comparison per campaign | Only velocity fan-out cohorting exists. | **GAP — no separate cohort rule to validate** |

## Provably broken and fixed

1. **Mixed time domains in velocity z-score.** `VelocityBurstRule` observed 24 hours but `fanoutBaseline` counted all campaign history. The graph-store contract now accepts `windowStart/windowEnd`, and payout decisioning supplies the configured velocity window.
2. **Cross-campaign overlap stopped before scoring.** Neo4j materialised shared edges correctly, but `loadNeighbourhood` filtered both endpoints to campaign-referral IDs. It now expands those IDs by one device/IP hop, while keeping returned `REFERRED` edges filtered to the requested campaign.

## Product decisions required

1. Must velocity require a supporting identity signal, an allowlisted/business reputation flag, or only HOLD as it does now? The graph cannot infer “viral but legitimate” from equal fan-out alone.
2. What collision cardinality/recurrence makes a shared phone or router suspicious? Current behavior treats one family pair and a farm as the same rule hit.
3. Should a one-off two-node referral cycle HOLD, or should cycle size/frequency/time qualify it?
4. Is datacenter/VPN alone intended to carry 0.70/HOLD despite corporate VPN and travel false positives? Should it require another signal?
5. Define IP-overlap time-window duration and concurrency semantics before timestamp/expiry work.
6. Define dormancy: number of days, bonus versus conversion anchor, and “meaningful activity.” Genuine churn is structurally indistinguishable without host behavior data.
7. Confirm the 0.40/0.75 bands and the very small 0.05 separation between the strongest single rule and device+IP overlap.
8. Decide whether cluster size/repeat collisions should change weight and whether raw, unclamped score should be retained for analysts.

## Automated evidence

- Final `./mvnw test`: **120 tests, 0 failures, 0 errors, 1 skip**. The skip is the pre-existing optional MMDB fixture test; `*IT` tests are now included in the normal Surefire run.
- `FraudRulesValidationTest`: positive/negative rule behavior, legitimate lookalikes, combination/boundary behavior, missing dormancy, and evasion characterization.
- `Neo4jGraphStoreIT`: real Neo4j rolling baseline, per-campaign isolation, cross-campaign fan-out, and cross-campaign device/IP scoring.
- `LocalAsnReputationCheckerTest`: actual YAML allowlist binding and carrier-first classification.
- Existing `RuleBasedScorerTest` and `ScoreBandsTest`: lower-level rule aggregation and boundary regression coverage.

The dev seed now includes the viral business, shared household, two-friend cycle, genuine churn, corporate VPN, and cross-campaign identity-overlap contrast subgraphs alongside the original fraud examples.
