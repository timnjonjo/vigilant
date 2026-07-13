package com.turing.vigilant.shared;

/**
 * The only enforcement outcomes Vigilant ever recommends, applied at the payout
 * decision point (never at signup). See spec section 6.
 */
public enum Decision {
    /** Low risk — pay the bonus immediately. */
    APPROVE,
    /** Medium risk — withhold pending async manual review. */
    HOLD,
    /** High risk — cancel the bonus, log a case for audit. */
    REJECT
}
