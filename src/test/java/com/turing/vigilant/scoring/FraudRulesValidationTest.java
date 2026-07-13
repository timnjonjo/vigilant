package com.turing.vigilant.scoring;

import com.turing.vigilant.graph.AccountNode;
import com.turing.vigilant.graph.FanoutBaseline;
import com.turing.vigilant.graph.ReferralEdge;
import com.turing.vigilant.graph.ReferralNeighbourhood;
import com.turing.vigilant.graph.SharedAttributeEdge;
import com.turing.vigilant.graph.SharedAttributeType;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.Decision;
import com.turing.vigilant.shared.IpType;
import com.turing.vigilant.shared.ReasonCode;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.ScoreBands;
import com.turing.vigilant.shared.TenantId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Repeatable validation scenarios for the v1 fraud rules. Characterisation tests
 * deliberately retain evidence of ambiguous false positives: they assert what
 * the engine actually does, while the validation report records where that
 * differs from the desired product outcome.
 */
class FraudRulesValidationTest {

    private static final TenantId TENANT = TenantId.of("loob-bank");
    private static final CampaignId CAMPAIGN = CampaignId.of("q3-signup-boost");
    private static final ReferralCode CODE = ReferralCode.of("VALIDATE-001");
    private static final Instant NOW = Instant.parse("2026-07-13T09:00:00Z");
    private static final ScoringWeights WEIGHTS = ScoringWeights.defaults();
    private static final RuleBasedScorer SCORER = new RuleBasedScorer(WEIGHTS);
    private static final ScoreBands BANDS = new ScoreBands(0.40, 0.75);

    @Nested
    class PerRuleScenarios {

        @Test
        void botDrivenFanoutBurstTriggersVelocityAloneAtPointFour() {
            RiskScore score = score(
                    star("bot-ref", 10, NOW.minus(Duration.ofMinutes(30)), Duration.ofMinutes(2)),
                    List.of(), residentialAccounts("bot-ref"), baseline(2.0, 1.0));

            assertScore(score, 0.40, ReasonCode.VELOCITY_BURST);
            assertThat(decision(score)).isEqualTo(Decision.HOLD);
        }

        @Test
        void steadyReferralsOutsideTheWindowStaySilent() {
            RiskScore score = score(
                    star("steady-ref", 10, NOW.minus(Duration.ofDays(10)), Duration.ofHours(1)),
                    List.of(), residentialAccounts("steady-ref"), baseline(2.0, 1.0));

            assertScore(score, 0.0);
            assertThat(decision(score)).isEqualTo(Decision.APPROVE);
        }

        @Test
        void viralBusinessWithDiverseCustomersIsIndistinguishableFromBotBurst() {
            // Distinct customers/devices/subnets produce no overlap edges, but the
            // velocity rule has no legitimacy/supporting-signal input.
            RiskScore score = score(
                    star("viral-kibanda", 10, NOW.minus(Duration.ofMinutes(30)), Duration.ofMinutes(2)),
                    List.of(), residentialAccounts("viral-kibanda"), baseline(2.0, 1.0));

            assertScore(score, 0.40, ReasonCode.VELOCITY_BURST);
            assertThat(decision(score)).isEqualTo(Decision.HOLD);
        }

        @Test
        void deviceFarmTriggersDeviceCollisionAloneAtPointFourFive() {
            RiskScore score = score(
                    star("device-farm-ref", 4, NOW.minus(Duration.ofHours(1)), Duration.ofMinutes(5)),
                    List.of(
                            shared("farm-1", "farm-2", SharedAttributeType.DEVICE),
                            shared("farm-1", "farm-3", SharedAttributeType.DEVICE),
                            shared("farm-1", "farm-4", SharedAttributeType.DEVICE)),
                    residentialAccounts("device-farm-ref"), baseline(4.0, 1.0));

            assertScore(score, 0.45, ReasonCode.DEVICE_COLLISION);
            assertThat(decision(score)).isEqualTo(Decision.HOLD);
        }

