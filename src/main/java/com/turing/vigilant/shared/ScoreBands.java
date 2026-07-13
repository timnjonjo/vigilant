package com.turing.vigilant.shared;

/**
 * Maps a normalised risk score in [0.0, 1.0] onto a graduated {@link Decision}
 * (spec section 6). Thresholds are tenant-tunable so reviewed cases can feed
 * back into recalibration over time.
 *
 * <p>{@code score < holdThreshold} → APPROVE;
 * {@code holdThreshold <= score < rejectThreshold} → HOLD;
 * {@code score >= rejectThreshold} → REJECT.
 */
public record ScoreBands(double holdThreshold, double rejectThreshold) {

    public ScoreBands {
        if (holdThreshold < 0.0 || rejectThreshold > 1.0) {
            throw new IllegalArgumentException("thresholds must lie within [0.0, 1.0]");
        }
        if (holdThreshold >= rejectThreshold) {
            throw new IllegalArgumentException("holdThreshold must be strictly below rejectThreshold");
        }
    }

    public Decision classify(double score) {
        if (score >= rejectThreshold) {
            return Decision.REJECT;
        }
        if (score >= holdThreshold) {
            return Decision.HOLD;
        }
        return Decision.APPROVE;
    }
}
