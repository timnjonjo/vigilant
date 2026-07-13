package com.turing.vigilant.ipreputation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.HashSet;

/**
 * Wires the IP-reputation beans from {@link IpReputationProperties}. The
 * {@link AsnResolver} is {@code @ConditionalOnMissingBean} so tests can supply a
 * stub resolver (no {@code .mmdb} needed) while production fails fast on a
 * missing database via {@link MmdbAsnResolver}.
 */
@Configuration
@EnableConfigurationProperties(IpReputationProperties.class)
public class IpReputationConfiguration {

    @Bean
    DatacenterAsnCatalog datacenterAsnCatalog(IpReputationProperties properties) {
        return new DatacenterAsnCatalog(
                new HashSet<>(properties.getDatacenterAsns()),
                new HashSet<>(properties.getKenyanCarrierAsns()));
    }

    @Bean
    @ConditionalOnMissingBean(AsnResolver.class)
    AsnResolver asnResolver(IpReputationProperties properties) {
        return new MmdbAsnResolver(Path.of(properties.getDatabasePath()));
    }

    @Bean
    IpReputationChecker ipReputationChecker(AsnResolver resolver, DatacenterAsnCatalog catalog) {
        return new com.turing.vigilant.ipreputation.LocalAsnReputationChecker(resolver, catalog);
    }
}
