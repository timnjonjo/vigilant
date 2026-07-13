package com.turing.vigilant.campaign;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A tenant campaign (spec section 10a). Relational config that drives scoring
 * baselines and the payout the host owes; Vigilant owns creation/management.
 * Referral codes, REFERRED edges, and cases are all scoped to a campaign.
 */
@Entity
@Table(name = "campaign")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Campaign {

    @Id
    @Column(name = "campaign_id")
    private String campaignId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "bonus_amount", nullable = false)
    private BigDecimal bonusAmount;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "conversion_criteria", nullable = false)
    private ConversionCriteria conversionCriteria;

    @Column(name = "referral_cap_per_user")
    private Integer referralCapPerUser;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Campaign create(String tenantId, String name, BigDecimal bonusAmount,
                                  LocalDate startDate, LocalDate endDate, CampaignStatus status,
                                  ConversionCriteria conversionCriteria, Integer referralCapPerUser,
                                  Instant now) {
        Campaign c = new Campaign();
        c.campaignId = UUID.randomUUID().toString();
        c.tenantId = tenantId;
        c.name = name;
        c.bonusAmount = bonusAmount;
        c.startDate = startDate;
        c.endDate = endDate;
        c.status = status != null ? status : CampaignStatus.DRAFT;
        c.conversionCriteria = conversionCriteria;
        c.referralCapPerUser = referralCapPerUser;
        c.createdAt = now;
        c.updatedAt = now;
        return c;
    }

    /** Partial update (PATCH semantics): only non-null fields are applied. */
    public void applyUpdate(String name, BigDecimal bonusAmount, LocalDate startDate, LocalDate endDate,
                            CampaignStatus status, ConversionCriteria conversionCriteria,
                            Integer referralCapPerUser, Instant now) {
        if (name != null) this.name = name;
        if (bonusAmount != null) this.bonusAmount = bonusAmount;
        if (startDate != null) this.startDate = startDate;
        if (endDate != null) this.endDate = endDate;
        if (status != null) this.status = status;
        if (conversionCriteria != null) this.conversionCriteria = conversionCriteria;
        if (referralCapPerUser != null) this.referralCapPerUser = referralCapPerUser;
        this.updatedAt = now;
    }
}