        @Test
        void siblingsSharingOnePhoneAreIndistinguishableFromDeviceFarm() {
            RiskScore score = score(
                    List.of(referral("family-ref", "sibling-a"), referral("family-ref", "sibling-b")),
                    List.of(shared("sibling-a", "sibling-b", SharedAttributeType.DEVICE)),
                    residentialAccounts("family-ref", "sibling-a", "sibling-b"), baseline(2.0, 1.0));

            assertScore(score, 0.45, ReasonCode.DEVICE_COLLISION);
            assertThat(decision(score)).isEqualTo(Decision.HOLD);
        }

        @Test
        void deviceModelSimilarityWithoutFingerprintEqualityStaysSilent() {
            RiskScore score = score(
                    List.of(referral("family-ref", "sibling-a"), referral("family-ref", "sibling-b")),
                    List.of(), residentialAccounts("family-ref", "sibling-a", "sibling-b"), baseline(2.0, 1.0));

            assertScore(score, 0.0);
        }

        @Test
        void deviceFarmOnOneSubnetTriggersIpCollisionAloneAtPointThree() {
            RiskScore score = score(
                    star("ip-farm-ref", 4, NOW.minus(Duration.ofHours(1)), Duration.ofMinutes(5)),
                    List.of(
                            shared("ip-farm-1", "ip-farm-2", SharedAttributeType.IP_SUBNET),
                            shared("ip-farm-1", "ip-farm-3", SharedAttributeType.IP_SUBNET),
                            shared("ip-farm-1", "ip-farm-4", SharedAttributeType.IP_SUBNET)),
                    residentialAccounts("ip-farm-ref"), baseline(4.0, 1.0));

            assertScore(score, 0.30, ReasonCode.IP_SUBNET_COLLISION);
            assertThat(decision(score)).isEqualTo(Decision.APPROVE);
        }

        @Test
        void householdOnSafaricomHomeFibreStillEmitsIpReasonButDoesNotHold() {
            RiskScore score = score(
                    List.of(referral("home-ref", "sibling-a"), referral("home-ref", "sibling-b")),
                    List.of(shared("sibling-a", "sibling-b", SharedAttributeType.IP_SUBNET)),
                    residentialAccounts("home-ref", "sibling-a", "sibling-b"), baseline(2.0, 1.0));

            assertScore(score, 0.30, ReasonCode.IP_SUBNET_COLLISION);
            assertThat(decision(score)).isEqualTo(Decision.APPROVE);
        }

        @Test
        void siblingsSharingBothPhoneAndRouterHitTheRejectBoundary() {
            RiskScore score = score(
                    List.of(referral("home-ref", "sibling-a"), referral("home-ref", "sibling-b")),
                    List.of(
                            shared("sibling-a", "sibling-b", SharedAttributeType.DEVICE),
                            shared("sibling-a", "sibling-b", SharedAttributeType.IP_SUBNET)),
                    residentialAccounts("home-ref", "sibling-a", "sibling-b"), baseline(2.0, 1.0));

            assertScore(score, 0.75, ReasonCode.DEVICE_COLLISION, ReasonCode.IP_SUBNET_COLLISION);
            assertThat(decision(score)).isEqualTo(Decision.REJECT);
        }

        @Test
        void threeAccountReferralRingTriggersCycleAloneAtPointFive() {
            RiskScore score = score("ring-a", List.of(
                    referral("ring-a", "ring-b"),
                    referral("ring-b", "ring-c"),
                    referral("ring-c", "ring-a")), List.of(), residentialAccounts("ring-a", "ring-b", "ring-c"),
                    baseline(1.0, 1.0));

            assertScore(score, 0.50, ReasonCode.CYCLE_DETECTED);
            assertThat(decision(score)).isEqualTo(Decision.HOLD);
        }

