package com.turing.vigilant.tenant;

import com.turing.vigilant.shared.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantRegistryTest {

    private final TenantConfig loob =
            new TenantConfig(TenantId.of("loob-bank"), "https://loob.example/callback");
    private final TenantRegistry registry = new TenantRegistry(List.of(loob));

    @Test
    void findsConfiguredTenant() {
        assertThat(registry.require(TenantId.of("loob-bank"))).isEqualTo(loob);
    }

    @Test
    void rejectsUnknownTenant() {
        assertThatThrownBy(() -> registry.require(TenantId.of("ghost-bank")))
                .isInstanceOf(UnknownTenantException.class);
    }
}
