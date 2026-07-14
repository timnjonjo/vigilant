package com.turing.vigilant.graph;

import com.turing.vigilant.graph.GraphCommands.ConversionRecord;
import com.turing.vigilant.graph.GraphCommands.RedemptionRecord;
import com.turing.vigilant.graph.GraphCommands.ReferrerRegistration;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.DomainEvent;
import com.turing.vigilant.shared.IpType;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;
import jakarta.annotation.PostConstruct;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Neo4j-backed {@link GraphStore}. Cypher is written by hand for full control of
 * graph traversal, and every statement is scoped by {@code tenantId}. Timestamps
 * are stored as epoch-millis longs to avoid temporal-type mapping ambiguity.
 *
 * <p>Campaign model (spec §10a): {@code campaignId} lives on the {@code REFERRED}
 * edge, not the node, so a user can participate in multiple campaigns. Each code a
 * referrer issues is an indexed {@code :ReferralCode} node linked to that referrer
 * by {@code ISSUED_TO} (one referrer, several codes across campaigns) — the code
 * lookup on every hot path is an index seek, not a tenant-wide scan.
 * {@code SHARES_DEVICE} / {@code SHARES_IP_SUBNET} stay campaign-agnostic —
 * cross-campaign identity overlap is a valid, stronger signal.
 */
@Component
public class Neo4jGraphStore implements GraphStore {

    private static final Logger log = LoggerFactory.getLogger(Neo4jGraphStore.class);

    private final Driver driver;

    /** Explorer traversal is capped: the referrer plus referees within this many
     *  REFERRED hops, up to this many nodes. Scoring is unaffected (it uses the
     *  unbounded {@link #loadNeighbourhood}); this only bounds what the analyst UI
     *  renders so a dense ring can't stall the case page. */
    private static final int VIZ_MAX_DEPTH = 3;
    private static final int VIZ_MAX_NODES = 250;

    public Neo4jGraphStore(Driver driver) {
        this.driver = driver;
    }

    @PostConstruct
    public void initialiseSchema() {
        try (Session session = driver.session()) {
            session.run("""
                    CREATE CONSTRAINT account_tenant_user IF NOT EXISTS
                    FOR (a:Account) REQUIRE (a.tenantId, a.userId) IS UNIQUE
                    """).consume();
            session.run("""
                    CREATE INDEX account_device IF NOT EXISTS
                    FOR (a:Account) ON (a.tenantId, a.deviceId)
                    """).consume();
            session.run("""
                    CREATE INDEX account_subnet IF NOT EXISTS
                    FOR (a:Account) ON (a.tenantId, a.ipSubnet)
                    """).consume();
            // Relationship-property index for the per-campaign fan-out baseline.
            session.run("""
                    CREATE INDEX referred_campaign IF NOT EXISTS
                    FOR ()-[e:REFERRED]-() ON (e.campaignId)
                    """).consume();
            // Referral codes are first-class, indexed nodes (a :ReferralCode per
            // issued code, linked to its referrer) rather than a list property on
            // Account. A code lookup — done on every redemption, conversion and
            // payout-check — must be an O(1) index seek; a list-membership predicate
            // (`$code IN a.referralCodes`) can't be indexed and forced a full
            // NodeByLabelScan over every account in the tenant. The unique
            // constraint also backs the seek.
            session.run("""
                    CREATE CONSTRAINT referral_code_unique IF NOT EXISTS
                    FOR (c:ReferralCode) REQUIRE (c.tenantId, c.code) IS UNIQUE
                    """).consume();
            // Backfill legacy codes only when existing referral edges prove one
            // unambiguous campaign. Previously cross-campaign codes remain
            // unbound and therefore fail closed.
            session.run("""
                    MATCH (c:ReferralCode)-[:ISSUED_TO]->(a:Account)-[r:REFERRED]->()
                    WHERE c.campaignId IS NULL
                    WITH c, collect(DISTINCT r.campaignId) AS campaigns
                    WHERE size(campaigns) = 1
                    SET c.campaignId = campaigns[0]
                    """).consume();
            // Drop the dead scalar-code index left by the pre-campaign model
            // (property renamed to the removed referralCodes list); harmless but
            // pure overhead on writes.
            session.run("DROP INDEX account_referral_code IF EXISTS").consume();
        }
        // One boot-time signal that the referral graph's schema is in place. The
        // per-request read/write paths run at 200+ TPS and are deliberately NOT
        // logged per call — their outcome surfaces in the correlated code_issued /
        // payout_decision events instead.
        DomainEvent.of(log, "graph_schema_ready")
                .field("constraints", "account_tenant_user, referral_code_unique")
                .field("indexes", "account_device, account_subnet, referred_campaign")
                .log();
    }

