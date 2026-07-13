package com.turing.vigilant.tenant;

import com.turing.vigilant.shared.TenantId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Turns bound {@link VigilantProperties} into the runtime beans: the
 * {@link TenantRegistry} and the {@link ScoreBands} used across scoring.
 */
@Configuration
@EnableConfigurationProperties(VigilantProperties.class)
public class TenantConfiguration {

    @Bean
    TenantRegistry tenantRegistry(VigilantProperties properties) {
        List<TenantConfig> tenants = properties.getTenants().entrySet().stream()
                .map(entry -> new TenantConfig(
                        TenantId.of(entry.getKey()),
                        entry.getValue().getCallbackUrl()))
                .toList();
        return new TenantRegistry(tenants);
    }
}
