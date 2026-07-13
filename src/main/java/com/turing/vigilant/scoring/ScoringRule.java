package com.turing.vigilant.scoring;

import java.util.Optional;

/** A single interpretable rule. Returns a {@link RuleHit} when it fires. */
public interface ScoringRule {

    Optional<RuleHit> evaluate(com.turing.vigilant.scoring.ScoringRequest request);
}
