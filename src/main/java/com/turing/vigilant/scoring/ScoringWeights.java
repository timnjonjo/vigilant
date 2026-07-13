package com.turing.vigilant.scoring;

import java.time.Duration;

/**
 * Tunable weights and thresholds for the rule-based scorer (v1). Reviewed cases
 * feed back into these over time (spec section 6). Weights are chosen so a single
 * strong signal lands in the HOLD band and two combine into REJECT.
 */
public record ScoringWeights(
        double velocityWeight,
        double deviceWeight,
        double ipWeight,
        double cycleWeight,
        double datacenterWeight,
        double velocityZThreshold,
        Duration velocityWindow,
        long velocityAbsoluteThreshold) {

    public static ScoringWeights defaults() {
        // A datacenter/VPN IP alone lands in HOLD (0.70 < reject 0.75); combined
        // with any second signal it tips into REJECT.
        return new ScoringWeights(0.40, 0.45, 0.30, 0.50, 0.70, 3.0, Duration.ofHours(24), 20);
    }
}
