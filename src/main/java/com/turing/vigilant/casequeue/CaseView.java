package com.turing.vigilant.casequeue;

import com.turing.vigilant.shared.Decision;
import com.turing.vigilant.shared.ReasonCode;

import java.time.Instant;
import java.util.List;

/** Read model of a case for the dashboard query endpoints. */
public record CaseView(
        Long id,
        String tenantId,
        String campaignId,
        String referralCode,
        String refereeUserId,
        Decision decision,
        double score,
        List<ReasonCode> reasonCodes,
        com.turing.vigilant.casequeue.CaseStatus status,
        Decision resolution,
        String resolvedBy,
        Instant openedAt,
        Instant resolvedAt) {

    public static CaseView of(com.turing.vigilant.casequeue.FraudCase c) {
        return new CaseView(
                c.getId(), c.getTenantId(), c.getCampaignId(), c.getReferralCode(), c.getRefereeUserId(),
                c.getDecision(), c.getScore(), c.getReasonCodes(), c.getStatus(),
                c.getResolution(), c.getResolvedBy(), c.getOpenedAt(), c.getResolvedAt());
    }
}
