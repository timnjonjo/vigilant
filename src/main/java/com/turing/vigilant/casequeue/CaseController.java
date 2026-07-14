package com.turing.vigilant.casequeue;

import com.turing.vigilant.graph.GraphStore;
import com.turing.vigilant.graph.ReferralNeighbourhood;
import com.turing.vigilant.graph.SharedAttributeType;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.Decision;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.ReasonCode;
import com.turing.vigilant.shared.TenantId;
import com.turing.vigilant.web.TenantAccessGuard;
import com.turing.vigilant.web.pagination.CursorPage;
import com.turing.vigilant.web.pagination.PageLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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
    private final CasePageService casePageService;
    private final AuditPageService auditPageService;
    private final CaseResolutionService resolutionService;
    private final GraphStore graphStore;

    public CaseController(TenantAccessGuard tenantAccessGuard, FraudCaseRepository repository,
                          CasePageService casePageService,
                          AuditPageService auditPageService,
                          CaseResolutionService resolutionService, GraphStore graphStore) {
        this.tenantAccessGuard = tenantAccessGuard;
        this.repository = repository;
        this.casePageService = casePageService;
        this.auditPageService = auditPageService;
        this.resolutionService = resolutionService;
        this.graphStore = graphStore;
    }

    @GetMapping("/v1/cases")
    CursorPage<CaseView> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String tenantId,
            @RequestParam(required = false) CaseStatus status,
            @RequestParam(required = false) String campaignId,
            @RequestParam(required = false) ReasonCode reasonCode,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "score") String sortBy,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "25") int limit) {
        tenantAccessGuard.requireAccess(tenantId, jwt);
        return casePageService.page(
                tenantId, status, campaignId, reasonCode, search, sortBy, cursor,
                PageLimits.requireValid(limit));
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

    /**
     * The flagged referral cluster for the graph explorer, pulled from Neo4j via a
     * bounded visualization query. Only the overlap edge types the case actually
     * flagged are fetched (device edges for {@code DEVICE_COLLISION}, subnet edges
     * for {@code IP_SUBNET_COLLISION}); a velocity/cycle/datacenter-only case draws
     * neither. Scoring is unaffected — it uses the full neighbourhood.
     */
    @GetMapping("/v1/cases/{id}/graph")
    CaseGraphView graph(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String tenantId,
            @PathVariable long id) {
        tenantAccessGuard.requireAccess(tenantId, jwt);
        FraudCase fraudCase = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new CaseNotFoundException(id));
        ReferralNeighbourhood neighbourhood = graphStore.loadCaseVisualization(
                TenantId.of(tenantId), ReferralCode.of(fraudCase.getReferralCode()),
                CampaignId.of(fraudCase.getCampaignId()),
                visualSharedEdges(fraudCase.getReasonCodes()));
        return CaseGraphView.of(neighbourhood);
    }

    /**
     * The shared-attribute edge types to draw for a case: only the collision type
     * the scorer flagged, so the explorer shows the evidence behind the reason
     * codes and nothing more. Velocity-, cycle- or datacenter-only cases map to an
     * empty set (no overlap edges fetched).
     */
    static Set<SharedAttributeType> visualSharedEdges(List<ReasonCode> reasonCodes) {
        Set<SharedAttributeType> types = EnumSet.noneOf(SharedAttributeType.class);
        for (ReasonCode reasonCode : reasonCodes) {
            switch (reasonCode) {
                case DEVICE_COLLISION -> types.add(SharedAttributeType.DEVICE);
                case IP_SUBNET_COLLISION -> types.add(SharedAttributeType.IP_SUBNET);
                default -> { /* velocity / cycle / datacenter: no overlap edges */ }
            }
        }
        return types;
    }

    /** Decision history for this case's referral code (synthesised, oldest first). */
    @GetMapping("/v1/cases/{id}/audit")
    CursorPage<AuditEntryView> audit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String tenantId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "25") int limit,
            @PathVariable long id) {
        tenantAccessGuard.requireAccess(tenantId, jwt);
        FraudCase fraudCase = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new CaseNotFoundException(id));
        return auditPageService.page(
                tenantId, id, fraudCase.getReferralCode(), cursor,
                PageLimits.requireValid(limit));
    }

    @PostMapping("/v1/cases/{id}/resolve")
    CaseView resolve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @Valid @RequestBody ResolveRequest request) {
        tenantAccessGuard.requireAccess(request.tenantId(), jwt);
        FraudCase resolved = resolutionService.resolve(
                TenantId.of(request.tenantId()), id, request.resolution(), authenticatedActor(jwt));
        return CaseView.of(resolved);
    }

    private static String authenticatedActor(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        if (username != null && !username.isBlank()) {
            return username;
        }
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("authenticated token has no actor identity");
        }
        return subject;
    }

    record ResolveRequest(
            @NotBlank @Size(max = 255) String tenantId,
            @NotNull Decision resolution) {
    }
}
