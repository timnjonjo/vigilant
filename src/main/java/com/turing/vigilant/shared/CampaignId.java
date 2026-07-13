package com.turing.vigilant.shared;

/**
 * Identifies a tenant campaign. Server-generated and opaque — referral codes,
 * REFERRED edges, and cases are all scoped to a campaign (spec section 10a).
 */
public record CampaignId(String value) {

    public CampaignId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("campaignId must not be blank");
        }
    }

    public static CampaignId of(String value) {
        return new CampaignId(value);
    }
}
