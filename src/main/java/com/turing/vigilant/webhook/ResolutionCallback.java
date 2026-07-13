package com.turing.vigilant.webhook;

import com.turing.vigilant.shared.Decision;

/**
 * Payload POSTed back to the host when a HOLD case is resolved (spec section 7).
 * {@code finalAction} is always APPROVE or REJECT.
 */
public record ResolutionCallback(
        String referralCode,
        String refereeUserId,
        Decision finalAction,
        long caseId) {
}
