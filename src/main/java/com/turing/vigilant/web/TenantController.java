package com.turing.vigilant.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The tenants the caller may see. Scoped to the token's own {@code tenant_id} —
 * an analyst never learns other tenants exist. (The frontend tenant switcher is
 * a mock-mode convenience; in real mode the tenant is fixed by the token.)
 */
@RestController
public class TenantController {

    @GetMapping("/v1/tenants")
    List<TenantView> tenants(@AuthenticationPrincipal Jwt jwt) {
        String tenantId = jwt.getClaimAsString("tenant_id");
        return (tenantId == null || tenantId.isBlank())
                ? List.of()
                : List.of(new TenantView(tenantId, tenantId));
    }

    record TenantView(String id, String name) {
    }
}
