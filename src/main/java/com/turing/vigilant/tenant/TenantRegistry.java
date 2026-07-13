package com.turing.vigilant.tenant;

import com.turing.vigilant.shared.TenantId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory lookup of onboarded tenants, seeded from configuration. Resolves a
 * {@link TenantId} to its config (e.g. the webhook callback URL). Inbound
 * authentication is handled upstream by Keycloak, not here.
 */
public class TenantRegistry {

    private final Map<String, TenantConfig> byId = new LinkedHashMap<>();

    public TenantRegistry(Collection<TenantConfig> tenants) {
        for (TenantConfig tenant : tenants) {
            byId.put(tenant.id().value(), tenant);
        }
    }

    public Optional<TenantConfig> find(TenantId tenantId) {
        return Optional.ofNullable(byId.get(tenantId.value()));
    }

    public TenantConfig require(TenantId tenantId) {
        return find(tenantId).orElseThrow(() -> new com.turing.vigilant.tenant.UnknownTenantException(tenantId));
    }
}
