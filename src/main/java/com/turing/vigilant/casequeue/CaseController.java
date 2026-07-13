package com.turing.vigilant.casequeue;

import com.turing.vigilant.graph.GraphStore;
import com.turing.vigilant.graph.ReferralNeighbourhood;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.Decision;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;
import com.turing.vigilant.web.TenantAccessGuard;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Case-queue endpoints for the dashboard: list, fetch, subgraph, audit, resolve.
 * Behind Keycloak (spec §8) — role authorization is enforced by
 * {@link com.turing.vigilant.web.SecurityConfig}, and every request's tenant must
 * match the token's {@code tenant_id} claim ({@link TenantAccessGuard}) before any
 * data is read or written.
 */
@RestController
public class CaseController {

    private final TenantAccessGuard tenantAccessGuard;
    private final FraudCaseRepository repository;
    private final CaseResolutionService resolutionService;
    private final GraphStore graphStore;

    public CaseController(TenantAccessGuard tenantAccessGuard, FraudCaseRepository repository,
                          CaseResolutionService resolutionService, GraphStore graphStore) {
        this.tenantAccessGuard = tenantAccessGuard;
        this.repository = repository;
        this.resolutionService = resolutionService;
        this.graphStore = graphStore;
    }

    @GetMapping("/v1/cases")
    List<CaseView> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String tenantId,
            @RequestParam(required = false) CaseStatus status,
            @RequestParam(required = false) String campaignId) {
        tenantAccessGuard.requireAccess(tenantId, jwt);
        List<FraudCase> cases = (status == null)
                ? repository.findByTenantIdOrderByOpenedAtDesc(tenantId)
                : repository.findByTenantIdAndStatusOrderByOpenedAtDesc(tenantId, status);
        return cases.stream()
                .filter(c -> campaignId == null || campaignId.isBlank() || campaignId.equals(c.getCampaignId()))
                .map(CaseView::of)
                .toList();
    }

    @GetMapping("/v1/cases/{id}")
    CaseView get(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String tenantId,
            @PathVariable long id) {
        tenantAccessGuard.requireAccess(tenantId, jwt);
        return repository.findByIdAndTenantId(id, tenantId)
                .map(CaseView::of)
                .orElseThrow(() -> new CaseNotFoundException(id));
    }

    /** The flagged referral cluster for the graph explorer, pulled from Neo4j. */
    @GetMapping("/v1/cases/{id}/graph")
    CaseGraphView graph(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String tenantId,
            @PathVariable long id) {
        tenantAccessGuard.requireAccess(tenantId, jwt);
        FraudCase fraudCase = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new CaseNotFoundException(id));
        ReferralNeighbourhood neighbourhood = graphStore.loadNeighbourhood(
                TenantId.of(tenantId), ReferralCode.of(fraudCase.getReferralCode()),
                CampaignId.of(fraudCase.getCampaignId()));
        return CaseGraphView.of(neighbourhood);
    }

    /** Decision history for this case's referral code (synthesised, oldest first). */
    @GetMapping("/v1/cases/{id}/audit")
    List<AuditEntryView> audit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String tenantId,
            @PathVariable long id) {
        tenantAccessGuard.requireAccess(tenantId, jwt);
        FraudCase fraudCase = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new CaseNotFoundException(id));
        List<FraudCase> related = repository.findByTenantIdAndReferralCodeOrderByOpenedAtAsc(
                tenantId, fraudCase.getReferralCode());
        return AuditEntryView.forRelatedCases(related);
    }

    @PostMapping("/v1/cases/{id}/resolve")
    CaseView resolve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @RequestBody ResolveRequest request) {
        tenantAccessGuard.requireAccess(request.tenantId(), jwt);
        FraudCase resolved = resolutionService.resolve(
                TenantId.of(request.tenantId()), id, request.resolution(), request.resolvedBy());
        return CaseView.of(resolved);
    }

    record ResolveRequest(
            @NotBlank String tenantId,
            @NotNull Decision resolution,
            @NotBlank String resolvedBy) {
    }
}
