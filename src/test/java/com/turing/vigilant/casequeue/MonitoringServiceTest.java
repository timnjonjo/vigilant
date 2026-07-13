package com.turing.vigilant.casequeue;

import com.turing.vigilant.casequeue.MonitoringView.TopCodeView;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.Decision;
import com.turing.vigilant.shared.ReasonCode;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonitoringServiceTest {

    private final FraudCaseRepository repository = mock(FraudCaseRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC);
    private final MonitoringService service = new MonitoringService(repository, clock);

    private static FraudCase openCase(String code, double score, Decision decision) {
        return FraudCase.open(new CaseOpening(
                TenantId.of("loob-bank"), CampaignId.of("camp-1"), ReferralCode.of(code), "u", decision, score,
                List.of(ReasonCode.DEVICE_COLLISION), Instant.parse("2026-07-10T10:00:00Z")));
    }

    @Test
    void computesDerivableMetricsAndFlagsUnavailableOnes() {
        FraudCase open = openCase("LOOB-1", 0.9, Decision.HOLD);
        FraudCase clearedGenuine = openCase("LOOB-2", 0.5, Decision.HOLD);
        clearedGenuine.resolve(Decision.APPROVE, "amina", Instant.parse("2026-07-10T12:00:00Z"));
        FraudCase confirmedFraud = openCase("LOOB-1", 0.8, Decision.REJECT);
        confirmedFraud.resolve(Decision.REJECT, "amina", Instant.parse("2026-07-10T12:00:00Z"));

        when(repository.findByTenantIdOrderByOpenedAtDesc("loob-bank"))
                .thenReturn(List.of(open, clearedGenuine, confirmedFraud));

        MonitoringView v = service.summarise("loob-bank");

        assertThat(v.openCaseCount()).isEqualTo(1);
        assertThat(v.falsePositiveRatePct()).isEqualTo(50.0); // 1 of 2 resolved cleared as genuine
        assertThat(v.topCodes()).extracting(TopCodeView::referralCode).containsExactly("LOOB-1", "LOOB-2");
        assertThat(v.topCodes().get(0).volume()).isEqualTo(2);
        assertThat(v.topCodes().get(0).riskScore()).isEqualTo(0.9);
        assertThat(v.fraudRateSeries()).hasSize(30);
        // Unavailable without a payout amount — returned as an explicit placeholder.
        assertThat(v.blockedValueKes()).isZero();
    }
}
