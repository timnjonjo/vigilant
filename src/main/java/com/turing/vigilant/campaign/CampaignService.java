package com.turing.vigilant.campaign;

import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.TenantId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Campaign lifecycle + the read-side validation the ingestion/decision paths use
 * to keep referral activity tied to a real, tenant-owned campaign (spec §10a).
 * Vigilant owns campaigns; every operation is tenant-scoped.
 */
@Service
public class CampaignService {

    private final CampaignRepository repository;
    private final Clock clock;

    public CampaignService(CampaignRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Campaign create(TenantId tenantId, String name, BigDecimal bonusAmount,
                           LocalDate startDate, LocalDate endDate, CampaignStatus status,
                           ConversionCriteria conversionCriteria, Integer referralCapPerUser) {
        Campaign campaign = Campaign.create(tenantId.value(), name, bonusAmount, startDate, endDate,
                status, conversionCriteria, referralCapPerUser, clock.instant());
        return repository.save(campaign);
    }

    @Transactional
    public Campaign update(TenantId tenantId, CampaignId campaignId, String name, BigDecimal bonusAmount,
                           LocalDate startDate, LocalDate endDate, CampaignStatus status,
                           ConversionCriteria conversionCriteria, Integer referralCapPerUser) {
        Campaign campaign = requireCampaign(tenantId, campaignId);
        campaign.applyUpdate(name, bonusAmount, startDate, endDate, status, conversionCriteria,
                referralCapPerUser, clock.instant());
        return repository.save(campaign);
    }

    @Transactional(readOnly = true)
    public List<Campaign> list(TenantId tenantId) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId.value());
    }

    /** Fetch a tenant's campaign, or throw {@link CampaignNotFoundException}. */
    @Transactional(readOnly = true)
    public Campaign requireCampaign(TenantId tenantId, CampaignId campaignId) {
        return repository.findByCampaignIdAndTenantId(campaignId.value(), tenantId.value())
                .orElseThrow(() -> new CampaignNotFoundException(campaignId));
    }

    /**
     * Guards code issuance: the campaign must exist, be the tenant's, and be
     * ACTIVE (spec §10a). Codes are never issued against a draft/paused/ended
     * campaign.
     */
    @Transactional(readOnly = true)
    public Campaign requireActiveCampaign(TenantId tenantId, CampaignId campaignId) {
        Campaign campaign = requireCampaign(tenantId, campaignId);
        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            throw new CampaignNotActiveException(campaignId, campaign.getStatus());
        }
        return campaign;
    }
}
