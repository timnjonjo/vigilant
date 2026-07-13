package com.turing.vigilant.casequeue;

import com.turing.vigilant.web.TenantAccessGuard;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Aggregate monitoring for the ops view. Tenant-scoped like the case endpoints. */
@RestController
public class MonitoringController {

    private final TenantAccessGuard tenantAccessGuard;
    private final MonitoringService monitoringService;

    public MonitoringController(TenantAccessGuard tenantAccessGuard, MonitoringService monitoringService) {
        this.tenantAccessGuard = tenantAccessGuard;
        this.monitoringService = monitoringService;
    }

    @GetMapping("/v1/monitoring")
    MonitoringView monitoring(@AuthenticationPrincipal Jwt jwt,
                              @RequestParam String tenantId,
                              @RequestParam(required = false) String campaignId) {
        tenantAccessGuard.requireAccess(tenantId, jwt);
        return monitoringService.summarise(tenantId, campaignId);
    }
}
