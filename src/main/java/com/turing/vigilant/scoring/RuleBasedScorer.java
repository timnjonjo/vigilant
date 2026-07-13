package com.turing.vigilant.scoring;

import com.turing.vigilant.scoring.rules.CycleDetectionRule;
import com.turing.vigilant.scoring.rules.DatacenterIpRule;
import com.turing.vigilant.scoring.rules.DeviceCollisionRule;
import com.turing.vigilant.scoring.rules.IpSubnetCollisionRule;
import com.turing.vigilant.scoring.rules.VelocityBurstRule;
import com.turing.vigilant.shared.ReasonCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Weighted, rules-first scorer (v1). Runs each rule, accumulates the weights of
 * those that fire, clamps to [0.0, 1.0], and returns the value with its reason
 * codes. Interpretable and auditable — no black-box model (spec section 2).
 */
public class RuleBasedScorer {

    private final List<ScoringRule> rules;

    public RuleBasedScorer(ScoringWeights weights) {
        this(List.of(
                new VelocityBurstRule(weights),
                new DeviceCollisionRule(weights),
                new IpSubnetCollisionRule(weights),
                new CycleDetectionRule(weights),
                new DatacenterIpRule(weights)));
    }

    public RuleBasedScorer(List<ScoringRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public RiskScore score(ScoringRequest request) {
        double total = 0.0;
        List<ReasonCode> reasonCodes = new ArrayList<>();
        for (ScoringRule rule : rules) {
            var hit = rule.evaluate(request);
            if (hit.isPresent()) {
                total += hit.get().weight();
                reasonCodes.add(hit.get().reasonCode());
            }
        }
        return new RiskScore(Math.min(1.0, total), reasonCodes);
    }
}
