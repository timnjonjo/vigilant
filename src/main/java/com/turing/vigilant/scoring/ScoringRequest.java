package com.turing.vigilant.scoring;

import com.turing.vigilant.graph.FanoutBaseline;
import com.turing.vigilant.graph.ReferralNeighbourhood;

import java.time.Instant;

/**
 * Everything a scoring pass needs, assembled from the graph store: the bounded
 * subgraph, the population fan-out baseline, and the evaluation instant.
 */
public record ScoringRequest(
        ReferralNeighbourhood neighbourhood,
        FanoutBaseline baseline,
        Instant evaluatedAt) {
}
