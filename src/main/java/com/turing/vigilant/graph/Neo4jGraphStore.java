package com.turing.vigilant.graph;

import com.turing.vigilant.graph.GraphCommands.ConversionRecord;
import com.turing.vigilant.graph.GraphCommands.RedemptionRecord;
import com.turing.vigilant.graph.GraphCommands.ReferrerRegistration;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.IpType;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;
import jakarta.annotation.PostConstruct;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Neo4j-backed {@link GraphStore}. Cypher is written by hand for full control of
 * graph traversal, and every statement is scoped by {@code tenantId}. Timestamps
 * are stored as epoch-millis longs to avoid temporal-type mapping ambiguity.
 *
 * <p>Campaign model (spec §10a): {@code campaignId} lives on the {@code REFERRED}
 * edge, not the node, so a user can participate in multiple campaigns. A referrer
 * node therefore holds a <em>list</em> of {@code referralCodes} (one per code it
 * has issued across campaigns), matched by membership. {@code SHARES_DEVICE} /
 * {@code SHARES_IP_SUBNET} stay campaign-agnostic — cross-campaign identity
 * overlap is a valid, stronger signal.
 */
@Component
public class Neo4jGraphStore implements GraphStore {

    private final Driver driver;

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
        }
    }

    @Override
    public void registerReferrer(ReferrerRegistration reg) {
        Map<String, Object> params = params(
                "tenantId", reg.tenantId().value(),
                "userId", reg.userId(),
                "deviceId", reg.deviceId(),
                "ipAddress", reg.ipAddress(),
                "ipSubnet", Subnets.subnetOf(reg.ipAddress()),
                "referralCode", reg.referralCode().value(),
                "ipType", reg.ipType().name(),
                "at", reg.at().toEpochMilli());
        try (Session session = driver.session()) {
            // Append the code to the referrer's list (a user may issue codes across
            // campaigns); never duplicate on re-registration of the same code.
            session.executeWriteWithoutResult(tx -> tx.run("""
                    MERGE (a:Account {tenantId: $tenantId, userId: $userId})
                      ON CREATE SET a.createdAt = $at, a.referralCodes = [$referralCode]
                      ON MATCH SET a.referralCodes =
                          CASE WHEN $referralCode IN coalesce(a.referralCodes, [])
                               THEN a.referralCodes
                               ELSE coalesce(a.referralCodes, []) + $referralCode END
                    SET a.deviceId = $deviceId,
                        a.ipAddress = $ipAddress,
                        a.ipSubnet = $ipSubnet,
                        a.ipType = $ipType,
                        a.ipReputationCheckedAt = $at
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
                    MATCH (referrer:Account {tenantId: $tenantId})
                    WHERE $referralCode IN referrer.referralCodes
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
                    MATCH (referrer:Account {tenantId: $tenantId})
                          -[ref:REFERRED {campaignId: $campaignId}]->
                          (referee:Account {tenantId: $tenantId, userId: $refereeUserId})
                    WHERE $referralCode IN referrer.referralCodes
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
                // REFERRED traversal scoped to this campaign; the referrer is matched
                // by code membership.
                var idsRow = tx.run("""
                        MATCH (r:Account {tenantId: $tenantId})
                        WHERE $referralCode IN r.referralCodes
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
    public FanoutBaseline fanoutBaseline(TenantId tenantId, CampaignId campaignId) {
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                // Baseline over referrers active in THIS campaign only (spec §10a).
                var row = tx.run("""
                        MATCH (r:Account {tenantId: $tenantId})-[e:REFERRED {campaignId: $campaignId}]->()
                        WITH r, count(e) AS fanout
                        RETURN sum(fanout) AS total, sum(fanout * fanout) AS sumSq, count(r) AS n
                        """, Map.of("tenantId", tenantId.value(), "campaignId", campaignId.value())).single();

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
    public Optional<String> findReferrerUserId(TenantId tenantId, ReferralCode referralCode) {
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                var rows = tx.run("""
                        MATCH (r:Account {tenantId: $tenantId})
                        WHERE $referralCode IN r.referralCodes
                        RETURN r.userId AS userId LIMIT 1
                        """, Map.of("tenantId", tenantId.value(), "referralCode", referralCode.value()))
                        .list();
                return rows.isEmpty()
                        ? Optional.<String>empty()
                        : Optional.of(rows.get(0).get("userId").asString());
            });
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
