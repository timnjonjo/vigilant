package com.turing.vigilant.casequeue;

import com.turing.vigilant.shared.ReasonCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Parameterized keyset queries for the analyst case queue; never uses OFFSET. */
@Repository
public class CasePageRepository {

    private final EntityManager entityManager;

    public CasePageRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<FraudCase> findPage(
            String tenantId, CaseStatus status, String campaignId, ReasonCode reasonCode,
            String search, CaseSort sort, Double cursorScore, Instant cursorOpenedAt, Long cursorId,
            int fetchSize) {
        StringBuilder sql = new StringBuilder("SELECT c.* FROM fraud_case c WHERE c.tenant_id = :tenantId");
        if (status != null) {
            sql.append(" AND c.status = :status");
        }
        if (campaignId != null) {
            sql.append(" AND c.campaign_id = :campaignId");
        }
        if (reasonCode != null) {
            sql.append(" AND string_to_array(c.reason_codes, ',') @> ARRAY[CAST(:reasonCode AS text)]");
        }
        if (search != null) {
            // Free-text case search: match the referral code or the referee's user
            // id, case-insensitively. The term's LIKE metacharacters are escaped so
            // a user typing '%' searches literally, not "match everything".
            sql.append(" AND (lower(c.referral_code) LIKE :search ESCAPE '\\'"
                    + " OR lower(c.referee_user_id) LIKE :search ESCAPE '\\')");
        }
        if (cursorId != null) {
            if (sort == CaseSort.SCORE) {
                sql.append(" AND (c.score, c.opened_at, c.id) < (:cursorScore, :cursorOpenedAt, :cursorId)");
            } else {
                sql.append(" AND (c.opened_at, c.id) > (:cursorOpenedAt, :cursorId)");
            }
        }
        sql.append(sort == CaseSort.SCORE
                ? " ORDER BY c.score DESC, c.opened_at DESC, c.id DESC"
                : " ORDER BY c.opened_at ASC, c.id ASC");

        Query query = entityManager.createNativeQuery(sql.toString(), FraudCase.class)
                .setParameter("tenantId", tenantId)
                .setMaxResults(fetchSize);
        if (status != null) {
            query.setParameter("status", status.name());
        }
        if (campaignId != null) {
            query.setParameter("campaignId", campaignId);
        }
        if (reasonCode != null) {
            query.setParameter("reasonCode", reasonCode.name());
        }
        if (search != null) {
            query.setParameter("search", "%" + escapeLike(search.toLowerCase(Locale.ROOT)) + "%");
        }
        if (cursorId != null) {
            if (sort == CaseSort.SCORE) {
                query.setParameter("cursorScore", cursorScore);
            }
            query.setParameter("cursorOpenedAt", cursorOpenedAt);
            query.setParameter("cursorId", cursorId);
        }
        return query.getResultList();
    }

    /** Escapes LIKE metacharacters so a search term is matched literally. */
    private static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