        @Test
        void twoFriendsReferringEachOtherAreTreatedAsTheSameCycleSignal() {
            RiskScore score = score("friend-a", List.of(
                    referral("friend-a", "friend-b"), referral("friend-b", "friend-a")),
                    List.of(), residentialAccounts("friend-a", "friend-b"), baseline(1.0, 1.0));

            assertScore(score, 0.50, ReasonCode.CYCLE_DETECTED);
            assertThat(decision(score)).isEqualTo(Decision.HOLD);
        }

        @Test
        void acyclicFriendChainStaysSilent() {
            RiskScore score = score("friend-a", List.of(
                    referral("friend-a", "friend-b"), referral("friend-b", "friend-c")),
                    List.of(), residentialAccounts("friend-a", "friend-b", "friend-c"), baseline(1.0, 1.0));

            assertScore(score, 0.0);
        }

        @Test
        void cloudProxyAccountTriggersDatacenterAloneAtPointSeven() {
            RiskScore score = score(
                    List.of(referral("proxy-ref", "proxy-user")), List.of(),
                    List.of(account("proxy-ref", IpType.RESIDENTIAL), account("proxy-user", IpType.DATACENTER)),
                    baseline(1.0, 1.0));

            assertScore(score, 0.70, ReasonCode.DATACENTER_OR_VPN_IP);
            assertThat(decision(score)).isEqualTo(Decision.HOLD);
        }

        @Test
        void legitimateCorporateVpnIsIndistinguishableFromFraudProxy() {
            RiskScore score = score(
                    List.of(referral("employee-ref", "employee")), List.of(),
                    List.of(account("employee-ref", IpType.RESIDENTIAL), account("employee", IpType.DATACENTER)),
                    baseline(1.0, 1.0));

            assertScore(score, 0.70, ReasonCode.DATACENTER_OR_VPN_IP);
            assertThat(decision(score)).isEqualTo(Decision.HOLD);
        }

        @Test
        void travellerOnResidentialIpAndKenyanMobileCustomerStaySilent() {
            RiskScore score = score(
                    List.of(referral("travel-ref", "traveller"), referral("travel-ref", "mobile-user")),
                    List.of(), List.of(
                            account("travel-ref", IpType.RESIDENTIAL),
                            account("traveller", IpType.RESIDENTIAL),
                            account("mobile-user", IpType.MOBILE)), baseline(2.0, 1.0));

            assertScore(score, 0.0);
        }

        @Test
        void bonusAndBailCannotTriggerBecausePostConversionActivityIsNotModelled() {
            RiskScore score = score(
                    List.of(referral("bail-ref", "bonus-and-bail-user")), List.of(),
                    List.of(account("bail-ref", IpType.RESIDENTIAL),
                            new AccountNode("bonus-and-bail-user", IpType.RESIDENTIAL, true)), baseline(1.0, 1.0));

            assertScore(score, 0.0);
            assertThat(score.reasonCodes()).doesNotContain(ReasonCode.values());
        }

        @Test
        void genuinePostConversionChurnProducesTheSameZeroScore() {
            RiskScore score = score(
                    List.of(referral("churn-ref", "genuine-churn-user")), List.of(),
                    List.of(account("churn-ref", IpType.RESIDENTIAL),
                            new AccountNode("genuine-churn-user", IpType.RESIDENTIAL, true)), baseline(1.0, 1.0));

            assertScore(score, 0.0);
        }
    }

    @Nested
    class CombinedRuleScenarios {

