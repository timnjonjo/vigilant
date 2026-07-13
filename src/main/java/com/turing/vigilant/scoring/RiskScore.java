package com.turing.vigilant.scoring;

import com.turing.vigilant.shared.ReasonCode;

import java.util.List;

/**
 * The output of a scoring pass: a normalised risk value in [0.0, 1.0] and the
 * explicit reason codes that produced it (spec section 6 — auditable by design).
 */
public record RiskScore(double value, List<ReasonCode> reasonCodes) {

    public RiskScore {
        reasonCodes = List.copyOf(reasonCodes);
    }
}
