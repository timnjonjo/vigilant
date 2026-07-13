package com.turing.vigilant.casequeue;

import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.Decision;
import com.turing.vigilant.shared.ReasonCode;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;

import java.time.Instant;
import java.util.List;

/**
 * Request to open a case for a HOLD or REJECT payout decision, carrying the audit
 * fields the queue persists (spec section 6). Scoped to the campaign the referral
 * code was issued against (spec §10a).
 */
public record CaseOpening(
        TenantId tenantId,
        CampaignId campaignId,
        ReferralCode referralCode,
        String refereeUserId,
        Decision decision,
        double score,
        List<ReasonCode> reasonCodes,
        Instant openedAt) {
}