        @Test
        void referralDeviceAndIpOverlapRejectsAtPointSevenFive() {
            List<ReferralEdge> referrals = List.of(
                    referral("overlap-ref", "overlap-a"), referral("overlap-ref", "overlap-b"));
            List<SharedAttributeEdge> overlap = List.of(
                    shared("overlap-a", "overlap-b", SharedAttributeType.DEVICE),
                    shared("overlap-a", "overlap-b", SharedAttributeType.IP_SUBNET));

            RiskScore score = score(referrals, overlap,
                    residentialAccounts("overlap-ref", "overlap-a", "overlap-b"), baseline(2.0, 1.0));

            assertScore(score, 0.75, ReasonCode.DEVICE_COLLISION, ReasonCode.IP_SUBNET_COLLISION);
            assertThat(score.value() - WEIGHTS.datacenterWeight()).isCloseTo(0.05, within(1.0e-9));
            assertThat(decision(score)).isEqualTo(Decision.REJECT);
        }

        @Test
        void severalSubthresholdFactsDoNotNaivelyAccumulate() {
            // fan-out z = 2.5 (< 3), plus three same-subnet edges. Repeated hits of
            // one rule count once, so only the 0.30 IP contribution remains.
            RiskScore score = score(
                    star("weak-ref", 7, NOW.minus(Duration.ofHours(1)), Duration.ofMinutes(5)),
                    List.of(
                            shared("weak-1", "weak-2", SharedAttributeType.IP_SUBNET),
                            shared("weak-2", "weak-3", SharedAttributeType.IP_SUBNET),
                            shared("weak-3", "weak-4", SharedAttributeType.IP_SUBNET)),
                    residentialAccounts("weak-ref"), baseline(2.0, 2.0));

            assertScore(score, 0.30, ReasonCode.IP_SUBNET_COLLISION);
            assertThat(decision(score)).isEqualTo(Decision.APPROVE);
        }

        @Test
        void exactPointSevenFiveBoundaryRejectsWhilePointSevenHolds() {
            assertThat(BANDS.classify(0.70)).isEqualTo(Decision.HOLD);
            assertThat(BANDS.classify(0.75)).isEqualTo(Decision.REJECT);
        }

        @Test
        void velocityDeviceIpAndDatacenterSignalsClampAtOne() {
            RiskScore score = score(
                    star("strong-ref", 10, NOW.minus(Duration.ofMinutes(30)), Duration.ofMinutes(2)),
                    List.of(
                            shared("strong-1", "strong-2", SharedAttributeType.DEVICE),
                            shared("strong-1", "strong-2", SharedAttributeType.IP_SUBNET)),
                    List.of(account("strong-ref", IpType.DATACENTER)), baseline(2.0, 1.0));

            assertScore(score, 1.0,
                    ReasonCode.VELOCITY_BURST,
                    ReasonCode.DEVICE_COLLISION,
                    ReasonCode.IP_SUBNET_COLLISION,
                    ReasonCode.DATACENTER_OR_VPN_IP);
            assertThat(decision(score)).isEqualTo(Decision.REJECT);
        }
    }

    @Nested
    class EvasionCharacterisation {

        @Test
        void spacingReferralsOutsideTwentyFourHoursEvadesVelocity() {
            RiskScore score = score(
                    star("paced-ref", 25, NOW.minus(Duration.ofDays(2)), Duration.ofMinutes(30)),
                    List.of(), residentialAccounts("paced-ref"), baseline(2.0, 1.0));

            assertScore(score, 0.0);
        }

        @Test
        void rotatingDevicesAvoidsDeviceRuleButSharedSubnetStillContributes() {
            RiskScore score = score(
                    List.of(referral("rotate-ref", "rotate-a"), referral("rotate-ref", "rotate-b")),
                    List.of(shared("rotate-a", "rotate-b", SharedAttributeType.IP_SUBNET)),
                    residentialAccounts("rotate-ref", "rotate-a", "rotate-b"), baseline(2.0, 1.0));

            assertScore(score, 0.30, ReasonCode.IP_SUBNET_COLLISION);
        }

