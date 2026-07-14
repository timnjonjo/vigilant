package com.turing.vigilant.decisions;

import com.turing.vigilant.campaign.CampaignService;
import com.turing.vigilant.casequeue.CaseRecorder;
import com.turing.vigilant.graph.FanoutBaseline;
import com.turing.vigilant.graph.GraphStore;
import com.turing.vigilant.graph.ReferralNeighbourhood;
import com.turing.vigilant.scoring.RiskScore;
import com.turing.vigilant.scoring.RuleBasedScorer;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.Decision;
import com.turing.vigilant.shared.ReasonCode;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.shared.TenantId;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayoutDecisionServiceTest {

    private static final TenantId TENANT = TenantId.of("loob-bank");
    private static final CampaignId CAMPAIGN = CampaignId.of("camp-1");
    private static final ReferralCode CODE = ReferralCode.of("LOOB-R1");

    private final GraphStore graphStore = mock(GraphStore.class);
    private final RuleBasedScorer scorer = mock(RuleBasedScorer.class);
    private final DecisionPolicy decisionPolicy = mock(DecisionPolicy.class);
    private final CaseRecorder caseRecorder = mock(CaseRecorder.class);
    private final CampaignService campaignService = mock(CampaignService.class);
    private final FanoutBaselineCache baselineCache = mock(FanoutBaselineCache.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-14T10:00:00Z"), ZoneOffset.UTC);
    private final PayoutDecisionService service = new PayoutDecisionService(
            graphStore, scorer, decisionPolicy, caseRecorder, campaignService, baselineCache, clock);

    @Test
    void failsClosedBeforeScoringAnUnknownOrUnconvertedSubject() {
        assertThatThrownBy(() -> service.decide(TENANT, CAMPAIGN, CODE, "referee-A"))
                .isInstanceOf(PayoutNotEligibleException.class);

        verify(campaignService).requireCampaign(TENANT, CAMPAIGN);
        verify(graphStore).convertedReferralExists(TENANT, CAMPAIGN, CODE, "referee-A");
        verify(graphStore, never()).loadNeighbourhood(any(), any(), any());
        verify(scorer, never()).score(any());
        verify(caseRecorder, never()).record(any());
    }

    @Test
    void scoresAnExactConvertedReferralNormally() {
        ReferralNeighbourhood neighbourhood = new ReferralNeighbourhood(
                TENANT, CODE, CAMPAIGN, "referrer", List.of(), List.of(), List.of());
        when(graphStore.convertedReferralExists(TENANT, CAMPAIGN, CODE, "referee-A")).thenReturn(true);
        when(graphStore.loadNeighbourhood(TENANT, CODE, CAMPAIGN)).thenReturn(neighbourhood);
        when(scorer.velocityWindow()).thenReturn(Duration.ofHours(1));
        when(baselineCache.get(TENANT, CAMPAIGN, Duration.ofHours(1))).thenReturn(FanoutBaseline.empty());
        when(scorer.score(any())).thenReturn(new RiskScore(0.1, List.of()));
        when(decisionPolicy.classify(0.1)).thenReturn(Decision.APPROVE);

        PayoutDecisionService.PayoutDecision decision =
                service.decide(TENANT, CAMPAIGN, CODE, "referee-A");

        assertThat(decision.action()).isEqualTo(Decision.APPROVE);
        assertThat(decision.score()).isEqualTo(0.1);
    }

    @Test
    void logsAStructuredPayoutDecisionEventWithoutRefereePii() {
        ReferralNeighbourhood neighbourhood = new ReferralNeighbourhood(
                TENANT, CODE, CAMPAIGN, "referrer", List.of(), List.of(), List.of());
        when(graphStore.convertedReferralExists(TENANT, CAMPAIGN, CODE, "referee-A")).thenReturn(true);
        when(graphStore.loadNeighbourhood(TENANT, CODE, CAMPAIGN)).thenReturn(neighbourhood);
        when(scorer.velocityWindow()).thenReturn(Duration.ofHours(1));
        when(baselineCache.get(TENANT, CAMPAIGN, Duration.ofHours(1))).thenReturn(FanoutBaseline.empty());
        when(scorer.score(any())).thenReturn(new RiskScore(0.75, List.of(ReasonCode.DEVICE_COLLISION)));
        when(decisionPolicy.classify(0.75)).thenReturn(Decision.HOLD);
        when(caseRecorder.record(any())).thenReturn(42L);

        ListAppender<ILoggingEvent> appender = attachAppender();
        service.decide(TENANT, CAMPAIGN, CODE, "referee-A");

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getMessage()).isEqualTo("payout_decision");
            Map<String, String> fields = fields(event);
            assertThat(fields)
                    .containsEntry("event", "payout_decision")
                    .containsEntry("tenantId", "loob-bank")
                    .containsEntry("campaignId", "camp-1")
                    .containsEntry("referralCode", "LOOB-R1")
                    .containsEntry("action", "HOLD")
                    .containsEntry("score", "0.75")
                    .containsEntry("caseId", "42");
            assertThat(fields.get("reasonCodes")).contains("DEVICE_COLLISION");
            // The referee id is PII: it must not reach the log, in any field or the message.
            assertThat(fields.values()).noneMatch(v -> v.contains("referee-A"));
            assertThat(event.getFormattedMessage()).doesNotContain("referee-A");
        });
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(PayoutDecisionService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static Map<String, String> fields(ILoggingEvent event) {
        return event.getKeyValuePairs().stream().collect(Collectors.toMap(
                pair -> pair.key, pair -> String.valueOf(pair.value), (a, b) -> b));
    }
}
