package com.turing.vigilant.graph;

import com.turing.vigilant.graph.GraphCommands.ConversionRecord;
import com.turing.vigilant.graph.GraphCommands.RedemptionRecord;
import com.turing.vigilant.graph.GraphCommands.ReferrerRegistration;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.IpType;
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
        store.registerReferrer(new ReferrerRegistration(loob, "R", "dev-R", "10.0.0.1", code, t0));
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
        store.registerReferrer(new ReferrerRegistration(loob, "R", "dev-R", "10.0.0.1", code, t0));
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
        store.registerReferrer(new ReferrerRegistration(loob, "R", "dev-R", "10.0.0.1", code, t0));
        store.recordRedemption(new RedemptionRecord(loob, campA, code, "A", "dev-A", "10.0.0.2", t0.plusSeconds(60)));

        store.recordConversion(new ConversionRecord(loob, campA, code, "A", "DEPOSIT", t0.plusSeconds(3600)));

        try (Session session = driver.session()) {
            boolean converted = session.run("""
                    MATCH (a:Account {tenantId:'loob-bank'})
                          -[ref:REFERRED {campaignId:'camp-A'}]->(:Account {tenantId:'loob-bank', userId:'A'})
                    WHERE 'LOOB-R1' IN a.referralCodes
                    RETURN ref.converted AS converted
                    """).single().get("converted").asBoolean();
            assertThat(converted).isTrue();
        }
    }

    @Test
    void computesFanoutBaselineOverCampaignReferrersOnly() {
        // R1 refers A,B in camp-A (fan-out 2); R2 refers C in camp-A (fan-out 1).
        // Referees are not referrers and must not count towards the baseline.
        store.registerReferrer(new ReferrerRegistration(loob, "R1", "d1", "10.0.0.1", code, t0));
        store.registerReferrer(new ReferrerRegistration(loob, "R2", "d2", "10.0.1.1", ReferralCode.of("LOOB-R2"), t0));
        store.recordRedemption(new RedemptionRecord(loob, campA, code, "A", "da", "10.0.0.2", t0.plusSeconds(60)));
        store.recordRedemption(new RedemptionRecord(loob, campA, code, "B", "db", "10.0.0.3", t0.plusSeconds(60)));
        store.recordRedemption(new RedemptionRecord(loob, campA, ReferralCode.of("LOOB-R2"), "C", "dc", "10.0.2.2", t0.plusSeconds(60)));

        FanoutBaseline baseline = store.fanoutBaseline(loob, campA);

        // {R1:2, R2:1} -> mean 1.5, variance (4+1)/2 - 1.5^2 = 0.25, stddev 0.5.
        assertThat(baseline.sampleSize()).isEqualTo(2);
        assertThat(baseline.mean()).isEqualTo(1.5);
        assertThat(baseline.standardDeviation()).isEqualTo(0.5);
    }

    @Test
    void perCampaignReferralActivityIsIsolatedForTheSameReferrer() {
        // One honest referrer active across two campaigns: fan-out must NOT blend.
        store.registerReferrer(new ReferrerRegistration(loob, "R", "dev-R", "10.0.0.1", code, t0));
        store.registerReferrer(new ReferrerRegistration(loob, "R", "dev-R", "10.0.0.1", ReferralCode.of("LOOB-R-B"), t0.plusSeconds(1)));
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
        assertThat(store.fanoutBaseline(loob, campA).mean()).isEqualTo(2.0); // R:2 in camp-A
        assertThat(store.fanoutBaseline(loob, campB).mean()).isEqualTo(1.0); // R:1 in camp-B
    }

    @Test
    void deviceOverlapIsCampaignAgnostic() {
        // A shared device across two campaigns still produces a SHARES_DEVICE edge.
        store.registerReferrer(new ReferrerRegistration(loob, "R", "dev-R", "10.0.0.1", code, t0));
        store.registerReferrer(new ReferrerRegistration(loob, "R", "dev-R", "10.0.0.1", ReferralCode.of("LOOB-R-B"), t0.plusSeconds(1)));
        store.recordRedemption(new RedemptionRecord(loob, campA, code, "A", "shared-dev", "10.0.0.2", t0.plusSeconds(60)));
        store.recordRedemption(new RedemptionRecord(loob, campB, ReferralCode.of("LOOB-R-B"), "B", "shared-dev", "10.0.9.9", t0.plusSeconds(90)));

        try (Session session = driver.session()) {
            long shares = session.run("""
                    MATCH (:Account {userId:'A'})-[e:SHARES_DEVICE]-(:Account {userId:'B'})
                    RETURN count(e) AS c
                    """).single().get("c").asLong();
            assertThat(shares).isEqualTo(1);
        }
    }

    @Test
    void scopesEverythingByTenant() {
        TenantId other = TenantId.of("other-bank");
        store.registerReferrer(new ReferrerRegistration(loob, "R", "dev-R", "10.0.0.1", code, t0));
        // Same code string, different tenant, must not leak.
        store.registerReferrer(new ReferrerRegistration(other, "X", "dev-X", "10.0.0.1", code, t0));
        store.recordRedemption(new RedemptionRecord(other, campA, code, "Y", "dev-Y", "10.0.0.9", t0.plusSeconds(60)));

        ReferralNeighbourhood n = store.loadNeighbourhood(loob, code, campA);

        assertThat(n.referrerUserId()).isEqualTo("R");
        assertThat(n.referralEdges()).isEmpty();
        assertThat(store.findReferrerUserId(loob, code)).contains("R");
        assertThat(store.findReferrerUserId(other, code)).contains("X");
    }

    @Test
    void returnsEmptyNeighbourhoodForUnknownCode() {
        ReferralNeighbourhood n = store.loadNeighbourhood(loob, ReferralCode.of("NOPE"), campA);

        assertThat(n.referrerUserId()).isNull();
        assertThat(n.referralEdges()).isEmpty();
        assertThat(n.sharedEdges()).isEmpty();
    }
}
