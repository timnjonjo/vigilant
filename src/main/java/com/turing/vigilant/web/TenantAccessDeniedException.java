package com.turing.vigilant.web;

/**
 * Raised when a valid token's tenant claim does not match the tenant being
 * queried — even a genuine analyst role for a different tenant is rejected.
 */
public class TenantAccessDeniedException extends RuntimeException {

    public TenantAccessDeniedException(String requestedTenantId) {
        super("Token is not authorized for tenant '" + requestedTenantId + "'");
    }
}
