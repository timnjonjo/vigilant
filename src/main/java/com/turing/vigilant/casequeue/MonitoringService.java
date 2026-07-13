package com.turing.vigilant.casequeue;

import com.turing.vigilant.casequeue.MonitoringView.FraudRatePointView;
import com.turing.vigilant.casequeue.MonitoringView.OpsAlertView;
import com.turing.vigilant.casequeue.MonitoringView.TopCodeView;
import com.turing.vigilant.shared.Decision;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Computes the ops view from the case store. Only genuinely derivable metrics are
 * populated; amount- and rate-based fields that the system doesn't capture yet
 * are returned as placeholders (see {@link MonitoringView}).
 */
@Service
public class MonitoringService {

    private static final int WINDOW_DAYS = 30;
    private static final int TOP_CODES = 10;

    private final FraudCaseRepository repository;
    private final Clock clock;

    public MonitoringService(FraudCaseRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public MonitoringView summarise(String tenantId) {
        return summarise(tenantId, null);
    }

    /** Optionally scoped to a single campaign (spec §10a) — null/blank = all campaigns. */
    public MonitoringView summarise(String tenantId, String campaignId) {
        List<FraudCase> cases = repository.findByTenantIdOrderByOpenedAtDesc(tenantId).stream()
                .filter(c -> campaignId == null || campaignId.isBlank() || campaignId.equals(c.getCampaignId()))
                .toList();

        long openCount = cases.stream().filter(c -> c.getStatus() == CaseStatus.OPEN).count();

        List<FraudCase> resolved = cases.stream()
                .filter(c -> c.getStatus() == CaseStatus.RESOLVED)
                .toList();
        long clearedAsGenuine = resolved.stream().filter(c -> c.getResolution() == Decision.APPROVE).count();
        double falsePositiveRate = resolved.isEmpty() ? 0.0 : clearedAsGenuine * 100.0 / resolved.size();

        return new MonitoringView(
                0.0,   // blockedValueKes — no payout amount captured
                0.0,   // blockedValueTrendPct
                round1(falsePositiveRate),
                0.0,   // falsePositiveTrendPct — no historical window computed
                openCount,
                dailyCounts(cases),
                topCodes(tenantId, cases),
                List.of());   // no alerting engine yet
    }

    /** Daily opened vs resolved counts over the trailing window (counts, not %). */
    private List<FraudRatePointView> dailyCounts(List<FraudCase> cases) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        return java.util.stream.IntStream.rangeClosed(0, WINDOW_DAYS - 1)
                .mapToObj(offset -> today.minusDays(WINDOW_DAYS - 1L - offset))
                .map(day -> new FraudRatePointView(
                        day.toString(),
                        cases.stream().filter(c -> onDay(c.getOpenedAt(), day)).count(),
                        cases.stream()
                                .filter(c -> c.getResolvedAt() != null && onDay(c.getResolvedAt(), day))
                                .count()))
                .toList();
    }

    private List<TopCodeView> topCodes(String tenantId, List<FraudCase> cases) {
        Map<String, List<FraudCase>> byCode = cases.stream()
                .collect(Collectors.groupingBy(FraudCase::getReferralCode));
        return byCode.entrySet().stream()
                .map(e -> new TopCodeView(
                        e.getKey(),
                        tenantId,
                        e.getValue().size(),
                        e.getValue().stream().mapToDouble(FraudCase::getScore).max().orElse(0.0),
                        0.0)) // blockedKes — no amount data
                .sorted(Comparator.comparingDouble(TopCodeView::riskScore).reversed())
                .limit(TOP_CODES)
                .toList();
    }

    private static boolean onDay(java.time.Instant instant, LocalDate day) {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC).equals(day);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
