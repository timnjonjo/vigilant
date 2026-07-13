package com.turing.vigilant.tenant;

import com.turing.vigilant.shared.TenantId;

/** Raised when a request references a tenant that is not onboarded. */
public class UnknownTenantException extends RuntimeException {

    public UnknownTenantException(TenantId tenantId) {
        super("unknown tenant: " + tenantId.value());
    }
}