    @Override
    public void registerReferrer(ReferrerRegistration reg) {
        Map<String, Object> params = params(
                "tenantId", reg.tenantId().value(),
                "campaignId", reg.campaignId().value(),
                "userId", reg.userId(),
                "deviceId", reg.deviceId(),
                "ipAddress", reg.ipAddress(),
                "ipSubnet", Subnets.subnetOf(reg.ipAddress()),
                "referralCode", reg.referralCode().value(),
                "ipType", reg.ipType().name(),
                "at", reg.at().toEpochMilli());
        try (Session session = driver.session()) {
            // The code is an indexed :ReferralCode node linked to its referrer (a
            // user may issue one code per campaign — several codes, one referrer).
            // MERGE keeps re-registration of the same code idempotent.
            session.executeWriteWithoutResult(tx -> tx.run("""
                    MERGE (a:Account {tenantId: $tenantId, userId: $userId})
                      ON CREATE SET a.createdAt = $at
                    SET a.deviceId = $deviceId,
                        a.ipAddress = $ipAddress,
                        a.ipSubnet = $ipSubnet,
                        a.ipType = $ipType,
                        a.ipReputationCheckedAt = $at
                    MERGE (c:ReferralCode {tenantId: $tenantId, code: $referralCode})
                      ON CREATE SET c.campaignId = $campaignId
                    WITH a, c
                    WHERE c.campaignId = $campaignId
                    MERGE (c)-[:ISSUED_TO]->(a)
                    """, params).consume());
        }
    }

    @Override
    public void recordRedemption(RedemptionRecord r) {
        Map<String, Object> params = params(
                "tenantId", r.tenantId().value(),
                "campaignId", r.campaignId().value(),
                "referralCode", r.referralCode().value(),
                "refereeUserId", r.refereeUserId(),
                "deviceId", r.deviceId(),
                "ipAddress", r.ipAddress(),
                "ipSubnet", Subnets.subnetOf(r.ipAddress()),
                "ipType", r.ipType().name(),
                "at", r.at().toEpochMilli());
        try (Session session = driver.session()) {
            // REFERRED edge is keyed by campaignId, so the same referrer→referee pair
            // can hold distinct edges across campaigns. SHARES_* stay campaign-agnostic.
            session.executeWriteWithoutResult(tx -> tx.run("""
                    MATCH (:ReferralCode {tenantId: $tenantId, campaignId: $campaignId, code: $referralCode})
                          -[:ISSUED_TO]->(referrer:Account)
                    MERGE (referee:Account {tenantId: $tenantId, userId: $refereeUserId})
                      ON CREATE SET referee.createdAt = $at
                    SET referee.deviceId = $deviceId,
                        referee.ipAddress = $ipAddress,
                        referee.ipSubnet = $ipSubnet,
                        referee.ipType = $ipType,
                        referee.ipReputationCheckedAt = $at
                    MERGE (referrer)-[ref:REFERRED {campaignId: $campaignId}]->(referee)
                      ON CREATE SET ref.createdAt = $at, ref.converted = false
                    WITH referee
                    CALL {
                        WITH referee
                        MATCH (other:Account {tenantId: $tenantId, deviceId: $deviceId})
                        WHERE other.userId <> referee.userId AND $deviceId IS NOT NULL
                        MERGE (referee)-[:SHARES_DEVICE]-(other)
                    }
                    CALL {
                        WITH referee
                        MATCH (other:Account {tenantId: $tenantId, ipSubnet: $ipSubnet})
                        WHERE other.userId <> referee.userId AND $ipSubnet IS NOT NULL
                        MERGE (referee)-[:SHARES_IP_SUBNET]-(other)
                    }
                    """, params).consume());
        }
    }

