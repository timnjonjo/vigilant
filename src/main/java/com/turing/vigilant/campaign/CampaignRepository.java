package com.turing.vigilant.campaign;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Postgres-backed campaign store. All lookups are tenant-scoped. */
public interface CampaignRepository extends JpaRepository<Campaign, String> {

    List<Campaign> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    Optional<Campaign> findByCampaignIdAndTenantId(String campaignId, String tenantId);
}
