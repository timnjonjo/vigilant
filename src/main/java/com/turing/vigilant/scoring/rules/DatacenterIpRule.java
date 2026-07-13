package com.turing.vigilant.scoring.rules;

import com.turing.vigilant.graph.AccountNode;
import com.turing.vigilant.scoring.RuleHit;
import com.turing.vigilant.scoring.ScoringRequest;
import com.turing.vigilant.scoring.ScoringRule;
import com.turing.vigilant.scoring.ScoringWeights;
import com.turing.vigilant.shared.IpType;
import com.turing.vigilant.shared.ReasonCode;

import java.util.Optional;

/**
 * Fires when any account in the cluster last connected from a datacenter / VPN /
 * cloud IP. The classification is done at ingestion and stored on the node, so
 * this rule stays graph-pure like the collision rules — no live IP lookup at
 * score time.
 */
public class DatacenterIpRule implements ScoringRule {

    private final ScoringWeights weights;

    public DatacenterIpRule(ScoringWeights weights) {
        this.weights = weights;
    }

    @Override
    public Optional<RuleHit> evaluate(ScoringRequest request) {
        boolean fromDatacenter = request.neighbourhood().accounts().stream()
                .map(AccountNode::ipType)
                .anyMatch(type -> type == IpType.DATACENTER);

        return fromDatacenter
                ? Optional.of(new RuleHit(ReasonCode.DATACENTER_OR_VPN_IP, weights.datacenterWeight()))
                : Optional.empty();
    }
}
