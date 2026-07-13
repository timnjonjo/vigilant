package com.turing.vigilant.graph;

/**
 * Rolling population baseline of per-referrer fan-out, used to compute a z-score
 * for the velocity/burst rule rather than a fixed threshold (spec section 5).
 */
public record FanoutBaseline(double mean, double standardDeviation, long sampleSize) {

    public static FanoutBaseline empty() {
        return new FanoutBaseline(0.0, 0.0, 0);
    }

    /** z-score of an observed fan-out; 0 when there is no usable spread yet. */
    public double zScore(long observedFanout) {
        if (standardDeviation <= 0.0) {
            return 0.0;
        }
        return (observedFanout - mean) / standardDeviation;
    }
}
