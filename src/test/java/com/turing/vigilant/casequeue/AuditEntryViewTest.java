package com.turing.vigilant.casequeue;

import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.Decision;
import com.turing.vigilant.shared.ReasonCode;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEntryViewTest {

    private static FraudCase openCase() {
        return FraudCase.open(new CaseOpening(
                TenantId.of("loob-bank"), CampaignId.of("camp-1"), ReferralCode.of("LOOB-1"), "u2",
                Decision.HOLD, 0.5, List.of(ReasonCode.DEVICE_COLLISION), Instant.parse("2026-07-10T10:00:00Z")));
    }

    @Test
    void synthesisesAnOpenedEntryForAnOpenCase() {
        List<AuditEntryView> entries = AuditEntryView.forRelatedCases(List.of(openCase()));

        assertThat(entries).singleElement()
                .satisfies(e -> {
                    assertThat(e.action()).isEqualTo("OPENED");
                    assertThat(e.actor()).isEqualTo("engine");
                });
    }

    @Test
    void addsAResolutionEntryOnceResolved() {
        FraudCase c = openCase();
        c.resolve(Decision.REJECT, "amina.k", Instant.parse("2026-07-10T12:00:00Z"));

        List<AuditEntryView> entries = AuditEntryView.forRelatedCases(List.of(c));

        assertThat(entries).hasSize(2);
        assertThat(entries.get(1).action()).isEqualTo("REJECT");
        assertThat(entries.get(1).actor()).isEqualTo("amina.k");
    }
}
