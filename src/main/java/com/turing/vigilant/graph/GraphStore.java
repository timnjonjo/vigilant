package com.turing.vigilant.graph;

import com.turing.vigilant.graph.GraphCommands.ConversionRecord;
import com.turing.vigilant.graph.GraphCommands.RedemptionRecord;
import com.turing.vigilant.graph.GraphCommands.ReferrerRegistration;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * The referral graph, always tenant-scoped. Backed by Neo4j; the interface keeps
 * scoring and ingestion decoupled from Cypher and lets tests substitute an
 * in-memory store.
 */
public interface GraphStore {

    /** Issuance-time upsert of the referrer node with its identity fingerprint. */
    void registerReferrer(ReferrerRegistration registration);

    /** Records a redemption: referee node, REFERRED edge, and overlap edges. */
    void recordRedemption(RedemptionRecord redemption);

    /** Marks the referee's qualifying action on the REFERRED edge. */
    void recordConversion(ConversionRecord conversion);

    /**
     * Pulls the bounded subgraph around a referral code for scoring. REFERRED
     * traversal is scoped to {@code campaignId}; SHARES_DEVICE/SHARES_IP_SUBNET
     * overlap edges are campaign-agnostic (cross-campaign overlap is a stronger
     * signal, spec §10a).
     */
    ReferralNeighbourhood loadNeighbourhood(TenantId tenantId, ReferralCode referralCode, CampaignId campaignId);

    /**
     * A bounded subgraph for the case-detail <em>visualization</em> only — never
     * for scoring. Unlike {@link #loadNeighbourhood} the REFERRED traversal is
     * depth- and count-capped (no unbounded expansion) and it does not widen the
     * cluster by a shared-attribute hop. Only the shared-attribute edge types in
     * {@code includedSharedEdges} are fetched, so a case whose reasons are purely
     * velocity, cycle or datacenter pulls no device/IP overlap edges at all.
     */
    ReferralNeighbourhood loadCaseVisualization(
            TenantId tenantId, ReferralCode referralCode, CampaignId campaignId,
            Set<SharedAttributeType> includedSharedEdges);

    /**
     * Population fan-out baseline for the velocity rule, computed <em>per
     * campaign</em> (spec §10a) — a high-incentive campaign naturally draws more
     * aggressive referral activity than a low-incentive one.
     */
    FanoutBaseline fanoutBaseline(
            TenantId tenantId, CampaignId campaignId, Instant windowStart, Instant windowEnd);

    /** Resolves the referrer's userId only when the code was issued for this campaign. */
    Optional<String> findReferrerUserId(
            TenantId tenantId, CampaignId campaignId, ReferralCode referralCode);

    /** True only for a code issued by this tenant for this exact campaign. */
    default boolean referralCodeExists(
            TenantId tenantId, CampaignId campaignId, ReferralCode referralCode) {
        return findReferrerUserId(tenantId, campaignId, referralCode).isPresent();
    }

    /** True only when this code/campaign has a redemption edge to the stated referee. */
    boolean referralExists(
            TenantId tenantId, CampaignId campaignId, ReferralCode referralCode, String refereeUserId);

    /** True only when that exact referral has completed its qualifying conversion. */
    boolean convertedReferralExists(
            TenantId tenantId, CampaignId campaignId, ReferralCode referralCode, String refereeUserId);

    /**
     * Issuance-time identity pre-check: does any other account in the tenant
     * already share this device or IP subnet? Used to tag (never deny) a risky
     * referral code (spec section 3).
     */
    boolean identityCollisionExists(TenantId tenantId, String userId, String deviceId, String ipAddress);
}
