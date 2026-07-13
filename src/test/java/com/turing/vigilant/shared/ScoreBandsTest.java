package com.turing.vigilant.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoreBandsTest {

    private final ScoreBands bands = new ScoreBands(0.40, 0.75);

    @Test
    void classifiesLowScoreAsApprove() {
        assertThat(bands.classify(0.0)).isEqualTo(Decision.APPROVE);
        assertThat(bands.classify(0.39)).isEqualTo(Decision.APPROVE);
    }

    @Test
    void classifiesMidScoreAsHold() {
        assertThat(bands.classify(0.40)).isEqualTo(Decision.HOLD);
        assertThat(bands.classify(0.74)).isEqualTo(Decision.HOLD);
    }

    @Test
    void classifiesHighScoreAsReject() {
        assertThat(bands.classify(0.75)).isEqualTo(Decision.REJECT);
        assertThat(bands.classify(1.0)).isEqualTo(Decision.REJECT);
    }

    @Test
    void rejectsThresholdsOutOfOrder() {
        assertThatThrownBy(() -> new ScoreBands(0.75, 0.40))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsThresholdsOutOfRange() {
        assertThatThrownBy(() -> new ScoreBands(-0.1, 0.75))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScoreBands(0.40, 1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
