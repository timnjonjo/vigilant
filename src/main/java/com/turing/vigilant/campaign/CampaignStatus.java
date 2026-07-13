package com.turing.vigilant.campaign;

/**
 * Lifecycle of a campaign (spec section 10a). Only {@code ACTIVE} campaigns may
 * have new referral codes issued against them.
 */
public enum CampaignStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    ENDED
}
