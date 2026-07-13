package com.turing.vigilant.campaign;

/**
 * What counts as a qualifying conversion for a campaign (spec section 10a) —
 * per campaign, not global. Extensible; the host system decides what to pay out.
 */
public enum ConversionCriteria {
    FIRST_DEPOSIT,
    N_DAY_RETENTION
}
