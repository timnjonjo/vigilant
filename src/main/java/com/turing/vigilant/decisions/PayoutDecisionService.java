package com.turing.vigilant.decisions;

import com.turing.vigilant.campaign.CampaignService;
import com.turing.vigilant.casequeue.CaseOpening;
import com.turing.vigilant.casequeue.CaseRecorder;
import com.turing.vigilant.graph.GraphStore;
import com.turing.vigilant.graph.ReferralNeighbourhood;
import com.turing.vigilant.scoring.RiskScore;
import com.turing.vigilant.scoring.RuleBasedScorer;
import com.turing.vigilant.scoring.ScoringRequest;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.Decision;
import com.turing.vigilant.shared.ReasonCode;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Runs the full scoring pass at payout time and returns a graduated decision.
 * HOLD/REJECT outcomes are logged as cases for audit and (for HOLD) later async
 * review — enforcement gates the money, never the user (spec sections 2, 6).
 */
@Service
public class PayoutDecisionService {

    private final GraphStore graphStore;
    private final RuleBasedScorer scorer;
    private final DecisionPolicy decisionPolicy;
    private final CaseRecorder caseRecorder;
    private final CampaignService campaignService;
    private final Clock clock;

    public PayoutDecisionService(GraphStore graphStore, RuleBasedScorer scorer,
                                 DecisionPolicy decisionPolicy, CaseRecorder caseRecorder,
                                 CampaignService campaignService, Clock clock) {
        this.graphStore = graphStore;
        this.scorer = scorer;
        this.decisionPolicy = decisionPolicy;
        this.caseRecorder = caseRecorder;
        this.campaignService = campaignService;
        this.clock = clock;
    }

    public PayoutDecision decide(TenantId tenantId, CampaignId campaignId,
                                 ReferralCode referralCode, String refereeUserId) {
        // The campaign must exist and belong to this tenant (may be paused/ended —
        // a payout for a code issued during the campaign is still checked).
        campaignService.requireCampaign(tenantId, campaignId);

        Instant now = clock.instant();
        // Neighbourhood + baseline are scoped to the campaign (spec §10a).
        ReferralNeighbourhood neighbourhood = graphStore.loadNeighbourhood(tenantId, referralCode, campaignId);
        Instant windowStart = now.minus(scorer.velocityWindow());
        RiskScore score = scorer.score(
                new ScoringRequest(
                        neighbourhood,
                        graphStore.fanoutBaseline(tenantId, campaignId, windowStart, now),
                        now));
        Decision action = decisionPolicy.classify(score.value());

        Long caseId = null;
        if (action != Decision.APPROVE) {
            caseId = caseRecorder.record(new CaseOpening(
                    tenantId, campaignId, referralCode, refereeUserId, action,
                    score.value(), score.reasonCodes(), now));
        }
        return new PayoutDecision(action, score.value(), score.reasonCodes(), caseId);
    }

    public record PayoutDecision(Decision action, double score, List<ReasonCode> reasonCodes, Long caseId) {
    }
}
