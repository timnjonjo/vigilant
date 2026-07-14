package com.turing.vigilant.graph;

import com.turing.vigilant.graph.GraphCommands.ConversionRecord;
import com.turing.vigilant.graph.GraphCommands.RedemptionRecord;
import com.turing.vigilant.graph.GraphCommands.ReferrerRegistration;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.IpType;
import com.turing.vigilant.scoring.RuleBasedScorer;
import com.turing.vigilant.scoring.ScoringRequest;
import com.turing.vigilant.scoring.ScoringWeights;
import com.turing.vigilant.shared.ReasonCode;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class Neo4jGraphStoreIT {

    @Container
    static final Neo4jContainer<?> NEO4J =
            new Neo4jContainer<>(DockerImageName.parse("neo4j:5.26")).withoutAuthentication();

    private static Driver driver;
    private Neo4jGraphStore store;

    private final TenantId loob = TenantId.of("loob-bank");
    private final ReferralCode code = ReferralCode.of("LOOB-R1");
    private final CampaignId campA = CampaignId.of("camp-A");
    private final CampaignId campB = CampaignId.of("camp-B");
    private final Instant t0 = Instant.parse("2026-07-01T10:00:00Z");

    @BeforeAll
    static void openDriver() {
        driver = GraphDatabase.driver(NEO4J.getBoltUrl(), AuthTokens.none());
    }

    @AfterAll
    static void closeDriver() {
        driver.close();
    }

    @BeforeEach
    void freshGraph() {
        try (Session session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n").consume();
        }
        store = new Neo4jGraphStore(driver);
        store.initialiseSchema();
    }

    @Test
    void materialisesReferralAndDeviceCollisionEdges() {
        store.registerReferrer(new ReferrerRegistration(loob, campA, "R", "dev-R", "10.0.0.1", code, t0));
        // Two referees redeem R's code sharing device "dev-shared".
        store.recordRedemption(new RedemptionRecord(loob, campA, code, "A", "dev-shared", "10.0.0.2", t0.plusSeconds(60)));
        store.recordRedemption(new RedemptionRecord(loob, campA, code, "B", "dev-shared", "10.0.0.3", t0.plusSeconds(120)));

        ReferralNeighbourhood n = store.loadNeighbourhood(loob, code, campA);

        assertThat(n.referrerUserId()).isEqualTo("R");
        assertThat(n.fanoutOfReferrer())
                .extracting(ReferralEdge::refereeUserId)
                .containsExactlyInAnyOrder("A", "B");
        assertThat(n.sharedEdges())
                .anySatisfy(edge -> {
                    assertThat(edge.type()).isEqualTo(SharedAttributeType.DEVICE);
                    assertThat(edge.userIdA()).isIn("A", "B");
                    assertThat(edge.userIdB()).isIn("A", "B");
                });
    }

    @Test
    void persistsIpTypeOnAccountsAndSurfacesItInTheNeighbourhood() {
        store.registerReferrer(new ReferrerRegistration(loob, campA, "R", "dev-R", "10.0.0.1", code, t0));
        // Referee A redeems from a datacenter IP; the classification is stored on the node.
        store.recordRedemption(new RedemptionRecord(
                loob, campA, code, "A", "dev-A", "203.0.113.7", t0.plusSeconds(60), IpType.DATACENTER));

        ReferralNeighbourhood n = store.loadNeighbourhood(loob, code, campA);

        assertThat(n.accounts())
                .contains(new AccountNode("A", IpType.DATACENTER))
                .contains(new AccountNode("R", IpType.UNKNOWN));
    }

    @Test
    void recordsConversionOnTheCampaignReferredEdge() {
        store.registerReferrer(new ReferrerRegistration(loob, campA, "R", "dev-R", "10.0.0.1", code, t0));
        store.recordRedemption(new RedemptionRecord(loob, campA, code, "A", "dev-A", "10.0.0.2", t0.plusSeconds(60)));

        store.recordConversion(new ConversionRecord(loob, campA, code, "A", "DEPOSIT", t0.plusSeconds(3600)));

        try (Session session = driver.session()) {
            boolean converted = session.run("""
                    MATCH (:ReferralCode {tenantId:'loob-bank', code:'LOOB-R1'})
                          -[:ISSUED_TO]->(a:Account {tenantId:'loob-bank'})
                          -[ref:REFERRED {campaignId:'camp-A'}]->(:Account {tenantId:'loob-bank', userId:'A'})
                    RETURN ref.converted AS converted
                    """).single().get("converted").asBoolean();
            assertThat(converted).isTrue();
        }
    }

    @Test
    void computesFanoutBaselineOverCampaignReferrersOnly() {
        // R1 refers A,B in camp-A (fan-out 2); R2 refers C in camp-A (fan-out 1).
        // Referees are not referrers and must not count towards the baseline.
        store.registerReferrer(new ReferrerRegistration(loob, campA, "R1", "d1", "10.0.0.1", code, t0));
        store.registerReferrer(new ReferrerRegistration(loob, campA, "R2", "d2", "10.0.1.1", ReferralCode.of("LOOB-R2"), t0));
        store.recordRedemption(new RedemptionRecord(loob, campA, code, "A", "da", "10.0.0.2", t0.plusSeconds(60)));
        store.recordRedemption(new RedemptionRecord(loob, campA, code, "B", "db", "10.0.0.3", t0.plusSeconds(60)));
        store.recordRedemption(new RedemptionRecord(loob, campA, ReferralCode.of("LOOB-R2"), "C", "dc", "10.0.2.2", t0.plusSeconds(60)));

        FanoutBaseline baseline = store.fanoutBaseline(
                loob, campA, t0.minusSeconds(1), t0.plusSeconds(3600));

        // {R1:2, R2:1} -> mean 1.5, variance (4+1)/2 - 1.5^2 = 0.25, stddev 0.5.
        assertThat(baseline.sampleSize()).isEqualTo(2);
        assertThat(baseline.mean()).isEqualTo(1.5);
        assertThat(baseline.standardDeviation()).isEqualTo(0.5);
    }

    @Test
    void fanoutBaselineUsesTheSameRollingWindowAsVelocityScoring() {
        ReferralCode oldCode = ReferralCode.of("LOOB-OLD");
        ReferralCode recentR1 = ReferralCode.of("LOOB-RECENT-1");
        ReferralCode recentR2 = ReferralCode.of("LOOB-RECENT-2");
        store.registerReferrer(new ReferrerRegistration(
                loob, campA, "old-ref", "d-old", "10.9.0.1", oldCode, t0.minusSeconds(172800)));
        for (int i = 0; i < 10; i++) {
            store.recordRedemption(new RedemptionRecord(
                    loob, campA, oldCode, "old-" + i, "do-" + i,
                    "10.9." + (10 + i) + ".2", t0.minusSeconds(172800 - i)));
        }
        store.registerReferrer(new ReferrerRegistration(loob, campA, "recent-1", "d-r1", "10.10.0.1", recentR1, t0));
        store.registerReferrer(new ReferrerRegistration(loob, campA, "recent-2", "d-r2", "10.11.0.1", recentR2, t0));
        store.recordRedemption(new RedemptionRecord(loob, campA, recentR1, "new-a", "dna", "10.12.0.1", t0));
        store.recordRedemption(new RedemptionRecord(loob, campA, recentR1, "new-b", "dnb", "10.13.0.1", t0));
        store.recordRedemption(new RedemptionRecord(loob, campA, recentR2, "new-c", "dnc", "10.14.0.1", t0));

        FanoutBaseline baseline = store.fanoutBaseline(
                loob, campA, t0.minusSeconds(3600), t0.plusSeconds(3600));

        assertThat(baseline.sampleSize()).isEqualTo(2);
        assertThat(baseline.mean()).isEqualTo(1.5);
        assertThat(baseline.standardDeviation()).isEqualTo(0.5);
    }

    @Test
    void perCampaignReferralActivityIsIsolatedForTheSameReferrer() {
        // One honest referrer active across two campaigns: fan-out must NOT blend.
        store.registerReferrer(new ReferrerRegistration(loob, campA, "R", "dev-R", "10.0.0.1", code, t0));
        store.registerReferrer(new ReferrerRegistration(loob, campB, "R", "dev-R", "10.0.0.1", ReferralCode.of("LOOB-R-B"), t0.plusSeconds(1)));
        // camp-A: R refers A, B (fan-out 2).
        store.recordRedemption(new RedemptionRecord(loob, campA, code, "A", "da", "10.0.0.2", t0.plusSeconds(60)));
        store.recordRedemption(new RedemptionRecord(loob, campA, code, "B", "db", "10.0.0.3", t0.plusSeconds(90)));
        // camp-B: R refers C (fan-out 1) via a different code.
        store.recordRedemption(new RedemptionRecord(loob, campB, ReferralCode.of("LOOB-R-B"), "C", "dc", "10.0.0.4", t0.plusSeconds(120)));

        ReferralNeighbourhood a = store.loadNeighbourhood(loob, code, campA);
        ReferralNeighbourhood b = store.loadNeighbourhood(loob, ReferralCode.of("LOOB-R-B"), campB);

        assertThat(a.fanoutOfReferrer()).extracting(ReferralEdge::refereeUserId)
                .containsExactlyInAnyOrder("A", "B");           // not A, B, C
        assertThat(b.fanoutOfReferrer()).extracting(ReferralEdge::refereeUserId)
                .containsExactly("C");
        assertThat(store.fanoutBaseline(loob, campA, t0, t0.plusSeconds(3600)).mean())
                .isEqualTo(2.0); // R:2 in camp-A
        assertThat(store.fanoutBaseline(loob, campB, t0, t0.plusSeconds(3600)).mean())
                .isEqualTo(1.0); // R:1 in camp-B
    }

    @Test
    void honestFanoutAcrossCampaignsDoesNotCombineIntoVelocityBurst() {
        ReferralCode codeA = ReferralCode.of("LOOB-HONEST-A");
        ReferralCode codeB = ReferralCode.of("LOOB-HONEST-B");
        store.registerReferrer(new ReferrerRegistration(loob, campA, "honest-r", "honest-device", "10.20.0.1", codeA, t0));
        store.registerReferrer(new ReferrerRegistration(loob, campB, "honest-r", "honest-device", "10.20.0.1", codeB, t0));
        for (int i = 0; i < 12; i++) {
            store.recordRedemption(new RedemptionRecord(
                    loob, campA, codeA, "a-" + i, "a-device-" + i,
                    "10." + (30 + i) + ".0.1", t0.plusSeconds(i)));
            store.recordRedemption(new RedemptionRecord(
                    loob, campB, codeB, "b-" + i, "b-device-" + i,
                    "10." + (60 + i) + ".0.1", t0.plusSeconds(i)));
        }
        ScoringWeights weights = ScoringWeights.defaults();
        RuleBasedScorer scorer = new RuleBasedScorer(weights);

        var scoreA = scorer.score(new ScoringRequest(
                store.loadNeighbourhood(loob, codeA, campA),
                store.fanoutBaseline(loob, campA, t0.minus(weights.velocityWindow()), t0.plusSeconds(60)),
                t0.plusSeconds(60)));
        var scoreB = scorer.score(new ScoringRequest(
                store.loadNeighbourhood(loob, codeB, campB),
                store.fanoutBaseline(loob, campB, t0.minus(weights.velocityWindow()), t0.plusSeconds(60)),
                t0.plusSeconds(60)));

        assertThat(scoreA.reasonCodes()).doesNotContain(ReasonCode.VELOCITY_BURST);
        assertThat(scoreB.reasonCodes()).doesNotContain(ReasonCode.VELOCITY_BURST);
        assertThat(store.loadNeighbourhood(loob, codeA, campA).fanoutOfReferrer()).hasSize(12);
        assertThat(store.loadNeighbourhood(loob, codeB, campB).fanoutOfReferrer()).hasSize(12);
    }

    @Test
    void deviceAndIpOverlapAcrossCampaignsReachTheScorer() {
        // Shared identity edges remain campaign-agnostic even though REFERRED
        // traversal and the velocity baseline stay campaign-scoped.
        store.registerReferrer(new ReferrerRegistration(loob, campA, "R", "dev-R", "10.1.0.1", code, t0));
        store.registerReferrer(new ReferrerRegistration(loob, campB, "R", "dev-R", "10.1.0.1", ReferralCode.of("LOOB-R-B"), t0.plusSeconds(1)));
        store.recordRedemption(new RedemptionRecord(loob, campA, code, "A", "shared-dev", "10.0.0.2", t0.plusSeconds(60)));
        store.recordRedemption(new RedemptionRecord(loob, campB, ReferralCode.of("LOOB-R-B"), "B", "shared-dev", "10.0.0.9", t0.plusSeconds(90)));

        ReferralNeighbourhood campaignA = store.loadNeighbourhood(loob, code, campA);
        var score = new RuleBasedScorer(ScoringWeights.defaults()).score(new ScoringRequest(
                campaignA,
                store.fanoutBaseline(loob, campA, t0, t0.plusSeconds(120)),
                t0.plusSeconds(120)));

        assertThat(campaignA.referralEdges()).allSatisfy(edge ->
                assertThat(edge.refereeUserId()).isNotEqualTo("B"));
        assertThat(campaignA.sharedEdges())
                .contains(new SharedAttributeEdge("A", "B", SharedAttributeType.DEVICE))
                .contains(new SharedAttributeEdge("A", "B", SharedAttributeType.IP_SUBNET));
        assertThat(score.reasonCodes()).containsExactly(
                ReasonCode.DEVICE_COLLISION, ReasonCode.IP_SUBNET_COLLISION);
        assertThat(score.value()).isEqualTo(0.75);
    }

    @Test
    void scopesEverythingByTenant() {
        TenantId other = TenantId.of("other-bank");
        store.registerReferrer(new ReferrerRegistration(loob, campA, "R", "dev-R", "10.0.0.1", code, t0));
        // Same code string, different tenant, must not leak.
        store.registerReferrer(new ReferrerRegistration(other, campA, "X", "dev-X", "10.0.0.1", code, t0));
        store.recordRedemption(new RedemptionRecord(other, campA, code, "Y", "dev-Y", "10.0.0.9", t0.plusSeconds(60)));

        ReferralNeighbourhood n = store.loadNeighbourhood(loob, code, campA);

        assertThat(n.referrerUserId()).isEqualTo("R");
        assertThat(n.referralEdges()).isEmpty();
        assertThat(store.findReferrerUserId(loob, campA, code)).contains("R");
        assertThat(store.findReferrerUserId(other, campA, code)).contains("X");
    }

    @Test
    void bindsAReferralCodeToItsIssuanceCampaign() {
        store.registerReferrer(new ReferrerRegistration(
                loob, campA, "R", "dev-R", "10.0.0.1", code, t0));

        assertThat(store.referralCodeExists(loob, campA, code)).isTrue();
        assertThat(store.referralCodeExists(loob, campB, code)).isFalse();

        // The graph write also refuses a cross-campaign use even if a caller
        // bypassed the service-layer guard.
        store.recordRedemption(new RedemptionRecord(
                loob, campB, code, "wrong-campaign-referee", "dev-X", "10.0.0.2", t0.plusSeconds(30)));
        assertThat(store.referralExists(loob, campB, code, "wrong-campaign-referee")).isFalse();

        store.recordRedemption(new RedemptionRecord(
                loob, campA, code, "A", "dev-A", "10.0.0.3", t0.plusSeconds(60)));
        assertThat(store.referralExists(loob, campA, code, "A")).isTrue();
        assertThat(store.convertedReferralExists(loob, campA, code, "A")).isFalse();

        store.recordConversion(new ConversionRecord(
                loob, campA, code, "A", "DEPOSIT", t0.plusSeconds(120)));
        assertThat(store.convertedReferralExists(loob, campA, code, "A")).isTrue();
        assertThat(store.convertedReferralExists(loob, campA, code, "someone-else")).isFalse();
        assertThat(store.convertedReferralExists(loob, campB, code, "A")).isFalse();
    }

    @Test
    void returnsEmptyNeighbourhoodForUnknownCode() {
        ReferralNeighbourhood n = store.loadNeighbourhood(loob, ReferralCode.of("NOPE"), campA);

        assertThat(n.referrerUserId()).isNull();
        assertThat(n.referralEdges()).isEmpty();
        assertThat(n.sharedEdges()).isEmpty();
    }

    // --- Case-detail visualization: bounded, reason-code-driven edge selection ---

    /** A ring where A and B share BOTH a device and an IP subnet, so the graph
     *  holds SHARES_DEVICE and SHARES_IP_SUBNET edges between them. */
    private void seedDeviceAndSubnetRing() {
        store.registerReferrer(new ReferrerRegistration(loob, campA, "R", "dev-R", "10.0.0.1", code, t0));
        store.recordRedemption(new RedemptionRecord(loob, campA, code, "A", "dev-shared", "10.0.0.2", t0.plusSeconds(60)));
        store.recordRedemption(new RedemptionRecord(loob, campA, code, "B", "dev-shared", "10.0.0.3", t0.plusSeconds(120)));
    }

    @Test
    void visualizationFetchesOnlyDeviceEdgesForADeviceCollisionCase() {
        seedDeviceAndSubnetRing();

        ReferralNeighbourhood n = store.loadCaseVisualization(
                loob, code, campA, Set.of(SharedAttributeType.DEVICE));

        assertThat(n.referrerUserId()).isEqualTo("R");
        assertThat(n.fanoutOfReferrer()).extracting(ReferralEdge::refereeUserId)
                .containsExactlyInAnyOrder("A", "B");
        assertThat(n.sharedEdges()).extracting(SharedAttributeEdge::type)
                .containsOnly(SharedAttributeType.DEVICE);
        assertThat(n.sharedEdges()).doesNotContain(
                new SharedAttributeEdge("A", "B", SharedAttributeType.IP_SUBNET));
    }

    @Test
    void visualizationFetchesOnlySubnetEdgesForAnIpCollisionCase() {
        seedDeviceAndSubnetRing();

        ReferralNeighbourhood n = store.loadCaseVisualization(
                loob, code, campA, Set.of(SharedAttributeType.IP_SUBNET));

        assertThat(n.sharedEdges()).extracting(SharedAttributeEdge::type)
                .containsOnly(SharedAttributeType.IP_SUBNET);
        assertThat(n.sharedEdges()).doesNotContain(
                new SharedAttributeEdge("A", "B", SharedAttributeType.DEVICE));
    }

    @Test
    void visualizationDrawsNoOverlapEdgesForAVelocityOrCycleOnlyCase() {
        seedDeviceAndSubnetRing();

        // A velocity/cycle/datacenter-only case selects no overlap edge types.
        ReferralNeighbourhood n = store.loadCaseVisualization(loob, code, campA, Set.of());

        assertThat(n.referrerUserId()).isEqualTo("R");
        assertThat(n.fanoutOfReferrer()).hasSize(2);          // referral cluster still shown
        assertThat(n.sharedEdges()).isEmpty();                // but no device/IP edges pulled
    }

    @Test
    void visualizationReturnsEmptyForUnknownCode() {
        ReferralNeighbourhood n = store.loadCaseVisualization(
                loob, ReferralCode.of("NOPE"), campA, Set.of(SharedAttributeType.DEVICE));

        assertThat(n.referrerUserId()).isNull();
        assertThat(n.referralEdges()).isEmpty();
        assertThat(n.sharedEdges()).isEmpty();
    }
}
