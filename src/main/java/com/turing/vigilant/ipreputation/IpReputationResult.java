package com.turing.vigilant.ipreputation;

import com.turing.vigilant.shared.IpType;

/**
 * Outcome of an IP reputation check: the classification, a risk contribution in
 * [0.0, 1.0], and the source that produced it (for audit — {@code "local-asn"}
 * for Tier 1, distinct from a future external Tier 2 checker).
 */
public record IpReputationResult(IpType type, double riskScore, String source) {
}