        @Test
        void rotatingSubnetsAvoidsIpRuleButSharedDeviceStillContributes() {
            RiskScore score = score(
                    List.of(referral("rotate-ref", "rotate-a"), referral("rotate-ref", "rotate-b")),
                    List.of(shared("rotate-a", "rotate-b", SharedAttributeType.DEVICE)),
                    residentialAccounts("rotate-ref", "rotate-a", "rotate-b"), baseline(2.0, 1.0));

            assertScore(score, 0.45, ReasonCode.DEVICE_COLLISION);
        }

        @Test
        void leavingReferralChainOpenEvadesCycleRule() {
            RiskScore score = score("open-a", List.of(
                    referral("open-a", "open-b"), referral("open-b", "open-c"),
                    referral("open-c", "open-d")), List.of(),
                    residentialAccounts("open-a", "open-b", "open-c", "open-d"), baseline(1.0, 1.0));

            assertScore(score, 0.0);
        }

        @Test
        void residentialProxyClassificationEvadesDatacenterRule() {
            RiskScore score = score(
                    List.of(referral("proxy-ref", "proxy-user")), List.of(),
                    List.of(account("proxy-ref", IpType.RESIDENTIAL),
                            account("proxy-user", IpType.RESIDENTIAL)), baseline(1.0, 1.0));

            assertScore(score, 0.0);
        }

        @Test
        void rotatingBothDevicesAndSubnetsEvadesBothCollisionRules() {
            RiskScore score = score(
                    List.of(referral("rotate-ref", "rotate-a"), referral("rotate-ref", "rotate-b")),
                    List.of(), residentialAccounts("rotate-ref", "rotate-a", "rotate-b"), baseline(2.0, 1.0));

            assertScore(score, 0.0);
        }
    }

    private static RiskScore score(
            List<ReferralEdge> referrals, List<SharedAttributeEdge> sharedEdges,
            List<AccountNode> accounts, FanoutBaseline baseline) {
        String referrer = referrals.isEmpty() ? "referrer" : referrals.get(0).referrerUserId();
        return score(referrer, referrals, sharedEdges, accounts, baseline);
    }

    private static RiskScore score(
            String referrer, List<ReferralEdge> referrals, List<SharedAttributeEdge> sharedEdges,
            List<AccountNode> accounts, FanoutBaseline baseline) {
        ReferralNeighbourhood neighbourhood = new ReferralNeighbourhood(
                TENANT, CODE, CAMPAIGN, referrer, referrals, sharedEdges, accounts);
        return SCORER.score(new ScoringRequest(neighbourhood, baseline, NOW));
    }

    private static Decision decision(RiskScore score) {
        return BANDS.classify(score.value());
    }

    private static List<ReferralEdge> star(
            String referrer, int fanout, Instant firstReferral, Duration spacing) {
        List<ReferralEdge> edges = new ArrayList<>();
        for (int i = 0; i < fanout; i++) {
            edges.add(new ReferralEdge(referrer, referrer + "-customer-" + i,
                    firstReferral.plus(spacing.multipliedBy(i))));
        }
        return edges;
    }

    private static ReferralEdge referral(String from, String to) {
        return new ReferralEdge(from, to, NOW.minus(Duration.ofMinutes(10)));
    }

    private static SharedAttributeEdge shared(String a, String b, SharedAttributeType type) {
        return new SharedAttributeEdge(a, b, type);
    }

    private static AccountNode account(String userId, IpType ipType) {
        return new AccountNode(userId, ipType);
    }

    private static List<AccountNode> residentialAccounts(String... userIds) {
        return java.util.Arrays.stream(userIds)
                .map(userId -> account(userId, IpType.RESIDENTIAL))
                .toList();
    }

    private static FanoutBaseline baseline(double mean, double standardDeviation) {
        return new FanoutBaseline(mean, standardDeviation, 100);
    }

    private static void assertScore(RiskScore score, double expected, ReasonCode... reasons) {
        assertThat(score.value()).isCloseTo(expected, within(1.0e-9));
        assertThat(score.reasonCodes()).containsExactly(reasons);
    }
}
