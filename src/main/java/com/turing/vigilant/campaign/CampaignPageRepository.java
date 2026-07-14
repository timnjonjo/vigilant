package com.turing.vigilant.campaign;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Tenant-scoped campaign keyset query; never uses OFFSET or COUNT. */
@Repository
public class CampaignPageRepository {

    private final EntityManager entityManager;

    public CampaignPageRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Campaign> findPage(
            String tenantId, Instant cursorCreatedAt, String cursorCampaignId, int fetchSize) {
        StringBuilder sql = new StringBuilder("SELECT c.* FROM campaign c WHERE c.tenant_id = :tenantId");
        if (cursorCampaignId != null) {
            sql.append(" AND (c.created_at, c.campaign_id) < (:cursorCreatedAt, :cursorCampaignId)");
        }
        sql.append(" ORDER BY c.created_at DESC, c.campaign_id DESC");
        Query query = entityManager.createNativeQuery(sql.toString(), Campaign.class)
                .setParameter("tenantId", tenantId)
                .setMaxResults(fetchSize);
        if (cursorCampaignId != null) {
            query.setParameter("cursorCreatedAt", cursorCreatedAt);
            query.setParameter("cursorCampaignId", cursorCampaignId);
        }
        return query.getResultList();
    }
}
