package com.turing.vigilant.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantIdTest {

    @Test
    void wrapsValue() {
        assertThat(TenantId.of("loob-bank").value()).isEqualTo("loob-bank");
    }

    @Test
    void rejectsBlankValue() {
        assertThatThrownBy(() -> TenantId.of("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantId.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalsByValue() {
        assertThat(TenantId.of("loob-bank")).isEqualTo(TenantId.of("loob-bank"));
    }
}
