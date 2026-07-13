package com.turing.vigilant.codes;

import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;

/** Produces a referral code for a referrer. */
public interface ReferralCodeGenerator {

    ReferralCode generate(TenantId tenantId, String userId);
}
