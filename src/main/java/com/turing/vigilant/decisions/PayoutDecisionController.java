package com.turing.vigilant.decisions;

import com.turing.vigilant.decisions.PayoutDecisionService.PayoutDecision;
import com.turing.vigilant.shared.Decision;
import com.turing.vigilant.shared.ReasonCode;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.TenantId;
import com.turing.vigilant.web.TenantAccessGuard;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * POST /v1/decisions/payout-check — spec section 7. Host calls before releasing
 * funds, authenticating with a Keycloak client-credentials token
 * ({@code host_integration} role); the token's {@code tenant_id} claim must match
 * the request body.
 */
@RestController
public class PayoutDecisionController {

    private final TenantAccessGuard tenantAccessGuard;
    private final PayoutDecisionService decisionService;

    public PayoutDecisionController(TenantAccessGuard tenantAccessGuard, PayoutDecisionService decisionService) {
        this.tenantAccessGuard = tenantAccessGuard;
        this.decisionService = decisionService;
    }

    @PostMapping("/v1/decisions/payout-check")
    PayoutCheckResponse payoutCheck(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody PayoutCheckRequest request) {
        tenantAccessGuard.requireAccess(request.tenantId(), jwt);
        PayoutDecision decision = decisionService.decide(
                TenantId.of(request.tenantId()),
                CampaignId.of(request.campaignId()),
                ReferralCode.of(request.referralCode()),
                request.refereeUserId());
        return new PayoutCheckResponse(
                decision.action(), decision.score(), decision.reasonCodes(), decision.caseId());
    }

    record PayoutCheckRequest(
            @NotBlank String tenantId,
            @NotBlank String campaignId,
            @NotBlank String referralCode,
            @NotBlank String refereeUserId) {
    }

    record PayoutCheckResponse(Decision action, double score, List<ReasonCode> reasonCodes, Long caseId) {
    }
}
