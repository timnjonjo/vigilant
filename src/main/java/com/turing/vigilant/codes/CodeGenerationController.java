package com.turing.vigilant.codes;

import com.turing.vigilant.codes.CodeIssuanceService.IssuedCode;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.TenantId;
import com.turing.vigilant.web.TenantAccessGuard;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /v1/codes/generate — spec section 7. Never returns DENIED. Host callers
 * authenticate with a Keycloak client-credentials token ({@code host_integration}
 * role); the token's {@code tenant_id} claim must match the request body.
 */
@RestController
public class CodeGenerationController {

    private final TenantAccessGuard tenantAccessGuard;
    private final CodeIssuanceService issuanceService;

    public CodeGenerationController(TenantAccessGuard tenantAccessGuard, CodeIssuanceService issuanceService) {
        this.tenantAccessGuard = tenantAccessGuard;
        this.issuanceService = issuanceService;
    }

    @PostMapping("/v1/codes/generate")
    GenerateCodeResponse generate(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody GenerateCodeRequest request) {
        tenantAccessGuard.requireAccess(request.tenantId(), jwt);
        IssuedCode issued = issuanceService.issue(
                TenantId.of(request.tenantId()),
                CampaignId.of(request.campaignId()),
                request.userId(),
                request.deviceId(),
                request.ipAddress());
        return new GenerateCodeResponse("ISSUED", issued.referralCode().value(), issued.riskFlag());
    }

    record GenerateCodeRequest(
            @NotBlank String tenantId,
            @NotBlank String campaignId,
            @NotBlank String userId,
            String deviceId,
            String ipAddress) {
    }

    record GenerateCodeResponse(String status, String referralCode, boolean riskFlag) {
    }
}
