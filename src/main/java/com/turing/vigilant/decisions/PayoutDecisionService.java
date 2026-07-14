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
import com.turing.vigilant.shared.DomainEvent;
import com.turing.vigilant.shared.TenantId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(PayoutDecisionService.class);

    private final GraphStore graphStore;
    private final RuleBasedScorer scorer;
    private final DecisionPolicy decisionPolicy;
    private final CaseRecorder caseRecorder;
    private final CampaignService campaignService;
    private final FanoutBaselineCache fanoutBaselineCache;
    private final Clock clock;

    public PayoutDecisionService(GraphStore graphStore, RuleBasedScorer scorer,
                                 DecisionPolicy decisionPolicy, CaseRecorder caseRecorder,
                                 CampaignService campaignService, FanoutBaselineCache fanoutBaselineCache,
                                 Clock clock) {
        this.graphStore = graphStore;
        this.scorer = scorer;
        this.decisionPolicy = decisionPolicy;
        this.caseRecorder = caseRecorder;
        this.campaignService = campaignService;
        this.fanoutBaselineCache = fanoutBaselineCache;
        this.clock = clock;
    }

    public PayoutDecision decide(TenantId tenantId, CampaignId campaignId,
                                 ReferralCode referralCode, String refereeUserId) {
        // The campaign must exist and belong to this tenant (may be paused/ended —
        // a payout for a code issued during the campaign is still checked).
        campaignService.requireCampaign(tenantId, campaignId);

        // An empty or unrelated neighbourhood scores zero. Verify the complete
        // payout subject first so unknown codes, cross-campaign codes, wrong
        // referees, and unconverted referrals cannot fail open to APPROVE.
        if (!graphStore.convertedReferralExists(tenantId, campaignId, referralCode, refereeUserId)) {
            throw new PayoutNotEligibleException();
        }

        Instant now = clock.instant();
        // Neighbourhood + baseline are scoped to the campaign (spec §10a). The
        // baseline is the same for every payout-check on the campaign, so it's
        // memoised (short TTL) rather than re-scanned per request — see
        // FanoutBaselineCache.
        ReferralNeighbourhood neighbourhood = graphStore.loadNeighbourhood(tenantId, referralCode, campaignId);
        RiskScore score = scorer.score(
                new ScoringRequest(
                        neighbourhood,
                        fanoutBaselineCache.get(tenantId, campaignId, scorer.velocityWindow()),
                        now));
        Decision action = decisionPolicy.classify(score.value());

        Long caseId = null;
        if (action != Decision.APPROVE) {
            caseId = caseRecorder.record(new CaseOpening(
                    tenantId, campaignId, referralCode, refereeUserId, action,
                    score.value(), score.reasonCodes(), now));
        }
        // The auditable record of every payout check. Carries the reason codes and
        // the opened caseId (null on APPROVE) so a decision can be traced from the
        // request id straight to its case. The refereeUserId is PII and is not
        // logged — the caseId links to it in Postgres when a review needs it.
        DomainEvent.of(log, "payout_decision")
                .field("tenantId", tenantId.value())
                .field("campaignId", campaignId.value())
                .field("referralCode", referralCode.value())
                .field("action", action)
                .field("score", score.value())
                .field("reasonCodes", score.reasonCodes())
                .field("caseId", caseId)
                .log();
        return new PayoutDecision(action, score.value(), score.reasonCodes(), caseId);
    }

    public record PayoutDecision(Decision action, double score, List<ReasonCode> reasonCodes, Long caseId) {
    }
}
