package com.turing.vigilant.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * One auth model for the whole API surface: every {@code /v1/**} endpoint is
 * behind a Keycloak-issued JWT (spec §7 resolved onto a single plane).
 * <ul>
 *   <li>Host integration ({@code /v1/codes}, {@code /v1/events},
 *       {@code /v1/decisions}) — server-to-server callers authenticate with a
 *       Keycloak <em>client-credentials</em> service-account token carrying the
 *       {@code host_integration} realm role.</li>
 *   <li>Dashboard ({@code /v1/cases}, {@code /v1/monitoring}, {@code /v1/tenants})
 *       — analysts authenticate with an authorization-code token carrying an
 *       analyst/ops/admin role.</li>
 * </ul>
 * Both planes carry a {@code tenant_id} claim; per-request tenant isolation is
 * enforced in-controller by {@link TenantAccessGuard}. Non-API paths (docs,
 * actuator health) stay open on the fallback chain.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/v1/**")
                .authorizeHttpRequests(auth -> auth
                        // Host integration (machine-to-machine, client-credentials).
                        .requestMatchers("/v1/codes/**", "/v1/events/**", "/v1/decisions/**")
                        .hasRole("host_integration")
                        // Campaign management: only tenant admins create/edit campaigns…
                        .requestMatchers(HttpMethod.POST, "/v1/campaigns").hasRole("tenant_admin")
                        .requestMatchers(HttpMethod.PATCH, "/v1/campaigns/**").hasRole("tenant_admin")
                        // …but any authenticated caller may read them (dashboard filters, host pull).
                        .requestMatchers(HttpMethod.GET, "/v1/campaigns", "/v1/campaigns/**").authenticated()
                        // Case management: analysts and tenant admins. ops_viewer is excluded.
                        .requestMatchers("/v1/cases/**").hasAnyRole("fraud_analyst", "tenant_admin")
                        // Aggregate monitoring: ops viewers too.
                        .requestMatchers("/v1/monitoring/**")
                        .hasAnyRole("ops_viewer", "fraud_analyst", "tenant_admin")
                        // Own-tenant lookup: any authenticated dashboard user.
                        .requestMatchers("/v1/tenants").authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain defaultSecurity(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }
}
