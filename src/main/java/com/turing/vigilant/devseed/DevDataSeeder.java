package com.turing.vigilant.devseed;

import com.turing.vigilant.campaign.Campaign;
import com.turing.vigilant.campaign.CampaignRepository;
import com.turing.vigilant.campaign.CampaignService;
import com.turing.vigilant.campaign.CampaignStatus;
import com.turing.vigilant.campaign.ConversionCriteria;
import com.turing.vigilant.casequeue.CaseOpening;
import com.turing.vigilant.casequeue.CaseRecorder;
import com.turing.vigilant.casequeue.FraudCase;
import com.turing.vigilant.casequeue.FraudCaseRepository;
import com.turing.vigilant.graph.GraphCommands.ConversionRecord;
import com.turing.vigilant.graph.GraphCommands.RedemptionRecord;
import com.turing.vigilant.graph.GraphCommands.ReferrerRegistration;
import com.turing.vigilant.graph.GraphStore;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.Decision;
import com.turing.vigilant.shared.IpType;
import com.turing.vigilant.shared.ReasonCode;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;
import org.neo4j.driver.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Local/dev data seeder — populates Postgres (campaigns + case queue) and Neo4j
 * (referral graph) with a realistic spread so the dashboard is meaningful on
 * first login, instead of empty screens.
 *
 * <p>Gated behind the {@code dev} Spring profile so it can never run in
 * production. The graph is written through the real {@link GraphStore} write
 * commands — the same path production ingestion uses — so node/edge shapes are
 * identical to live data (campaign-scoped {@code REFERRED} edges, auto {@code
 * SHARES_DEVICE}/{@code SHARES_IP_SUBNET} edges). Cases go through
 * {@link CaseRecorder} (OPEN) and the {@link FraudCase} entity directly (RESOLVED,
 * to skip the synchronous resolution webhook).
 *
 * <p>Two campaigns per tenant (spec §10a): one ACTIVE holding the open cases, one
 * ENDED holding the resolved ones — every seeded edge and case carries its
 * {@code campaignId}. Idempotent: skips if {@code loob-bank} already has cases,
 * unless {@code vigilant.dev-seed.reset=true}, which clears cases, graph and
 * campaigns then reseeds.
 *
 * <p>All data is for the single wired tenant {@code loob-bank}.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private static final TenantId TENANT = TenantId.of("loob-bank");
    private static final String ANALYST = "analyst-loob";

    private final GraphStore graphStore;
    private final CaseRecorder caseRecorder;
    private final FraudCaseRepository caseRepository;
    private final CampaignService campaignService;
    private final CampaignRepository campaignRepository;
    private final Driver neo4jDriver;
    private final Clock clock;
    private final boolean reset;

    public DevDataSeeder(GraphStore graphStore,
                         CaseRecorder caseRecorder,
                         FraudCaseRepository caseRepository,
                         CampaignService campaignService,
                         CampaignRepository campaignRepository,
                         Driver neo4jDriver,
                         Clock clock,
                         @Value("${vigilant.dev-seed.reset:false}") boolean reset) {
        this.graphStore = graphStore;
        this.caseRecorder = caseRecorder;
        this.caseRepository = caseRepository;
        this.campaignService = campaignService;
        this.campaignRepository = campaignRepository;
        this.neo4jDriver = neo4jDriver;
        this.clock = clock;
        this.reset = reset;
    }

    @Override
    public void run(String... args) {
        List<FraudCase> existing = caseRepository.findByTenantIdOrderByOpenedAtDesc(TENANT.value());
        if (!existing.isEmpty() && !reset) {
            log.info("[dev-seed] {} already has {} cases — skipping (set vigilant.dev-seed.reset=true to reseed)",
                    TENANT.value(), existing.size());
            return;
        }
        if (reset && !existing.isEmpty()) {
            log.info("[dev-seed] reset=true — clearing {} existing {} cases + graph + campaigns",
                    existing.size(), TENANT.value());
            caseRepository.deleteAll(existing);
            campaignRepository.deleteAll(campaignRepository.findByTenantIdOrderByCreatedAtDesc(TENANT.value()));
            clearTenantGraph();
        }

        log.info("[dev-seed] seeding {} demo data (campaigns + graph + cases)…", TENANT.value());
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);

        // Two campaigns: an ACTIVE one carrying the open queue, an ENDED one whose
        // cases have all been resolved.
        Campaign active = campaignService.create(TENANT, "Q3 Signup Boost",
                new BigDecimal("350.00"), today.minusDays(20), today.plusDays(40),
                CampaignStatus.ACTIVE, ConversionCriteria.FIRST_DEPOSIT, 5);
        Campaign ended = campaignService.create(TENANT, "Jielimishe Promo",
                new BigDecimal("200.00"), today.minusDays(90), today.minusDays(10),
                CampaignStatus.ENDED, ConversionCriteria.N_DAY_RETENTION, 3);
        CampaignId activeId = CampaignId.of(active.getCampaignId());
        CampaignId endedId = CampaignId.of(ended.getCampaignId());

        seedOrganic(now, activeId);
        seedFanoutBurst(now, activeId);
        seedSharedDeviceCluster(now, activeId);
        seedCycle(now, activeId);
        seedMultiEdgeOverlap(now, activeId);
        seedViralBusinessContrast(now, activeId);
        seedHouseholdContrast(now, activeId);
        seedFriendsCycleContrast(now, activeId);
        seedChurnContrast(now, activeId);
        seedCorporateVpnContrast(now, activeId);
        seedCrossCampaignOverlap(now, activeId, endedId);
        seedResolvedFalsePositive(now, endedId);
        seedResolvedConfirmedFraud(now, endedId);

        log.info("[dev-seed] done — 2 campaigns, 7 cases, plus fraud-rule validation contrast subgraphs.");
    }

    // ---- Scenario 1: organic contrast (clean graph, held only for mild velocity) ----
    private void seedOrganic(Instant now, CampaignId campaign) {
        Instant at = now.minus(Duration.ofDays(2));
        String code = "LOOB-ORGANIC-01";
        referrer(code, "org-ref", "dev-org-ref", "197.232.10.1", IpType.RESIDENTIAL, at);
        redeem(campaign, code, "org-r1", "dev-org-1", "197.232.20.5", IpType.RESIDENTIAL, at.plusSeconds(3600));
        redeem(campaign, code, "org-r2", "dev-org-2", "105.160.30.9", IpType.RESIDENTIAL, at.plusSeconds(9200));
        redeem(campaign, code, "org-r3", "dev-org-3", "154.70.40.2", IpType.MOBILE, at.plusSeconds(15000));
        convert(campaign, code, "org-r1", at.plusSeconds(7200));
        openCase(campaign, code, "org-r1", Decision.HOLD, 0.41, List.of(ReasonCode.VELOCITY_BURST), at);
    }

    // ---- Scenario 2: fan-out burst (one referrer, many referees in a tight window) ----
    private void seedFanoutBurst(Instant now, CampaignId campaign) {
        Instant at = now.minus(Duration.ofDays(1));
        String code = "LOOB-FANOUT-07";
        referrer(code, "fan-ref", "dev-fan-ref", "41.80.1.1", IpType.RESIDENTIAL, at);
        for (int i = 1; i <= 9; i++) {
            IpType type = (i % 3 == 0) ? IpType.MOBILE : IpType.RESIDENTIAL;
            // distinct device + distinct /24 per referee -> pure fan-out, no shared-attribute edges
            redeem(campaign, code, "fan-r" + i, "dev-fan-" + i, "41.80." + (10 + i) + ".5", type, at.plusSeconds(i * 90L));
            if (i % 2 == 0) {
                convert(campaign, code, "fan-r" + i, at.plusSeconds(i * 90L + 300));
            }
        }
        openCase(campaign, code, "fan-r1", Decision.HOLD, 0.56, List.of(ReasonCode.VELOCITY_BURST), at);
    }

    // ---- Scenario 3: shared-device cluster (referees share one fingerprint) ----
    private void seedSharedDeviceCluster(Instant now, CampaignId campaign) {
        Instant at = now.minus(Duration.ofDays(3));
        String code = "LOOB-DEVICE-03";
        String sharedDevice = "SHARED-DEV-AAA";
        referrer(code, "dev-ref", "dev-dev-ref", "105.20.5.1", IpType.RESIDENTIAL, at);
        for (int i = 1; i <= 4; i++) {
            // same device across all referees -> SHARES_DEVICE edges; distinct /24 -> no IP edges
            redeem(campaign, code, "dev-r" + i, sharedDevice, "105.20." + (30 + i) + ".7", IpType.RESIDENTIAL, at.plusSeconds(i * 240L));
        }
        convert(campaign, code, "dev-r1", at.plusSeconds(1000));
        openCase(campaign, code, "dev-r1", Decision.HOLD, 0.63, List.of(ReasonCode.DEVICE_COLLISION), at);
    }

    // ---- Scenario 4: cycle (small ring referring each other) ----
    private void seedCycle(Instant now, CampaignId campaign) {
        Instant at = now.minus(Duration.ofDays(5));
        // A -> B -> C -> A. Each node issues its own code; the case keys off A's code.
        referrer("LOOB-CYCLE-02", "cyc-a", "dev-cyc-a", "196.201.1.10", IpType.RESIDENTIAL, at);
        referrer("LOOB-CYCLE-02-B", "cyc-b", "dev-cyc-b", "196.201.2.10", IpType.RESIDENTIAL, at.plusSeconds(60));
        referrer("LOOB-CYCLE-02-C", "cyc-c", "dev-cyc-c", "196.201.3.10", IpType.RESIDENTIAL, at.plusSeconds(120));
        redeem(campaign, "LOOB-CYCLE-02", "cyc-b", "dev-cyc-b", "196.201.2.10", IpType.RESIDENTIAL, at.plusSeconds(200));
        redeem(campaign, "LOOB-CYCLE-02-B", "cyc-c", "dev-cyc-c", "196.201.3.10", IpType.RESIDENTIAL, at.plusSeconds(400));
        redeem(campaign, "LOOB-CYCLE-02-C", "cyc-a", "dev-cyc-a", "196.201.1.10", IpType.RESIDENTIAL, at.plusSeconds(600));
        openCase(campaign, "LOOB-CYCLE-02", "cyc-b", Decision.REJECT, 0.81, List.of(ReasonCode.CYCLE_DETECTED), at);
    }

    // ---- Scenario 5: multi-edge-type overlap (referral + device + IP + datacenter): highest score ----
    private void seedMultiEdgeOverlap(Instant now, CampaignId campaign) {
        Instant at = now.minus(Duration.ofHours(4));
        String code = "LOOB-OVERLAP-09";
        String sharedDevice = "SHARED-DEV-OVL";
        // referrer + referees all on the same datacenter /24 AND same device -> all three edge types coincide
        referrer(code, "ov-ref", "dev-ov-ref", "3.5.140.1", IpType.DATACENTER, at);
        for (int i = 1; i <= 4; i++) {
            redeem(campaign, code, "ov-r" + i, sharedDevice, "3.5.140." + (10 + i), IpType.DATACENTER, at.plusSeconds(i * 45L));
        }
        convert(campaign, code, "ov-r1", at.plusSeconds(500));
        openCase(campaign, code, "ov-r1", Decision.REJECT, 0.93,
                List.of(ReasonCode.VELOCITY_BURST, ReasonCode.DEVICE_COLLISION,
                        ReasonCode.IP_SUBNET_COLLISION, ReasonCode.DATACENTER_OR_VPN_IP), at);
    }

    // ---- Validation contrasts: realistic lookalikes used by the repeatable rule audit ----

    /** Popular local business: same fan-out shape as a bot burst, but diverse identities. */
    private void seedViralBusinessContrast(Instant now, CampaignId campaign) {
        Instant at = now.minus(Duration.ofHours(2));
        String code = "LOOB-VIRAL-BIZ";
        referrer(code, "viral-kibanda", "dev-viral-owner", "41.70.1.1", IpType.RESIDENTIAL, at);
        for (int i = 1; i <= 10; i++) {
            redeem(campaign, code, "viral-customer-" + i, "dev-viral-" + i,
                    "41." + (100 + i) + ".1.5", IpType.RESIDENTIAL, at.plusSeconds(i * 90L));
        }
    }

    /** Two siblings sharing both one phone and one home-fibre router. */
    private void seedHouseholdContrast(Instant now, CampaignId campaign) {
        Instant at = now.minus(Duration.ofHours(3));
        String code = "LOOB-HOUSEHOLD";
        referrer(code, "household-ref", "dev-household-ref", "105.30.1.1", IpType.RESIDENTIAL, at);
        redeem(campaign, code, "household-a", "family-shared-phone", "105.30.20.2", IpType.RESIDENTIAL, at.plusSeconds(60));
        redeem(campaign, code, "household-b", "family-shared-phone", "105.30.20.3", IpType.RESIDENTIAL, at.plusSeconds(120));
    }

    /** A single two-friend cycle, the ambiguity contrast for the three-node ring. */
    private void seedFriendsCycleContrast(Instant now, CampaignId campaign) {
        Instant at = now.minus(Duration.ofHours(5));
        referrer("LOOB-FRIEND-A", "friend-a", "dev-friend-a", "196.10.1.1", IpType.RESIDENTIAL, at);
        referrer("LOOB-FRIEND-B", "friend-b", "dev-friend-b", "196.10.2.1", IpType.RESIDENTIAL, at);
        redeem(campaign, "LOOB-FRIEND-A", "friend-b", "dev-friend-b", "196.10.2.1", IpType.RESIDENTIAL, at.plusSeconds(60));
        redeem(campaign, "LOOB-FRIEND-B", "friend-a", "dev-friend-a", "196.10.1.1", IpType.RESIDENTIAL, at.plusSeconds(120));
    }

    /** Converted then churned: the graph has conversion but no post-conversion activity model. */
    private void seedChurnContrast(Instant now, CampaignId campaign) {
        Instant at = now.minus(Duration.ofDays(12));
        String code = "LOOB-CHURNED";
        referrer(code, "churn-ref", "dev-churn-ref", "154.80.1.1", IpType.RESIDENTIAL, at);
        redeem(campaign, code, "churned-user", "dev-churned", "154.80.2.1", IpType.RESIDENTIAL, at.plusSeconds(60));
        convert(campaign, code, "churned-user", at.plusSeconds(3600));
    }

    /** Legitimate employee on a corporate VPN, indistinguishable from a cloud proxy. */
    private void seedCorporateVpnContrast(Instant now, CampaignId campaign) {
        Instant at = now.minus(Duration.ofHours(6));
        String code = "LOOB-CORP-VPN";
        referrer(code, "employee-ref", "dev-employee-ref", "197.220.1.1", IpType.RESIDENTIAL, at);
        redeem(campaign, code, "employee-vpn", "dev-employee", "3.8.1.10", IpType.DATACENTER, at.plusSeconds(60));
    }

    /** Same identity farmed in both campaigns; shared edges must cross campaign scope. */
    private void seedCrossCampaignOverlap(Instant now, CampaignId active, CampaignId ended) {
        Instant endedAt = now.minus(Duration.ofDays(12));
        Instant activeAt = now.minus(Duration.ofHours(7));
        referrer("LOOB-CROSS-B", "cross-ref", "dev-cross-ref", "41.200.1.1", IpType.RESIDENTIAL, endedAt);
        redeem(ended, "LOOB-CROSS-B", "cross-b", "cross-shared-device", "41.200.20.3", IpType.RESIDENTIAL, endedAt.plusSeconds(60));
        referrer("LOOB-CROSS-A", "cross-ref", "dev-cross-ref", "41.200.1.1", IpType.RESIDENTIAL, activeAt);
        redeem(active, "LOOB-CROSS-A", "cross-a", "cross-shared-device", "41.200.20.2", IpType.RESIDENTIAL, activeAt.plusSeconds(60));
    }

    // ---- Scenario 6: already-resolved, ruled a false positive (drives FP-rate) ----
    private void seedResolvedFalsePositive(Instant now, CampaignId campaign) {
        Instant openedAt = now.minus(Duration.ofDays(8));
        Instant resolvedAt = now.minus(Duration.ofDays(1));
        String code = "LOOB-FANOUT-04";
        referrer(code, "fp-ref", "dev-fp-ref", "41.90.7.1", IpType.RESIDENTIAL, openedAt);
        for (int i = 1; i <= 3; i++) {
            redeem(campaign, code, "fp-r" + i, "dev-fp-" + i, "41.90." + (10 + i) + ".4", IpType.RESIDENTIAL, openedAt.plusSeconds(i * 120L));
        }
        convert(campaign, code, "fp-r1", openedAt.plusSeconds(400));
        resolvedCase(campaign, code, "fp-r1", Decision.HOLD, 0.48, List.of(ReasonCode.VELOCITY_BURST),
                openedAt, Decision.APPROVE, resolvedAt);
    }

    // ---- Scenario 7: already-resolved, confirmed fraud (held then rejected) ----
    private void seedResolvedConfirmedFraud(Instant now, CampaignId campaign) {
        Instant openedAt = now.minus(Duration.ofDays(6));
        Instant resolvedAt = now.minus(Duration.ofDays(2));
        String code = "LOOB-DEVICE-08";
        String sharedDevice = "SHARED-DEV-TP";
        referrer(code, "tp-ref", "dev-tp-ref", "62.8.90.1", IpType.RESIDENTIAL, openedAt);
        for (int i = 1; i <= 3; i++) {
            // shared device AND shared /24 -> device + IP edges
            redeem(campaign, code, "tp-r" + i, sharedDevice, "62.8.90." + (10 + i), IpType.RESIDENTIAL, openedAt.plusSeconds(i * 150L));
        }
        resolvedCase(campaign, code, "tp-r1", Decision.HOLD, 0.72,
                List.of(ReasonCode.DEVICE_COLLISION, ReasonCode.IP_SUBNET_COLLISION),
                openedAt, Decision.REJECT, resolvedAt);
    }

    // ---- helpers ----

    private void referrer(String code, String userId, String deviceId, String ip, IpType ipType, Instant at) {
        graphStore.registerReferrer(new ReferrerRegistration(
                TENANT, userId, deviceId, ip, ReferralCode.of(code), at, ipType));
    }

    private void redeem(CampaignId campaign, String code, String refereeUserId, String deviceId,
                        String ip, IpType ipType, Instant at) {
        graphStore.recordRedemption(new RedemptionRecord(
                TENANT, campaign, ReferralCode.of(code), refereeUserId, deviceId, ip, at, ipType));
    }

    private void convert(CampaignId campaign, String code, String refereeUserId, Instant at) {
        graphStore.recordConversion(new ConversionRecord(
                TENANT, campaign, ReferralCode.of(code), refereeUserId, "SIGNUP", at));
    }

    private void openCase(CampaignId campaign, String code, String refereeUserId, Decision decision,
                          double score, List<ReasonCode> reasons, Instant openedAt) {
        caseRecorder.record(new CaseOpening(
                TENANT, campaign, ReferralCode.of(code), refereeUserId, decision, score, reasons, openedAt));
    }

    private void resolvedCase(CampaignId campaign, String code, String refereeUserId, Decision decision,
                              double score, List<ReasonCode> reasons, Instant openedAt,
                              Decision resolution, Instant resolvedAt) {
        // Build + resolve on the entity directly to avoid the (synchronous, throwing)
        // resolution webhook a dev host has no listener for.
        FraudCase c = FraudCase.open(new CaseOpening(
                TENANT, campaign, ReferralCode.of(code), refereeUserId, decision, score, reasons, openedAt));
        c.resolve(resolution, ANALYST, resolvedAt);
        caseRepository.save(c);
    }

    private void clearTenantGraph() {
        try (var session = neo4jDriver.session()) {
            session.run("MATCH (a:Account {tenantId: $t}) DETACH DELETE a",
                    org.neo4j.driver.Values.parameters("t", TENANT.value()));
        }
    }
}
