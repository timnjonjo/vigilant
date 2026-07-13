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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FraudCaseTest {

    private static final Instant OPENED = Instant.parse("2026-07-10T12:00:00Z");
    private static final Instant RESOLVED = Instant.parse("2026-07-10T13:00:00Z");

    private FraudCase openCase() {
        return FraudCase.open(new CaseOpening(
                TenantId.of("loob-bank"), CampaignId.of("camp-1"), ReferralCode.of("LOOB-R1"), "u2",
                Decision.HOLD, 0.5, List.of(ReasonCode.DEVICE_COLLISION), OPENED));
    }

    @Test
    void opensInOpenStatusWithAuditFields() {
        FraudCase c = openCase();

        assertThat(c.getStatus()).isEqualTo(CaseStatus.OPEN);
        assertThat(c.getDecision()).isEqualTo(Decision.HOLD);
        assertThat(c.getScore()).isEqualTo(0.5);
        assertThat(c.getReasonCodes()).containsExactly(ReasonCode.DEVICE_COLLISION);
        assertThat(c.getOpenedAt()).isEqualTo(OPENED);
        assertThat(c.getResolution()).isNull();
    }

    @Test
    void resolvingRecordsFinalActionAndResolver() {
        FraudCase c = openCase();

        c.resolve(Decision.REJECT, "analyst-jane", RESOLVED);

        assertThat(c.getStatus()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(c.getResolution()).isEqualTo(Decision.REJECT);
        assertThat(c.getResolvedBy()).isEqualTo("analyst-jane");
        assertThat(c.getResolvedAt()).isEqualTo(RESOLVED);
    }

    @Test
    void cannotResolveTwice() {
        FraudCase c = openCase();
        c.resolve(Decision.APPROVE, "analyst-jane", RESOLVED);

        assertThatThrownBy(() -> c.resolve(Decision.REJECT, "analyst-bob", RESOLVED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resolutionMustBeApproveOrReject() {
        FraudCase c = openCase();

        assertThatThrownBy(() -> c.resolve(Decision.HOLD, "analyst-jane", RESOLVED))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
