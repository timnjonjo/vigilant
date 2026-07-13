package com.turing.vigilant.scoring;

import com.turing.vigilant.graph.AccountNode;
import com.turing.vigilant.graph.FanoutBaseline;
import com.turing.vigilant.graph.ReferralEdge;
import com.turing.vigilant.graph.ReferralNeighbourhood;
import com.turing.vigilant.graph.SharedAttributeEdge;
import com.turing.vigilant.graph.SharedAttributeType;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.IpType;
import com.turing.vigilant.shared.ReasonCode;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedScorerTest {

    private static final TenantId TENANT = TenantId.of("loob-bank");
    private static final ReferralCode CODE = ReferralCode.of("LOOB-R1");
    private static final CampaignId CAMPAIGN = CampaignId.of("camp-1");
    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");

    private final ScoringWeights weights = ScoringWeights.defaults();
    private final RuleBasedScorer scorer = new RuleBasedScorer(weights);

    @Test
    void cleanReferralsScoreZeroWithNoReasons() {
        ReferralNeighbourhood clean = neighbourhood(
                List.of(referral("R", "A"), referral("R", "B")),
                List.of());

        RiskScore score = scorer.score(new ScoringRequest(clean, baseline(2.0, 1.0), NOW));

        assertThat(score.value()).isZero();
        assertThat(score.reasonCodes()).isEmpty();
    }

    @Test
    void deviceCollisionRaisesDeviceReason() {
        ReferralNeighbourhood shared = neighbourhood(
                List.of(referral("R", "A"), referral("R", "B")),
                List.of(new SharedAttributeEdge("A", "B", SharedAttributeType.DEVICE)));

        RiskScore score = scorer.score(new ScoringRequest(shared, baseline(2.0, 1.0), NOW));

        assertThat(score.reasonCodes()).containsExactly(ReasonCode.DEVICE_COLLISION);
        assertThat(score.value()).isEqualTo(weights.deviceWeight());
    }

    @Test
    void ipSubnetCollisionRaisesIpReason() {
        ReferralNeighbourhood shared = neighbourhood(
                List.of(referral("R", "A")),
                List.of(new SharedAttributeEdge("A", "R", SharedAttributeType.IP_SUBNET)));

        RiskScore score = scorer.score(new ScoringRequest(shared, baseline(2.0, 1.0), NOW));

        assertThat(score.reasonCodes()).containsExactly(ReasonCode.IP_SUBNET_COLLISION);
    }

    @Test
    void referralCycleRaisesCycleReason() {
        // A -> B and B -> A form a self-referral loop.
        ReferralNeighbourhood ring = neighbourhood(
                List.of(referral("A", "B"), referral("B", "A")),
                List.of());

        RiskScore score = scorer.score(new ScoringRequest(ring, baseline(2.0, 1.0), NOW));

        assertThat(score.reasonCodes()).contains(ReasonCode.CYCLE_DETECTED);
    }

    @Test
    void fanoutBurstAboveZThresholdRaisesVelocityReason() {
        // Referrer R issues 10 referrals in the window; baseline mean 2, stddev 1 -> z = 8.
        List<ReferralEdge> burst = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            burst.add(new ReferralEdge("R", "ref-" + i, NOW.minus(Duration.ofMinutes(30))));
        }
        ReferralNeighbourhood n = neighbourhood(burst, List.of());

        RiskScore score = scorer.score(new ScoringRequest(n, baseline(2.0, 1.0), NOW));

        assertThat(score.reasonCodes()).contains(ReasonCode.VELOCITY_BURST);
    }

    @Test
    void staleReferralsOutsideWindowDoNotCountAsBurst() {
        List<ReferralEdge> old = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            old.add(new ReferralEdge("R", "ref-" + i, NOW.minus(Duration.ofDays(30))));
        }
        ReferralNeighbourhood n = neighbourhood(old, List.of());

        RiskScore score = scorer.score(new ScoringRequest(n, baseline(2.0, 1.0), NOW));

        assertThat(score.reasonCodes()).doesNotContain(ReasonCode.VELOCITY_BURST);
    }

    @Test
    void datacenterAccountRaisesDatacenterReasonInTheHoldBand() {
        ReferralNeighbourhood n = new ReferralNeighbourhood(
                TENANT, CODE, CAMPAIGN, "R",
                List.of(referral("R", "A")),
                List.of(),
                List.of(new AccountNode("R", IpType.RESIDENTIAL),
                        new AccountNode("A", IpType.DATACENTER)));

        RiskScore score = scorer.score(new ScoringRequest(n, baseline(2.0, 1.0), NOW));

        assertThat(score.reasonCodes()).containsExactly(ReasonCode.DATACENTER_OR_VPN_IP);
        assertThat(score.value()).isEqualTo(weights.datacenterWeight());
    }

    @Test
    void residentialAndMobileAccountsDoNotRaiseDatacenterReason() {
        ReferralNeighbourhood n = neighbourhood(
                List.of(referral("R", "A")),
                List.of(),
                List.of(new AccountNode("R", IpType.MOBILE),
                        new AccountNode("A", IpType.RESIDENTIAL)));

        RiskScore score = scorer.score(new ScoringRequest(n, baseline(2.0, 1.0), NOW));

        assertThat(score.reasonCodes()).doesNotContain(ReasonCode.DATACENTER_OR_VPN_IP);
    }

    @Test
    void combinedSignalsAccumulateAndClampAtOne() {
        List<ReferralEdge> burst = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            burst.add(new ReferralEdge("R", "ref-" + i, NOW.minus(Duration.ofMinutes(10))));
        }
        burst.add(referral("ref-0", "R")); // closes a cycle R -> ref-0 -> R
        ReferralNeighbourhood n = neighbourhood(burst, List.of(
                new SharedAttributeEdge("R", "ref-0", SharedAttributeType.DEVICE),
                new SharedAttributeEdge("R", "ref-1", SharedAttributeType.IP_SUBNET)));

        RiskScore score = scorer.score(new ScoringRequest(n, baseline(2.0, 1.0), NOW));

        assertThat(score.reasonCodes()).contains(
                ReasonCode.VELOCITY_BURST,
                ReasonCode.DEVICE_COLLISION,
                ReasonCode.IP_SUBNET_COLLISION,
                ReasonCode.CYCLE_DETECTED);
        assertThat(score.value()).isEqualTo(1.0); // clamped
    }

    private static ReferralEdge referral(String from, String to) {
        return new ReferralEdge(from, to, NOW.minus(Duration.ofMinutes(5)));
    }

    private static ReferralNeighbourhood neighbourhood(
            List<ReferralEdge> referralEdges, List<SharedAttributeEdge> sharedEdges) {
        return neighbourhood(referralEdges, sharedEdges, List.of());
    }

    private static ReferralNeighbourhood neighbourhood(
            List<ReferralEdge> referralEdges, List<SharedAttributeEdge> sharedEdges,
            List<AccountNode> accounts) {
        return new ReferralNeighbourhood(TENANT, CODE, CAMPAIGN, "R", referralEdges, sharedEdges, accounts);
    }

    private static FanoutBaseline baseline(double mean, double stddev) {
        return new FanoutBaseline(mean, stddev, 100);
    }
}
