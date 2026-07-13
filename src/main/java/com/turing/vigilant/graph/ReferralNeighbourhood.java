package com.turing.vigilant.graph;

import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;

import java.util.List;

/**
 * A bounded subgraph pulled from Neo4j around a referrer, handed to the scoring
 * engine to run in-process algorithms (JGraphT cycle detection, collision and
 * fan-out checks). Not the system of record — a read-only snapshot. The REFERRED
 * edges here are scoped to {@link #campaignId}; shared-attribute edges are not.
 */
public record ReferralNeighbourhood(
        TenantId tenantId,
        ReferralCode referralCode,
        CampaignId campaignId,
        String referrerUserId,
        List<ReferralEdge> referralEdges,
        List<SharedAttributeEdge> sharedEdges,
        List<AccountNode> accounts) {

    /** Referrals directly issued by this referrer (their fan-out). */
    public List<ReferralEdge> fanoutOfReferrer() {
        return referralEdges.stream()
                .filter(edge -> edge.referrerUserId().equals(referrerUserId))
                .toList();
    }
}
