package com.turing.vigilant.codes;

import com.turing.vigilant.campaign.CampaignNotActiveException;
import com.turing.vigilant.campaign.CampaignStatus;
import com.turing.vigilant.campaign.CampaignService;
import com.turing.vigilant.graph.GraphStore;
import com.turing.vigilant.ipreputation.IpReputationChecker;
import com.turing.vigilant.ipreputation.IpReputationResult;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.IpType;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodeIssuanceServiceTest {

    private static final TenantId LOOB = TenantId.of("loob-bank");
    private static final CampaignId CAMPAIGN = CampaignId.of("camp-1");

    private final GraphStore graphStore = mock(GraphStore.class);
    private final ReferralCodeGenerator generator = mock(ReferralCodeGenerator.class);
    private final IpReputationChecker ipChecker = mock(IpReputationChecker.class);
    private final CampaignService campaignService = mock(CampaignService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC);
    private final CodeIssuanceService service =
            new CodeIssuanceService(graphStore, generator, ipChecker, campaignService, clock);

    @Test
    void issuesAgainstAnActiveCampaign() {
        when(generator.generate(any(), any())).thenReturn(ReferralCode.of("LOOB-ABC"));
        when(ipChecker.check(any())).thenReturn(new IpReputationResult(IpType.RESIDENTIAL, 0.1, "x"));

        CodeIssuanceService.IssuedCode issued = service.issue(LOOB, CAMPAIGN, "u1", "d1", "10.0.0.1");

        assertThat(issued.referralCode().value()).isEqualTo("LOOB-ABC");
        verify(campaignService).requireActiveCampaign(LOOB, CAMPAIGN);
        verify(graphStore).registerReferrer(any());
    }

    @Test
    void rejectsAnInactiveCampaignAndDoesNotTouchTheGraph() {
        when(campaignService.requireActiveCampaign(LOOB, CAMPAIGN))
                .thenThrow(new CampaignNotActiveException(CAMPAIGN, CampaignStatus.DRAFT));

        assertThatThrownBy(() -> service.issue(LOOB, CAMPAIGN, "u1", "d1", "10.0.0.1"))
                .isInstanceOf(CampaignNotActiveException.class);

        verify(graphStore, never()).registerReferrer(any());
        verify(generator, never()).generate(any(), any());
    }
}
