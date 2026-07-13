package com.turing.vigilant.events;

import com.turing.vigilant.campaign.CampaignService;
import com.turing.vigilant.graph.GraphCommands.ConversionRecord;
import com.turing.vigilant.graph.GraphCommands.RedemptionRecord;
import com.turing.vigilant.graph.GraphStore;
import com.turing.vigilant.ipreputation.IpReputationChecker;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.IpType;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Ingests redemption and conversion events into the graph. Ingestion never gates
 * signup (spec section 2) — redemption always resolves to ACCEPT. The IP is
 * classified here (soft-flag) and the result stored on the referee node for the
 * payout-time datacenter rule to read. Events carry the {@code campaignId} the
 * code was issued against (spec §10a), validated against the tenant's campaigns.
 */
@Service
public class EventIngestionService {

    private final GraphStore graphStore;
    private final IpReputationChecker ipReputationChecker;
    private final CampaignService campaignService;
    private final Clock clock;

    public EventIngestionService(GraphStore graphStore, IpReputationChecker ipReputationChecker,
                                 CampaignService campaignService, Clock clock) {
        this.graphStore = graphStore;
        this.ipReputationChecker = ipReputationChecker;
        this.campaignService = campaignService;
        this.clock = clock;
    }

    public void recordRedemption(TenantId tenantId, CampaignId campaignId, ReferralCode referralCode,
                                 String newUserId, String deviceId, String ipAddress, Instant timestamp) {
        campaignService.requireCampaign(tenantId, campaignId);
        graphStore.recordRedemption(new RedemptionRecord(
                tenantId, campaignId, referralCode, newUserId, deviceId, ipAddress,
                orNow(timestamp), reputationTypeOf(ipAddress)));
    }

    /**
     * Classifies the IP, tolerating null/malformed input — ingestion must never
     * fail on a bad address, so anything unclassifiable is stored as UNKNOWN.
     */
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

    public void recordConversion(TenantId tenantId, CampaignId campaignId, ReferralCode referralCode,
                                 String refereeUserId, String conversionType, Instant timestamp) {
        campaignService.requireCampaign(tenantId, campaignId);
        graphStore.recordConversion(new ConversionRecord(
                tenantId, campaignId, referralCode, refereeUserId, conversionType, orNow(timestamp)));
    }

    private Instant orNow(Instant timestamp) {
        return timestamp != null ? timestamp : clock.instant();
    }
}
