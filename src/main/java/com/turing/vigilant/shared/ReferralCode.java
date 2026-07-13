package com.turing.vigilant.shared;

/**
 * A referral code issued to a referrer. Unique within a tenant.
 */
public record ReferralCode(String value) {

    public ReferralCode {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("referralCode must not be blank");
        }
    }

    public static ReferralCode of(String value) {
        return new ReferralCode(value);
    }
}
