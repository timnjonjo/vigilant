package com.turing.vigilant.scoring.rules;

import com.turing.vigilant.graph.ReferralEdge;
import com.turing.vigilant.scoring.RuleHit;
import com.turing.vigilant.scoring.ScoringRequest;
import com.turing.vigilant.scoring.ScoringRule;
import com.turing.vigilant.scoring.ScoringWeights;
import com.turing.vigilant.shared.ReasonCode;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedPseudograph;

import java.util.Optional;

/**
 * Builds a directed graph of the REFERRED edges in the pulled subgraph and runs
 * JGraphT cycle detection to catch self-referral loops (spec section 4). JGraphT
 * is used only for this in-process algorithm, never as the system of record.
 */
public class CycleDetectionRule implements ScoringRule {

    private final ScoringWeights weights;

    public CycleDetectionRule(ScoringWeights weights) {
        this.weights = weights;
    }

    @Override
    public Optional<RuleHit> evaluate(ScoringRequest request) {
        Graph<String, DefaultEdge> graph = new DirectedPseudograph<>(DefaultEdge.class);
        for (ReferralEdge edge : request.neighbourhood().referralEdges()) {
            graph.addVertex(edge.referrerUserId());
            graph.addVertex(edge.refereeUserId());
            graph.addEdge(edge.referrerUserId(), edge.refereeUserId());
        }

        boolean hasCycle = new CycleDetector<>(graph).detectCycles();
        return hasCycle
                ? Optional.of(new RuleHit(ReasonCode.CYCLE_DETECTED, weights.cycleWeight()))
                : Optional.empty();
    }
}
