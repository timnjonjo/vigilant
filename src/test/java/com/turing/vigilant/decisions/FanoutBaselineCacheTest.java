package com.turing.vigilant.decisions;

import com.turing.vigilant.graph.FanoutBaseline;
import com.turing.vigilant.graph.GraphStore;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The cache exists to keep the per-campaign fan-out baseline off the hot path: it
 * must recompute at most once per TTL per campaign, isolate campaigns, and always
 * scan the current velocity window when it does recompute.
 */
class FanoutBaselineCacheTest {

    private final TenantId loob = TenantId.of("loob-bank");
    private final CampaignId campA = CampaignId.of("camp-a");
    private final CampaignId campB = CampaignId.of("camp-b");
    private final Duration window = Duration.ofDays(2);
    private final Duration ttl = Duration.ofSeconds(30);

    /** A hand-cranked clock so the test controls TTL expiry deterministically. */
    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant start) { this.now = start; }
        void advance(Duration d) { now = now.plus(d); }
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    @Test
    void computesOnceThenServesFromCacheWithinTtl() {
        GraphStore graph = mock(GraphStore.class);
        FanoutBaseline baseline = new FanoutBaseline(2.0, 0.5, 100);
        when(graph.fanoutBaseline(any(), any(), any(), any())).thenReturn(baseline);
        MutableClock clock = new MutableClock(Instant.parse("2026-07-14T10:00:00Z"));
        FanoutBaselineCache cache = new FanoutBaselineCache(graph, clock, ttl);

        FanoutBaseline first = cache.get(loob, campA, window);
        clock.advance(Duration.ofSeconds(29)); // still inside the TTL
        FanoutBaseline second = cache.get(loob, campA, window);

        assertThat(first).isEqualTo(baseline);
        assertThat(second).isSameAs(first);
        verify(graph, times(1)).fanoutBaseline(any(), any(), any(), any());
    }

    @Test
    void recomputesAfterTtlExpiryOverTheCurrentWindow() {
        GraphStore graph = mock(GraphStore.class);
        when(graph.fanoutBaseline(any(), any(), any(), any()))
                .thenReturn(new FanoutBaseline(1.0, 0.1, 10));
        MutableClock clock = new MutableClock(Instant.parse("2026-07-14T10:00:00Z"));
        FanoutBaselineCache cache = new FanoutBaselineCache(graph, clock, ttl);

        cache.get(loob, campA, window);
        clock.advance(Duration.ofSeconds(31)); // past the TTL
        cache.get(loob, campA, window);

        verify(graph, times(2)).fanoutBaseline(any(), any(), any(), any());
        // The recompute must use the window ending at "now", not the stale first now.
        Instant now = clock.instant();
        verify(graph).fanoutBaseline(eq(loob), eq(campA), eq(now.minus(window)), eq(now));
    }

    @Test
    void keysByCampaignSoCampaignsDoNotShareABaseline() {
        GraphStore graph = mock(GraphStore.class);
        AtomicReference<CampaignId> lastAsked = new AtomicReference<>();
        when(graph.fanoutBaseline(any(), any(), any(), any())).thenAnswer(inv -> {
            lastAsked.set(inv.getArgument(1));
            return inv.getArgument(1).equals(campA)
                    ? new FanoutBaseline(2.0, 0.5, 100)
                    : new FanoutBaseline(9.0, 1.0, 5);
        });
        MutableClock clock = new MutableClock(Instant.parse("2026-07-14T10:00:00Z"));
        FanoutBaselineCache cache = new FanoutBaselineCache(graph, clock, ttl);

        FanoutBaseline a = cache.get(loob, campA, window);
        FanoutBaseline b = cache.get(loob, campB, window);

        assertThat(a.mean()).isEqualTo(2.0);
        assertThat(b.mean()).isEqualTo(9.0);
        verify(graph, times(2)).fanoutBaseline(any(), any(), any(), any());
    }
}
