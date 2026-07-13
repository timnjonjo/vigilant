package com.turing.vigilant.scoring.rules;

import com.turing.vigilant.graph.SharedAttributeEdge;
import com.turing.vigilant.graph.SharedAttributeType;
import com.turing.vigilant.scoring.RuleHit;
import com.turing.vigilant.scoring.ScoringRequest;
import com.turing.vigilant.scoring.ScoringRule;
import com.turing.vigilant.scoring.ScoringWeights;
import com.turing.vigilant.shared.ReasonCode;

import java.util.Optional;

/** Fires when accounts in the cluster share an IP subnet within the window. */
public class IpSubnetCollisionRule implements ScoringRule {

    private final ScoringWeights weights;

    public IpSubnetCollisionRule(ScoringWeights weights) {
        this.weights = weights;
    }

    @Override
    public Optional<RuleHit> evaluate(ScoringRequest request) {
        boolean collides = request.neighbourhood().sharedEdges().stream()
                .map(SharedAttributeEdge::type)
                .anyMatch(type -> type == SharedAttributeType.IP_SUBNET);

        return collides
                ? Optional.of(new RuleHit(ReasonCode.IP_SUBNET_COLLISION, weights.ipWeight()))
                : Optional.empty();
    }
}
