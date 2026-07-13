package com.turing.vigilant.tenant;

import com.turing.vigilant.shared.TenantId;

/**
 * Per-tenant configuration. {@code callbackUrl} is where HOLD-case resolutions
 * are POSTed back to the host (spec section 7). Inbound authentication is handled
 * by Keycloak (a per-tenant {@code tenant_id} claim), not by this record.
 */
public record TenantConfig(TenantId id, String callbackUrl) {

    public TenantConfig {
        if (id == null) {
            throw new IllegalArgumentException("tenant id must not be null");
        }
    }
}
