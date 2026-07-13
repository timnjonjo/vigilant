package com.turing.vigilant.tenant;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Binds {@code vigilant.*} configuration: the onboarded tenants and the scoring
 * thresholds. Tenants are keyed by their {@code tenantId}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "vigilant")
public class VigilantProperties {

    private Map<String, TenantProperties> tenants = new LinkedHashMap<>();
    private ScoringProperties scoring = new ScoringProperties();

    @Getter
    @Setter
    public static class TenantProperties {
        private String callbackUrl;
    }

    @Getter
    @Setter
    public static class ScoringProperties {
        private double holdThreshold = 0.40;
        private double rejectThreshold = 0.75;
    }
}
