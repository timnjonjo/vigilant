package com.turing.vigilant.graph;

import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.IpType;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;

import java.time.Instant;

/**
 * Write commands accepted by the {@link GraphStore}, one per lifecycle event in
 * the vertical slice: issuance → redemption → conversion. Redemption/conversion
 * carry the {@code campaignId} that lands on the {@code REFERRED} edge (spec
 * §10a). Issuance binds each generated code to one campaign.
 */
public final class GraphCommands {

    private GraphCommands() {
    }

    /** Code issuance: create/update the referrer node with its fingerprint. */
    public record ReferrerRegistration(
            TenantId tenantId,
            CampaignId campaignId,
            String userId,
            String deviceId,
            String ipAddress,
            ReferralCode referralCode,
            Instant at,
            IpType ipType) {

        /** Back-compat overload for callers that don't classify the IP; defaults to UNKNOWN. */
        public ReferrerRegistration(TenantId tenantId, CampaignId campaignId, String userId,
                                    String deviceId, String ipAddress, ReferralCode referralCode, Instant at) {
            this(tenantId, campaignId, userId, deviceId, ipAddress, referralCode, at, IpType.UNKNOWN);
        }
    }

    /** Redemption: create the referee, the campaign-scoped REFERRED edge, and overlap edges. */
    public record RedemptionRecord(
            TenantId tenantId,
            CampaignId campaignId,
            ReferralCode referralCode,
            String refereeUserId,
            String deviceId,
            String ipAddress,
            Instant at,
            IpType ipType) {

        /** Back-compat overload for callers that don't classify the IP; defaults to UNKNOWN. */
        public RedemptionRecord(TenantId tenantId, CampaignId campaignId, ReferralCode referralCode,
                                String refereeUserId, String deviceId, String ipAddress, Instant at) {
            this(tenantId, campaignId, referralCode, refereeUserId, deviceId, ipAddress, at, IpType.UNKNOWN);
        }
    }

    /** Conversion: mark the referee's qualifying action on the campaign's REFERRED edge. */
    public record ConversionRecord(
            TenantId tenantId,
            CampaignId campaignId,
            ReferralCode referralCode,
            String refereeUserId,
            String conversionType,
            Instant at) {
    }
}
