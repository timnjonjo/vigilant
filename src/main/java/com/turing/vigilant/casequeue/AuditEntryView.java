package com.turing.vigilant.casequeue;

import java.util.ArrayList;
import java.util.List;

/**
 * A case audit entry, shaped for the dashboard's {@code AuditEntry} type. There
 * is no per-action log table yet, so entries are synthesised from the persisted
 * case fields: an OPENED event and (if resolved) the analyst's decision — across
 * every case sharing the same referral code, so the analyst sees the code's
 * history, not just this one case.
 */
public record AuditEntryView(String id, String at, String actor, String action, String note) {

    static List<AuditEntryView> forRelatedCases(List<FraudCase> cases) {
        List<AuditEntryView> entries = new ArrayList<>();
        for (FraudCase c : cases) {
            String reasons = String.join(", ", c.getReasonCodes().stream().map(Enum::name).toList());
            entries.add(new AuditEntryView(
                    "case-" + c.getId() + "-opened",
                    c.getOpenedAt().toString(),
                    "engine",
                    "OPENED",
                    "Case #" + c.getId() + " opened — score " + String.format("%.2f", c.getScore())
                            + " → " + c.getDecision() + " (" + reasons + ")"));
            if (c.getStatus() == CaseStatus.RESOLVED && c.getResolvedAt() != null) {
                entries.add(new AuditEntryView(
                        "case-" + c.getId() + "-resolved",
                        c.getResolvedAt().toString(),
                        c.getResolvedBy(),
                        c.getResolution().name(),
                        "Case #" + c.getId() + " resolved " + c.getResolution() + "."));
            }
        }
        return entries;
    }
}
