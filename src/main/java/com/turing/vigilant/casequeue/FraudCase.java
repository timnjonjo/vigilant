package com.turing.vigilant.casequeue;

import com.turing.vigilant.shared.Decision;
import com.turing.vigilant.shared.ReasonCode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * A HOLD/REJECT case with its full audit trail (spec section 6). Opened by the
 * decisioning flow and later resolved by an analyst; every field that a review
 * decision depends on is captured for compliance.
 */
@Entity
@Table(name = "fraud_case")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FraudCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "campaign_id", nullable = false)
    private String campaignId;

    @Column(name = "referral_code", nullable = false)
    private String referralCode;

    @Column(name = "referee_user_id", nullable = false)
    private String refereeUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Decision decision;

    @Column(nullable = false)
    private double score;

    @Convert(converter = com.turing.vigilant.casequeue.ReasonCodesConverter.class)
    @Column(name = "reason_codes", nullable = false)
    private List<ReasonCode> reasonCodes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private com.turing.vigilant.casequeue.CaseStatus status;

    @Enumerated(EnumType.STRING)
    private Decision resolution;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public static FraudCase open(com.turing.vigilant.casequeue.CaseOpening opening) {
        FraudCase fraudCase = new FraudCase();
        fraudCase.tenantId = opening.tenantId().value();
        fraudCase.campaignId = opening.campaignId().value();
        fraudCase.referralCode = opening.referralCode().value();
        fraudCase.refereeUserId = opening.refereeUserId();
        fraudCase.decision = opening.decision();
        fraudCase.score = opening.score();
        fraudCase.reasonCodes = List.copyOf(opening.reasonCodes());
        fraudCase.status = com.turing.vigilant.casequeue.CaseStatus.OPEN;
        fraudCase.openedAt = opening.openedAt();
        return fraudCase;
    }

    /** Applies an analyst's resolution. The final action is APPROVE or REJECT. */
    public void resolve(Decision resolution, String resolvedBy, Instant resolvedAt) {
        if (status == com.turing.vigilant.casequeue.CaseStatus.RESOLVED) {
            throw new IllegalStateException("case " + id + " is already resolved");
        }
        if (resolution != Decision.APPROVE && resolution != Decision.REJECT) {
            throw new IllegalArgumentException("resolution must be APPROVE or REJECT");
        }
        this.resolution = resolution;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolvedAt;
        this.status = com.turing.vigilant.casequeue.CaseStatus.RESOLVED;
    }
}
