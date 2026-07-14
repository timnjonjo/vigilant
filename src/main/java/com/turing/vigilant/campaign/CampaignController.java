package com.turing.vigilant.campaign;

import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.TenantId;
import com.turing.vigilant.web.TenantAccessGuard;
import com.turing.vigilant.web.pagination.CursorPage;
import com.turing.vigilant.web.pagination.PageLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Campaign management (spec §10a). Writes (POST/PATCH) are gated to
 * {@code tenant_admin} in {@link com.turing.vigilant.web.SecurityConfig}; reads
 * (GET) are open to any authenticated caller (analysts/ops need them to populate
 * the campaign filter; host systems use the list for the pull model). Every
 * operation is tenant-scoped via {@link TenantAccessGuard}.
 */
@RestController
public class CampaignController {

    private final CampaignService campaignService;
    private final CampaignPageService campaignPageService;
    private final TenantAccessGuard tenantAccessGuard;

    public CampaignController(CampaignService campaignService,
                              CampaignPageService campaignPageService,
                              TenantAccessGuard tenantAccessGuard) {
        this.campaignService = campaignService;
        this.campaignPageService = campaignPageService;
        this.tenantAccessGuard = tenantAccessGuard;
    }

    @PostMapping("/v1/campaigns")
    @ResponseStatus(HttpStatus.CREATED)
    CampaignView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateCampaignRequest request) {
        tenantAccessGuard.requireAccess(request.tenantId(), jwt);
        Campaign campaign = campaignService.create(
                TenantId.of(request.tenantId()), request.name(), request.bonusAmount(),
                request.startDate(), request.endDate(), request.status(),
                request.conversionCriteria(), request.referralCapPerUser());
        return CampaignView.of(campaign);
    }

    @GetMapping("/v1/campaigns")
    CursorPage<CampaignView> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String tenantId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "25") int limit) {
        tenantAccessGuard.requireAccess(tenantId, jwt);
        return campaignPageService.page(
                TenantId.of(tenantId), cursor, PageLimits.requireValid(limit));
    }

    @GetMapping("/v1/campaigns/{campaignId}")
    CampaignView get(@AuthenticationPrincipal Jwt jwt,
                     @RequestParam String tenantId,
                     @PathVariable String campaignId) {
        tenantAccessGuard.requireAccess(tenantId, jwt);
        return CampaignView.of(campaignService.requireCampaign(TenantId.of(tenantId), CampaignId.of(campaignId)));
    }

    @PatchMapping("/v1/campaigns/{campaignId}")
    CampaignView update(@AuthenticationPrincipal Jwt jwt,
                        @PathVariable String campaignId,
                        @Valid @RequestBody UpdateCampaignRequest request) {
        tenantAccessGuard.requireAccess(request.tenantId(), jwt);
        Campaign campaign = campaignService.update(
                TenantId.of(request.tenantId()), CampaignId.of(campaignId), request.name(),
                request.bonusAmount(), request.startDate(), request.endDate(), request.status(),
                request.conversionCriteria(), request.referralCapPerUser());
        return CampaignView.of(campaign);
    }

    record CreateCampaignRequest(
            @NotBlank @Size(max = 255) String tenantId,
            @NotBlank @Size(max = 255) String name,
            @NotNull @PositiveOrZero BigDecimal bonusAmount,
            LocalDate startDate,
            LocalDate endDate,
            CampaignStatus status,
            @NotNull ConversionCriteria conversionCriteria,
            Integer referralCapPerUser) {
    }

    /** All fields except tenantId are optional — PATCH applies only what's present. */
    record UpdateCampaignRequest(
            @NotBlank @Size(max = 255) String tenantId,
            @Size(max = 255) String name,
            BigDecimal bonusAmount,
            LocalDate startDate,
            LocalDate endDate,
            CampaignStatus status,
            ConversionCriteria conversionCriteria,
            Integer referralCapPerUser) {
    }
}
