package com.turing.vigilant.campaign;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** API projection of a {@link Campaign}. */
public record CampaignView(
        String campaignId,
        String tenantId,
        String name,
        BigDecimal bonusAmount,
        LocalDate startDate,
        LocalDate endDate,
        CampaignStatus status,
        ConversionCriteria conversionCriteria,
        Integer referralCapPerUser,
        Instant createdAt,
        Instant updatedAt) {

    public static CampaignView of(Campaign c) {
        return new CampaignView(
                c.getCampaignId(), c.getTenantId(), c.getName(), c.getBonusAmount(),
                c.getStartDate(), c.getEndDate(), c.getStatus(), c.getConversionCriteria(),
                c.getReferralCapPerUser(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
