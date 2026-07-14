package com.turing.vigilant.decisions;

import com.turing.vigilant.graph.FanoutBaseline;
import com.turing.vigilant.graph.GraphStore;
import com.turing.vigilant.shared.CampaignId;
import com.turing.vigilant.shared.TenantId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-TTL memoisation of the per-campaign fan-out baseline.
 *
 * <p>The baseline (mean/stddev/n of every referrer's fan-out in the velocity
 * window) is the same for every payout-check on a given campaign, yet computing
 * it scans <em>all</em> of that campaign's {@code REFERRED} edges — ~200k db-hits
 * on a busy campaign. Under load that recomputation, once per request, was the
 * dominant cost after the code-lookup scan was removed (it pegged Neo4j CPU while
 * Postgres sat idle). The distribution barely moves second-to-second, so a caller
 * can safely reuse a value up to {@code ttl} old — a purely statistical baseline
 * tolerates that staleness, and it turns an O(campaign edges) graph aggregation
 * into an O(1) map read on the hot path.
 *
 * <p>Entries are computed under a per-key lock (single-flight) so a TTL expiry
 * can't stampede N concurrent requests into N identical scans.
 */
@Component
public class FanoutBaselineCache {

    private final GraphStore graphStore;
    private final Clock clock;
    private final Duration ttl;
    private final ConcurrentHashMap<Key, Entry> cache = new ConcurrentHashMap<>();

    public FanoutBaselineCache(GraphStore graphStore, Clock clock,
                               @Value("${vigilant.scoring.fanout-baseline-ttl:PT30S}") Duration ttl) {
        this.graphStore = graphStore;
        this.clock = clock;
        this.ttl = ttl;
    }

    /** Returns a baseline for the campaign, recomputing only if the cached one has aged past the TTL. */
    public FanoutBaseline get(TenantId tenantId, CampaignId campaignId, Duration velocityWindow) {
        Key key = new Key(tenantId.value(), campaignId.value());
        Instant now = clock.instant();
        Entry cached = cache.get(key);
        if (cached != null && Duration.between(cached.computedAt(), now).compareTo(ttl) < 0) {
            return cached.baseline();
        }
        // Single-flight: only one thread recomputes a given campaign at a time; the
        // rest reuse whatever it stored. compute() also re-checks freshness so a
        // thread that queued behind a recompute doesn't recompute again.
        return cache.compute(key, (k, existing) -> {
            Instant t = clock.instant();
            if (existing != null && Duration.between(existing.computedAt(), t).compareTo(ttl) < 0) {
                return existing;
            }
            FanoutBaseline fresh = graphStore.fanoutBaseline(
                    tenantId, campaignId, t.minus(velocityWindow), t);
            return new Entry(fresh, t);
        }).baseline();
    }

    private record Key(String tenantId, String campaignId) {
    }

    private record Entry(FanoutBaseline baseline, Instant computedAt) {
    }
}
