package com.turing.vigilant.events;

import com.turing.vigilant.campaign.CampaignService;
import com.turing.vigilant.graph.GraphCommands.RedemptionRecord;
import com.turing.vigilant.graph.GraphStore;
import com.turing.vigilant.ipreputation.IpReputationChecker;
import com.turing.vigilant.ipreputation.IpReputationResult;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.IpType;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventIngestionServiceTest {

    private static final TenantId TENANT = TenantId.of("loob-bank");
    private static final CampaignId CAMPAIGN = CampaignId.of("camp-1");
    private static final ReferralCode CODE = ReferralCode.of("LOOB-R1");

    private final GraphStore graphStore = mock(GraphStore.class);
    private final IpReputationChecker ipChecker = mock(IpReputationChecker.class);
    private final CampaignService campaignService = mock(CampaignService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-10T12:00:00Z"), ZoneOffset.UTC);
    private final EventIngestionService service =
            new EventIngestionService(graphStore, ipChecker, campaignService, clock);

    @Test
    void classifiesTheRedemptionIpAndStoresItOnTheNode() {
        when(graphStore.referralCodeExists(TENANT, CAMPAIGN, CODE)).thenReturn(true);
        when(ipChecker.check("203.0.113.7"))
                .thenReturn(new IpReputationResult(IpType.DATACENTER, 0.70, "local-asn"));

        service.recordRedemption(TENANT, CAMPAIGN, CODE, "u2", "dev", "203.0.113.7",
                Instant.parse("2026-07-10T10:00:00Z"));

        ArgumentCaptor<RedemptionRecord> captor = ArgumentCaptor.forClass(RedemptionRecord.class);
        verify(graphStore).recordRedemption(captor.capture());
        assertThat(captor.getValue().ipType()).isEqualTo(IpType.DATACENTER);
        assertThat(captor.getValue().campaignId()).isEqualTo(CAMPAIGN);
    }

    @Test
    void aMalformedIpDoesNotFailIngestionOrPolluteTheGraph() {
        when(graphStore.referralCodeExists(TENANT, CAMPAIGN, CODE)).thenReturn(true);

        service.recordRedemption(TENANT, CAMPAIGN, CODE, "u3", "dev", "garbage", null);

        ArgumentCaptor<RedemptionRecord> captor = ArgumentCaptor.forClass(RedemptionRecord.class);
        verify(graphStore).recordRedemption(captor.capture());
        assertThat(captor.getValue().ipType()).isEqualTo(IpType.UNKNOWN);
        assertThat(captor.getValue().ipAddress()).isNull();
    }

    @Test
    void rejectsACodeThatWasNotIssuedForTheClaimedCampaign() {
        assertThatThrownBy(() -> service.recordRedemption(
                TENANT, CAMPAIGN, CODE, "u3", "dev", "10.0.0.1", null))
                .isInstanceOf(ReferralEventNotFoundException.class);

        verify(graphStore, never()).recordRedemption(org.mockito.ArgumentMatchers.any());
    }
}
