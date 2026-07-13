package com.turing.vigilant.web;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * The single enforcement point for tenant isolation on the dashboard plane: the
 * request's tenant must equal the token's {@code tenant_id} claim. This is what
 * makes the per-query tenant scoping trustworthy rather than a filter a caller
 * could set to anything.
 */
@Component
public class TenantAccessGuard {

    static final String TENANT_CLAIM = "tenant_id";

    public void requireAccess(String requestedTenantId, Jwt jwt) {
        String tokenTenant = jwt.getClaimAsString(TENANT_CLAIM);
        if (tokenTenant == null || !tokenTenant.equals(requestedTenantId)) {
            throw new TenantAccessDeniedException(requestedTenantId);
        }
    }
}
