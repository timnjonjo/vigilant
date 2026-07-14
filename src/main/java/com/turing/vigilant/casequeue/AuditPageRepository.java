package com.turing.vigilant.casequeue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/** Paginates the synthesised OPENED/RESOLVED audit stream without loading its history. */
@Repository
public class AuditPageRepository {

    private final EntityManager entityManager;

    public AuditPageRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<AuditEventRow> findPage(
            String tenantId, String referralCode, Instant cursorAt,
            Long cursorCaseId, Integer cursorEventOrder, int fetchSize) {
        StringBuilder sql = new StringBuilder("""
                SELECT e.* FROM (
                    SELECT c.id AS case_id, 0 AS event_order, c.opened_at AS event_at,
                           c.score, c.decision, c.reason_codes,
                           NULL::varchar AS resolved_by, NULL::varchar AS resolution
                    FROM fraud_case c
                    WHERE c.tenant_id = :tenantId AND c.referral_code = :referralCode
                    UNION ALL
                    SELECT c.id AS case_id, 1 AS event_order, c.resolved_at AS event_at,
                           c.score, c.decision, c.reason_codes,
                           c.resolved_by, c.resolution
                    FROM fraud_case c
                    WHERE c.tenant_id = :tenantId AND c.referral_code = :referralCode
                      AND c.resolved_at IS NOT NULL
                ) e
                WHERE 1 = 1
                """);
        if (cursorCaseId != null) {
            sql.append(" AND (e.event_at, e.case_id, e.event_order) > (:cursorAt, :cursorCaseId, :cursorEventOrder)");
        }
        sql.append(" ORDER BY e.event_at ASC, e.case_id ASC, e.event_order ASC");

        Query query = entityManager.createNativeQuery(sql.toString())
                .setParameter("tenantId", tenantId)
                .setParameter("referralCode", referralCode)
                .setMaxResults(fetchSize);
        if (cursorCaseId != null) {
            query.setParameter("cursorAt", cursorAt);
            query.setParameter("cursorCaseId", cursorCaseId);
            query.setParameter("cursorEventOrder", cursorEventOrder);
        }
        return ((List<Object[]>) query.getResultList()).stream()
                .map(AuditPageRepository::map)
                .toList();
    }

    private static AuditEventRow map(Object[] row) {
        return new AuditEventRow(
                ((Number) row[0]).longValue(),
                ((Number) row[1]).intValue(),
                instant(row[2]),
                ((Number) row[3]).doubleValue(),
                String.valueOf(row[4]),
                String.valueOf(row[5]),
                row[6] == null ? null : String.valueOf(row[6]),
                row[7] == null ? null : String.valueOf(row[7]));
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalStateException("unsupported audit timestamp type: " + value.getClass());
    }

    public record AuditEventRow(
            long caseId,
            int eventOrder,
            Instant at,
            double score,
            String decision,
            String reasonCodes,
            String resolvedBy,
            String resolution) {

        AuditEntryView toView() {
            if (eventOrder == 0) {
                String reasons = reasonCodes.replace(",", ", ");
                return new AuditEntryView(
                        "case-" + caseId + "-opened", at.toString(), "engine", "OPENED",
                        "Case #" + caseId + " opened — score " + String.format("%.2f", score)
                                + " → " + decision + " (" + reasons + ")");
            }
            return new AuditEntryView(
                    "case-" + caseId + "-resolved", at.toString(), resolvedBy, resolution,
                    "Case #" + caseId + " resolved " + resolution + ".");
        }
    }
}
