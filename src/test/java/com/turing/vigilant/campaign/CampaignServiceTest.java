package com.turing.vigilant.campaign;

import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CampaignServiceTest {

    private static final TenantId LOOB = TenantId.of("loob-bank");
    private static final TenantId ACME = TenantId.of("acme-sacco");

    private final CampaignRepository repository = mock(CampaignRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC);
    private final CampaignService service = new CampaignService(repository, clock);

    private Campaign campaign(TenantId tenant, CampaignStatus status) {
        return Campaign.create(tenant.value(), "Q3", new BigDecimal("350.00"), null, null,
                status, ConversionCriteria.FIRST_DEPOSIT, 5, clock.instant());
    }

    @Test
    void createPersistsWithGeneratedId() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        Campaign c = service.create(LOOB, "Q3", new BigDecimal("350.00"), null, null,
                CampaignStatus.ACTIVE, ConversionCriteria.FIRST_DEPOSIT, 5);

        assertThat(c.getCampaignId()).isNotBlank();
        assertThat(c.getTenantId()).isEqualTo("loob-bank");
        assertThat(c.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
    }

    @Test
    void requireCampaignIsTenantScoped() {
        // Repository is keyed by (campaignId, tenantId): another tenant sees nothing.
        when(repository.findByCampaignIdAndTenantId("c1", "acme-sacco")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireCampaign(ACME, CampaignId.of("c1")))
                .isInstanceOf(CampaignNotFoundException.class);
    }

    @Test
    void requireActiveCampaignRejectsANonActiveCampaign() {
        when(repository.findByCampaignIdAndTenantId("c1", "loob-bank"))
                .thenReturn(Optional.of(campaign(LOOB, CampaignStatus.DRAFT)));

        assertThatThrownBy(() -> service.requireActiveCampaign(LOOB, CampaignId.of("c1")))
                .isInstanceOf(CampaignNotActiveException.class);
    }

    @Test
    void requireActiveCampaignReturnsAnActiveCampaign() {
        Campaign active = campaign(LOOB, CampaignStatus.ACTIVE);
        when(repository.findByCampaignIdAndTenantId("c1", "loob-bank")).thenReturn(Optional.of(active));

        assertThat(service.requireActiveCampaign(LOOB, CampaignId.of("c1"))).isSameAs(active);
    }

    @Test
    void updateAppliesOnlyProvidedFieldsAndSaves() {
        Campaign existing = campaign(LOOB, CampaignStatus.ACTIVE);
        when(repository.findByCampaignIdAndTenantId(existing.getCampaignId(), "loob-bank"))
                .thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        Campaign updated = service.update(LOOB, CampaignId.of(existing.getCampaignId()),
                null, null, null, null, CampaignStatus.PAUSED, null, null);

        assertThat(updated.getStatus()).isEqualTo(CampaignStatus.PAUSED);
        assertThat(updated.getName()).isEqualTo("Q3"); // unchanged
    }
}
