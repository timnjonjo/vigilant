package com.turing.vigilant.events;

import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;
import com.turing.vigilant.web.TenantAccessGuard;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * POST /v1/events/redemption and /v1/events/conversion — spec section 7.
 * Fire-and-forget ingestion; redemption always ACCEPTs. Host callers authenticate
 * with a Keycloak client-credentials token ({@code host_integration} role); the
 * token's {@code tenant_id} claim must match the request body.
 */
@RestController
public class EventController {

    private final TenantAccessGuard tenantAccessGuard;
    private final com.turing.vigilant.events.EventIngestionService ingestionService;

    public EventController(TenantAccessGuard tenantAccessGuard, com.turing.vigilant.events.EventIngestionService ingestionService) {
        this.tenantAccessGuard = tenantAccessGuard;
        this.ingestionService = ingestionService;
    }

    @PostMapping("/v1/events/redemption")
    RedemptionResponse redemption(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody RedemptionRequest request) {
        tenantAccessGuard.requireAccess(request.tenantId(), jwt);
        ingestionService.recordRedemption(
                TenantId.of(request.tenantId()),
                CampaignId.of(request.campaignId()),
                ReferralCode.of(request.referralCode()),
                request.newUserId(),
                request.deviceId(),
                request.ipAddress(),
                request.timestamp());
        return new RedemptionResponse("ACCEPT");
    }

    @PostMapping("/v1/events/conversion")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void conversion(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ConversionRequest request) {
        tenantAccessGuard.requireAccess(request.tenantId(), jwt);
        ingestionService.recordConversion(
                TenantId.of(request.tenantId()),
                CampaignId.of(request.campaignId()),
                ReferralCode.of(request.referralCode()),
                request.refereeUserId(),
                request.conversionType(),
                request.timestamp());
    }

    record RedemptionRequest(
            @NotBlank String tenantId,
            @NotBlank String campaignId,
            @NotBlank String referralCode,
            @NotBlank String newUserId,
            String deviceId,
            String ipAddress,
            Instant timestamp) {
    }

    record RedemptionResponse(String action) {
    }

    record ConversionRequest(
            @NotBlank String tenantId,
            @NotBlank String campaignId,
            @NotBlank String referralCode,
            @NotBlank String refereeUserId,
            String conversionType,
            Instant timestamp) {
    }
}
