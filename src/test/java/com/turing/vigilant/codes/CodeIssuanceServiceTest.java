package com.turing.vigilant.codes;

import com.turing.vigilant.campaign.CampaignNotActiveException;
import com.turing.vigilant.campaign.CampaignStatus;
import com.turing.vigilant.campaign.CampaignService;
import com.turing.vigilant.graph.GraphStore;
import com.turing.vigilant.graph.GraphCommands.ReferrerRegistration;
import com.turing.vigilant.ipreputation.IpReputationChecker;
import com.turing.vigilant.ipreputation.IpReputationResult;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.IpType;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Collectors;

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
    void bindsTheCodeToItsCampaignAndDropsAMalformedIp() {
        when(generator.generate(any(), any())).thenReturn(ReferralCode.of("LOOB-BOUND"));

        service.issue(LOOB, CAMPAIGN, "u1", "d1", "not-an-ip");

        ArgumentCaptor<ReferrerRegistration> captor = ArgumentCaptor.forClass(ReferrerRegistration.class);
        verify(graphStore).registerReferrer(captor.capture());
        assertThat(captor.getValue().campaignId()).isEqualTo(CAMPAIGN);
        assertThat(captor.getValue().ipAddress()).isNull();
        assertThat(captor.getValue().ipType()).isEqualTo(IpType.UNKNOWN);
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

    @Test
    void logsAStructuredCodeIssuedEventWithoutIdentityFields() {
        when(generator.generate(any(), any())).thenReturn(ReferralCode.of("LOOB-ABC"));
        when(ipChecker.check(any())).thenReturn(new IpReputationResult(IpType.RESIDENTIAL, 0.1, "x"));
        when(graphStore.identityCollisionExists(LOOB, "u1", "d1", "10.0.0.1")).thenReturn(true);

        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(CodeIssuanceService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        service.issue(LOOB, CAMPAIGN, "u1", "d1", "10.0.0.1");

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getMessage()).isEqualTo("code_issued");
            Map<String, String> fields = event.getKeyValuePairs().stream().collect(
                    Collectors.toMap(pair -> pair.key, pair -> String.valueOf(pair.value), (a, b) -> b));
            assertThat(fields)
                    .containsEntry("event", "code_issued")
                    .containsEntry("tenantId", "loob-bank")
                    .containsEntry("campaignId", "camp-1")
                    .containsEntry("referralCode", "LOOB-ABC")
                    .containsEntry("riskFlag", "true");
            // Referrer userId, device and IP are identity/PII — never at INFO.
            assertThat(fields.values()).noneMatch(v ->
                    v.contains("u1") || v.contains("d1") || v.contains("10.0.0.1"));
        });
    }
}
