package com.turing.vigilant.casequeue;

import java.util.List;

/**
 * Ops monitoring aggregates, shaped for the dashboard's {@code MonitoringSummary}.
 *
 * <p>Honesty note: the case store captures no payout amount and no total-event
 * denominator, so {@code blockedValueKes} and the trend fields are placeholders
 * (0) and {@code fraudRateSeries} carries daily case <em>counts</em>, not true
 * rates. {@code openCaseCount}, {@code falsePositiveRatePct} and {@code topCodes}
 * are computed from real data. See the integration notes.
 */
public record MonitoringView(
        double blockedValueKes,
        double blockedValueTrendPct,
        double falsePositiveRatePct,
        double falsePositiveTrendPct,
        long openCaseCount,
        List<FraudRatePointView> fraudRateSeries,
        List<TopCodeView> topCodes,
        List<OpsAlertView> alerts) {

    public record FraudRatePointView(String date, double flaggedRate, double reviewedRate) {
    }

    public record TopCodeView(String referralCode, String tenantId, long volume, double riskScore,
                              double blockedKes) {
    }

    public record OpsAlertView(String id, String severity, String title, String detail, String at) {
    }
}
