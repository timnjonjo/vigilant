package com.turing.vigilant.decisions;

import com.turing.vigilant.shared.Decision;
import com.turing.vigilant.shared.ScoreBands;

/**
 * Applies the tenant's score bands at decision time. A plain class (not the
 * {@link ScoreBands} value record) so it can be a Spring bean — module beans are
 * proxied for observability, and records cannot be subclassed.
 */
public class DecisionPolicy {

    private final ScoreBands scoreBands;

    public DecisionPolicy(ScoreBands scoreBands) {
        this.scoreBands = scoreBands;
    }

    public Decision classify(double score) {
        return scoreBands.classify(score);
    }
}
