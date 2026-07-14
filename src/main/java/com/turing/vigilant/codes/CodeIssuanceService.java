package com.turing.vigilant.codes;

import com.turing.vigilant.campaign.CampaignService;
import com.turing.vigilant.graph.GraphCommands.ReferrerRegistration;
import com.turing.vigilant.graph.GraphStore;
import com.turing.vigilant.ipreputation.IpReputationChecker;
import com.turing.vigilant.ipreputation.IpAddresses;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.IpType;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.DomainEvent;
import com.turing.vigilant.shared.TenantId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Issues referral codes. A code is ALWAYS issued (spec section 2, principle 1):
 * a risky identity is tagged via {@code riskFlag}, never denied. Codes are issued
 * against a specific ACTIVE campaign (spec §10a) — a request for a non-existent,
 * wrong-tenant, or non-active campaign is rejected. The referrer's IP is
 * classified so a datacenter-hosted referrer is visible on the graph.
 */
@Service
public class CodeIssuanceService {

    private static final Logger log = LoggerFactory.getLogger(CodeIssuanceService.class);

    private final GraphStore graphStore;
    private final com.turing.vigilant.codes.ReferralCodeGenerator generator;
    private final IpReputationChecker ipReputationChecker;
    private final CampaignService campaignService;
    private final Clock clock;

    public CodeIssuanceService(GraphStore graphStore, com.turing.vigilant.codes.ReferralCodeGenerator generator,
                               IpReputationChecker ipReputationChecker, CampaignService campaignService, Clock clock) {
        this.graphStore = graphStore;
        this.generator = generator;
        this.ipReputationChecker = ipReputationChecker;
        this.campaignService = campaignService;
        this.clock = clock;
    }

    public IssuedCode issue(TenantId tenantId, CampaignId campaignId, String userId,
                            String deviceId, String ipAddress) {
        campaignService.requireActiveCampaign(tenantId, campaignId);
        String normalizedIpAddress = IpAddresses.normalizeLiteralOrNull(ipAddress);
        ReferralCode code = generator.generate(tenantId, userId);
        graphStore.registerReferrer(new ReferrerRegistration(
                tenantId, campaignId, userId, deviceId, normalizedIpAddress, code, clock.instant(),
                reputationTypeOf(normalizedIpAddress)));
        boolean riskFlag = graphStore.identityCollisionExists(
                tenantId, userId, deviceId, normalizedIpAddress);
        // Safe fields only: tenant/campaign/code + the risk flag. The referrer's
        // userId, deviceId and IP are identity/PII and are deliberately not logged.
        DomainEvent.of(log, "code_issued")
                .field("tenantId", tenantId.value())
                .field("campaignId", campaignId.value())
                .field("referralCode", code.value())
                .field("riskFlag", riskFlag)
                .log();
        return new IssuedCode(code, riskFlag);
    }

    /** Classifies a normalized IP; missing/invalid source input is represented as UNKNOWN. */
    private IpType reputationTypeOf(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return IpType.UNKNOWN;
        }
        try {
            return ipReputationChecker.check(ipAddress).type();
        } catch (IllegalArgumentException e) {
            return IpType.UNKNOWN;
        }
    }

    public record IssuedCode(ReferralCode referralCode, boolean riskFlag) {
    }
}
