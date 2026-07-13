package com.turing.vigilant.casequeue;

import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.Decision;
import com.turing.vigilant.shared.ReasonCode;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
class CasequeueIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    FraudCaseRepository repository;

    private static final TenantId LOOB = TenantId.of("loob-bank");
    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private CaseOpening hold(String refereeUserId) {
        return new CaseOpening(LOOB, CampaignId.of("camp-1"), ReferralCode.of("LOOB-R1"), refereeUserId,
                Decision.HOLD, 0.5, List.of(ReasonCode.DEVICE_COLLISION, ReasonCode.CYCLE_DETECTED), NOW);
    }

    @Test
    void recordsAndReloadsCaseWithAuditFields() {
        long id = new JpaCaseRecorder(repository).record(hold("u2"));

        FraudCase reloaded = repository.findById(id).orElseThrow();
        assertThat(reloaded.getTenantId()).isEqualTo("loob-bank");
        assertThat(reloaded.getStatus()).isEqualTo(CaseStatus.OPEN);
        assertThat(reloaded.getReasonCodes())
                .containsExactly(ReasonCode.DEVICE_COLLISION, ReasonCode.CYCLE_DETECTED);
        assertThat(reloaded.getScore()).isEqualTo(0.5);
    }

    @Test
    void queriesAreTenantAndStatusScoped() {
        JpaCaseRecorder recorder = new JpaCaseRecorder(repository);
        recorder.record(hold("u2"));
        recorder.record(new CaseOpening(TenantId.of("other-bank"), CampaignId.of("camp-2"), ReferralCode.of("O-1"), "x",
                Decision.REJECT, 0.9, List.of(ReasonCode.VELOCITY_BURST), NOW));

        assertThat(repository.findByTenantIdOrderByOpenedAtDesc("loob-bank")).hasSize(1);
        assertThat(repository.findByTenantIdAndStatusOrderByOpenedAtDesc("loob-bank", CaseStatus.OPEN))
                .hasSize(1);
        assertThat(repository.findByTenantIdAndStatusOrderByOpenedAtDesc("loob-bank", CaseStatus.RESOLVED))
                .isEmpty();
    }

    @Test
    void resolvingPersistsOutcomeAndFiresWebhook() {
        long id = new JpaCaseRecorder(repository).record(hold("u2"));
        CapturingWebhookDispatcher dispatcher = new CapturingWebhookDispatcher();
        CaseResolutionService resolution = new CaseResolutionService(repository, dispatcher, clock);

        resolution.resolve(LOOB, id, Decision.REJECT, "analyst-jane");

        FraudCase reloaded = repository.findById(id).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(reloaded.getResolution()).isEqualTo(Decision.REJECT);
        assertThat(reloaded.getResolvedBy()).isEqualTo("analyst-jane");
        assertThat(reloaded.getResolvedAt()).isEqualTo(NOW);

        assertThat(dispatcher.dispatched).singleElement().satisfies(d -> {
            assertThat(d.tenantId()).isEqualTo(LOOB);
            assertThat(d.callback().finalAction()).isEqualTo(Decision.REJECT);
            assertThat(d.callback().caseId()).isEqualTo(id);
            assertThat(d.callback().refereeUserId()).isEqualTo("u2");
        });
    }
}
