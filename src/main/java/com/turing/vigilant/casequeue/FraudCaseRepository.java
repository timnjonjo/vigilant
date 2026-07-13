package com.turing.vigilant.casequeue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Case queue persistence. All lookups are tenant-scoped. */
public interface FraudCaseRepository extends JpaRepository<FraudCase, Long> {

    List<FraudCase> findByTenantIdOrderByOpenedAtDesc(String tenantId);

    List<FraudCase> findByTenantIdAndStatusOrderByOpenedAtDesc(String tenantId, CaseStatus status);

    List<FraudCase> findByTenantIdAndReferralCodeOrderByOpenedAtAsc(String tenantId, String referralCode);

    Optional<FraudCase> findByIdAndTenantId(Long id, String tenantId);
}
