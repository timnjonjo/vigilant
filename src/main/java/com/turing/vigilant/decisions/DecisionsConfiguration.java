package com.turing.vigilant.decisions;

import com.turing.vigilant.shared.ScoreBands;
import com.turing.vigilant.tenant.VigilantProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Builds the decision-time policy from the configured score-band thresholds. */
@Configuration
public class DecisionsConfiguration {

    @Bean
    DecisionPolicy decisionPolicy(VigilantProperties properties) {
        ScoreBands bands = new ScoreBands(
                properties.getScoring().getHoldThreshold(),
                properties.getScoring().getRejectThreshold());
        return new DecisionPolicy(bands);
    }
}
