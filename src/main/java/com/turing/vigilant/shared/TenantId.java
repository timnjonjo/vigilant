package com.turing.vigilant.shared;

/**
 * Identifies the host client. Every node, edge, and query in Vigilant is scoped
 * by a {@code TenantId} from day one (spec section 7 auth).
 */
public record TenantId(String value) {

    public TenantId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
    }

    public static TenantId of(String value) {
        return new TenantId(value);
    }
}
