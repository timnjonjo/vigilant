package com.turing.vigilant.codes;

import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Default generator: tenant prefix plus a short random suffix. */
@Component
public class RandomReferralCodeGenerator implements ReferralCodeGenerator {

    @Override
    public ReferralCode generate(TenantId tenantId, String userId) {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return ReferralCode.of(tenantId.value().toUpperCase() + "-" + suffix);
    }
}