    @Override
    public void recordConversion(ConversionRecord c) {
        Map<String, Object> params = params(
                "tenantId", c.tenantId().value(),
                "campaignId", c.campaignId().value(),
                "referralCode", c.referralCode().value(),
                "refereeUserId", c.refereeUserId(),
                "conversionType", c.conversionType(),
                "at", c.at().toEpochMilli());
        try (Session session = driver.session()) {
            session.executeWriteWithoutResult(tx -> tx.run("""
                    MATCH (:ReferralCode {tenantId: $tenantId, campaignId: $campaignId, code: $referralCode})
                          -[:ISSUED_TO]->(referrer:Account)
                          -[ref:REFERRED {campaignId: $campaignId}]->
                          (referee:Account {tenantId: $tenantId, userId: $refereeUserId})
                    SET ref.converted = true,
                        ref.conversionType = $conversionType,
                        ref.convertedAt = $at,
                        referee.converted = true
                    """, params).consume());
        }
    }

    @Override
    public com.turing.vigilant.graph.ReferralNeighbourhood loadNeighbourhood(
            TenantId tenantId, ReferralCode referralCode, CampaignId campaignId) {
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Map<String, Object> seed = Map.of(
                        "tenantId", tenantId.value(),
                        "referralCode", referralCode.value(),
                        "campaignId", campaignId.value());
                // REFERRED traversal scoped to this campaign; the referrer is found
                // by an index seek on the :ReferralCode node.
                var idsRow = tx.run("""
                        MATCH (:ReferralCode {tenantId: $tenantId, campaignId: $campaignId, code: $referralCode})
                              -[:ISSUED_TO]->(r:Account)
                        OPTIONAL MATCH (r)-[:REFERRED* {campaignId: $campaignId}]-(m:Account {tenantId: $tenantId})
                        RETURN r.userId AS referrerUserId, collect(DISTINCT m.userId) AS others
                        """, seed).list();

                if (idsRow.isEmpty() || idsRow.get(0).get("referrerUserId").isNull()) {
                    return new com.turing.vigilant.graph.ReferralNeighbourhood(
                            tenantId, referralCode, campaignId, null, List.of(), List.of(), List.of());
                }

                String referrerUserId = idsRow.get(0).get("referrerUserId").asString();
                List<Object> otherIds = idsRow.get(0).get("others").asList();
                List<String> ids = new ArrayList<>();
                ids.add(referrerUserId);
                otherIds.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(Object::toString)
                        .forEach(ids::add);

                Map<String, Object> referralIdParams = Map.of(
                        "tenantId", tenantId.value(), "ids", ids, "campaignId", campaignId.value());

                // Expand the campaign-scoped referral component by one shared-attribute
                // hop. This keeps referral traversal scoped to the requested campaign,
                // while making a device/IP neighbour from another campaign visible to
                // collision scoring as required by spec section 10a.
                tx.run("""
                        MATCH (a:Account {tenantId: $tenantId})
                              -[:SHARES_DEVICE|SHARES_IP_SUBNET]-
                              (b:Account {tenantId: $tenantId})
                        WHERE a.userId IN $ids
                        RETURN DISTINCT b.userId AS userId
                        """, referralIdParams).list(row -> row.get("userId").asString()).stream()
                        .filter(id -> !ids.contains(id))
                        .forEach(ids::add);

                Map<String, Object> idParams = Map.of(
                        "tenantId", tenantId.value(), "ids", ids, "campaignId", campaignId.value());

                // Referral edges are campaign-scoped…
                List<ReferralEdge> referralEdges = tx.run("""
                        MATCH (a:Account {tenantId: $tenantId})-[e:REFERRED {campaignId: $campaignId}]->(b:Account {tenantId: $tenantId})
                        WHERE a.userId IN $ids AND b.userId IN $ids
                        RETURN a.userId AS src, b.userId AS dst, e.createdAt AS createdAt
                        """, idParams).list(row -> new ReferralEdge(
                                row.get("src").asString(),
                                row.get("dst").asString(),
                                Instant.ofEpochMilli(row.get("createdAt").asLong())));

                // …shared-attribute edges are NOT (cross-campaign overlap still counts).
                List<com.turing.vigilant.graph.SharedAttributeEdge> sharedEdges = tx.run("""
                        MATCH (a:Account {tenantId: $tenantId})-[e:SHARES_DEVICE|SHARES_IP_SUBNET]-(b:Account {tenantId: $tenantId})
                        WHERE a.userId IN $ids AND b.userId IN $ids AND a.userId < b.userId
                        RETURN a.userId AS ua, b.userId AS ub, type(e) AS t
                        """, idParams).list(row -> new com.turing.vigilant.graph.SharedAttributeEdge(
                                row.get("ua").asString(),
                                row.get("ub").asString(),
                                row.get("t").asString().equals("SHARES_DEVICE")
                                        ? com.turing.vigilant.graph.SharedAttributeType.DEVICE
                                        : com.turing.vigilant.graph.SharedAttributeType.IP_SUBNET));

                List<AccountNode> accounts = tx.run("""
                        MATCH (a:Account {tenantId: $tenantId})
                        WHERE a.userId IN $ids
                        RETURN a.userId AS userId, a.ipType AS ipType,
                               coalesce(a.converted, false) AS converted
                        """, idParams).list(row -> new AccountNode(
                                row.get("userId").asString(),
                                ipTypeOf(row.get("ipType")),
                                row.get("converted").asBoolean(false)));

                return new com.turing.vigilant.graph.ReferralNeighbourhood(
                        tenantId, referralCode, campaignId, referrerUserId, referralEdges, sharedEdges, accounts);
            });
        }
    }

    @Override
    public ReferralNeighbourhood loadCaseVisualization(
            TenantId tenantId, ReferralCode referralCode, CampaignId campaignId,
            Set<SharedAttributeType> includedSharedEdges) {
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                // Bounded, campaign-scoped traversal: the referrer plus up to
                // VIZ_MAX_NODES referees within VIZ_MAX_DEPTH REFERRED hops. Unlike
                // loadNeighbourhood there is no unbounded * and no shared-attribute
                // widening hop — the explorer draws the referral cluster, not the
                // full scoring neighbourhood. The depth is a validated int constant,
                // so string-formatting it into the pattern is injection-safe.
                var idsRow = tx.run("""
                        MATCH (:ReferralCode {tenantId: $tenantId, campaignId: $campaignId, code: $referralCode})
                              -[:ISSUED_TO]->(r:Account)
                        OPTIONAL MATCH (r)-[:REFERRED*1..%d {campaignId: $campaignId}]->(m:Account {tenantId: $tenantId})
                        WITH r, collect(DISTINCT m.userId)[0..$maxNodes] AS others
                        RETURN r.userId AS referrerUserId, others
                        """.formatted(VIZ_MAX_DEPTH), Map.of(
                                "tenantId", tenantId.value(),
                                "campaignId", campaignId.value(),
                                "referralCode", referralCode.value(),
                                "maxNodes", VIZ_MAX_NODES)).list();

                if (idsRow.isEmpty() || idsRow.get(0).get("referrerUserId").isNull()) {
                    return new ReferralNeighbourhood(
                            tenantId, referralCode, campaignId, null, List.of(), List.of(), List.of());
                }

                String referrerUserId = idsRow.get(0).get("referrerUserId").asString();
                List<String> ids = new ArrayList<>();
                ids.add(referrerUserId);
                idsRow.get(0).get("others").asList().stream()
                        .filter(java.util.Objects::nonNull)
                        .map(Object::toString)
                        .forEach(ids::add);

                Map<String, Object> idParams = Map.of(
                        "tenantId", tenantId.value(), "ids", ids, "campaignId", campaignId.value());

                List<ReferralEdge> referralEdges = tx.run("""
                        MATCH (a:Account {tenantId: $tenantId})-[e:REFERRED {campaignId: $campaignId}]->(b:Account {tenantId: $tenantId})
                        WHERE a.userId IN $ids AND b.userId IN $ids
                        RETURN a.userId AS src, b.userId AS dst, e.createdAt AS createdAt
                        """, idParams).list(row -> new ReferralEdge(
                                row.get("src").asString(),
                                row.get("dst").asString(),
                                Instant.ofEpochMilli(row.get("createdAt").asLong())));

                // Fetch overlap edges only for the collision types the case flagged;
                // a velocity/cycle/datacenter-only case skips this query entirely.
                List<SharedAttributeEdge> sharedEdges = List.of();
                if (!includedSharedEdges.isEmpty()) {
                    List<String> edgeTypes = includedSharedEdges.stream()
                            .map(t -> t == SharedAttributeType.DEVICE ? "SHARES_DEVICE" : "SHARES_IP_SUBNET")
                            .toList();
                    sharedEdges = tx.run("""
                            MATCH (a:Account {tenantId: $tenantId})-[e:SHARES_DEVICE|SHARES_IP_SUBNET]-(b:Account {tenantId: $tenantId})
                            WHERE a.userId IN $ids AND b.userId IN $ids AND a.userId < b.userId
                              AND type(e) IN $edgeTypes
                            RETURN a.userId AS ua, b.userId AS ub, type(e) AS t
                            """, Map.of(
                                    "tenantId", tenantId.value(), "ids", ids, "edgeTypes", edgeTypes))
                            .list(row -> new SharedAttributeEdge(
                                    row.get("ua").asString(),
                                    row.get("ub").asString(),
                                    row.get("t").asString().equals("SHARES_DEVICE")
                                            ? SharedAttributeType.DEVICE
                                            : SharedAttributeType.IP_SUBNET));
                }

                List<AccountNode> accounts = tx.run("""
                        MATCH (a:Account {tenantId: $tenantId})
                        WHERE a.userId IN $ids
                        RETURN a.userId AS userId, a.ipType AS ipType,
                               coalesce(a.converted, false) AS converted
                        """, idParams).list(row -> new AccountNode(
                                row.get("userId").asString(),
                                ipTypeOf(row.get("ipType")),
                                row.get("converted").asBoolean(false)));

                return new ReferralNeighbourhood(
                        tenantId, referralCode, campaignId, referrerUserId, referralEdges, sharedEdges, accounts);
            });
        }
    }

    @Override
    public FanoutBaseline fanoutBaseline(
            TenantId tenantId, CampaignId campaignId, Instant windowStart, Instant windowEnd) {
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                // Compare like with like: each population fan-out uses the same rolling
                // time window as VelocityBurstRule's observed fan-out, and only the
                // requested campaign contributes (spec sections 5 and 10a).
                var row = tx.run("""
                        MATCH (r:Account {tenantId: $tenantId})-[e:REFERRED {campaignId: $campaignId}]->()
                        WHERE e.createdAt >= $windowStart AND e.createdAt <= $windowEnd
                        WITH r, count(e) AS fanout
                        RETURN sum(fanout) AS total, sum(fanout * fanout) AS sumSq, count(r) AS n
                        """, Map.of(
                                "tenantId", tenantId.value(),
                                "campaignId", campaignId.value(),
                                "windowStart", windowStart.toEpochMilli(),
                                "windowEnd", windowEnd.toEpochMilli())).single();

                long n = row.get("n").asLong(0);
                if (n == 0) {
                    return FanoutBaseline.empty();
                }
                double total = row.get("total").asDouble(0.0);
                double sumSq = row.get("sumSq").asDouble(0.0);
                double mean = total / n;
                double variance = Math.max(0.0, (sumSq / n) - (mean * mean));
                return new FanoutBaseline(mean, Math.sqrt(variance), n);
            });
        }
    }

    @Override
    public Optional<String> findReferrerUserId(
            TenantId tenantId, CampaignId campaignId, ReferralCode referralCode) {
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                var rows = tx.run("""
                        MATCH (:ReferralCode {tenantId: $tenantId, campaignId: $campaignId, code: $referralCode})
                              -[:ISSUED_TO]->(r:Account)
                        RETURN r.userId AS userId LIMIT 1
                        """, Map.of(
                                "tenantId", tenantId.value(),
                                "campaignId", campaignId.value(),
                                "referralCode", referralCode.value()))
                        .list();
                return rows.isEmpty()
                        ? Optional.<String>empty()
                        : Optional.of(rows.get(0).get("userId").asString());
            });
        }
    }

    @Override
    public boolean referralExists(
            TenantId tenantId, CampaignId campaignId, ReferralCode referralCode, String refereeUserId) {
        return referralStateExists(tenantId, campaignId, referralCode, refereeUserId, false);
    }

    @Override
    public boolean convertedReferralExists(
            TenantId tenantId, CampaignId campaignId, ReferralCode referralCode, String refereeUserId) {
        return referralStateExists(tenantId, campaignId, referralCode, refereeUserId, true);
    }

    private boolean referralStateExists(
            TenantId tenantId, CampaignId campaignId, ReferralCode referralCode,
            String refereeUserId, boolean requireConversion) {
        try (Session session = driver.session()) {
            return session.executeRead(tx -> tx.run("""
                    MATCH (:ReferralCode {tenantId: $tenantId, campaignId: $campaignId, code: $referralCode})
                          -[:ISSUED_TO]->(:Account {tenantId: $tenantId})
                          -[r:REFERRED {campaignId: $campaignId}]->
                          (:Account {tenantId: $tenantId, userId: $refereeUserId})
                    WHERE NOT $requireConversion OR coalesce(r.converted, false)
                    RETURN count(r) > 0 AS exists
                    """, Map.of(
                            "tenantId", tenantId.value(),
                            "campaignId", campaignId.value(),
                            "referralCode", referralCode.value(),
                            "refereeUserId", refereeUserId,
                            "requireConversion", requireConversion))
                    .single().get("exists").asBoolean());
        }
    }

    @Override
    public boolean identityCollisionExists(TenantId tenantId, String userId, String deviceId, String ipAddress) {
        Map<String, Object> params = params(
                "tenantId", tenantId.value(),
                "userId", userId,
                "deviceId", deviceId,
                "ipSubnet", Subnets.subnetOf(ipAddress));
        try (Session session = driver.session()) {
            return session.executeRead(tx -> tx.run("""
                    MATCH (a:Account {tenantId: $tenantId})
                    WHERE a.userId <> $userId
                      AND ((a.deviceId IS NOT NULL AND a.deviceId = $deviceId)
                        OR (a.ipSubnet IS NOT NULL AND a.ipSubnet = $ipSubnet))
                    RETURN count(a) > 0 AS collides
                    """, params).single().get("collides").asBoolean());
        }
    }

    /** Reads a stored ipType property, defaulting absent/unrecognised values to UNKNOWN. */
    private static IpType ipTypeOf(Value value) {
        if (value == null || value.isNull()) {
            return IpType.UNKNOWN;
        }
        try {
            return IpType.valueOf(value.asString());
        } catch (IllegalArgumentException e) {
            return IpType.UNKNOWN;
        }
    }

    /** Builds a parameter map that, unlike {@link Map#of}, tolerates null values. */
    private static Map<String, Object> params(Object... keyValues) {
        Map<String, Object> map = new java.util.HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
