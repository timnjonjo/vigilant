package com.turing.vigilant.scoring.rules;

import com.turing.vigilant.graph.ReferralEdge;
import com.turing.vigilant.scoring.RuleHit;
import com.turing.vigilant.scoring.ScoringRequest;
import com.turing.vigilant.scoring.ScoringRule;
import com.turing.vigilant.scoring.ScoringWeights;
import com.turing.vigilant.shared.ReasonCode;

import java.time.Instant;
import java.util.Optional;

/**
 * Flags a referrer whose fan-out within the rolling window is a statistical
 * outlier against the population baseline (z-score), with an absolute-count
 * fallback for cold start when the baseline has no usable spread yet.
 */
public class VelocityBurstRule implements ScoringRule {

    private final ScoringWeights weights;

    public VelocityBurstRule(ScoringWeights weights) {
        this.weights = weights;
    }

    @Override
    public Optional<RuleHit> evaluate(ScoringRequest request) {
        Instant windowStart = request.evaluatedAt().minus(weights.velocityWindow());
        long fanoutInWindow = request.neighbourhood().fanoutOfReferrer().stream()
                .map(ReferralEdge::createdAt)
                .filter(at -> !at.isBefore(windowStart) && !at.isAfter(request.evaluatedAt()))
                .count();

        double z = request.baseline().zScore(fanoutInWindow);
        boolean burst = z >= weights.velocityZThreshold()
                || fanoutInWindow >= weights.velocityAbsoluteThreshold();

        return burst
                ? Optional.of(new RuleHit(ReasonCode.VELOCITY_BURST, weights.velocityWeight()))
                : Optional.empty();
    }
}
