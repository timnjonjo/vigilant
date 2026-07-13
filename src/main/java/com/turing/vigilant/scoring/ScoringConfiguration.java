package com.turing.vigilant.scoring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the scoring beans. Weights use defaults until they are made tenant-tunable. */
@Configuration
public class ScoringConfiguration {

    @Bean
    RuleBasedScorer ruleBasedScorer() {
        return new RuleBasedScorer(ScoringWeights.defaults());
    }
}
