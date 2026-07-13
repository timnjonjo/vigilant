package com.turing.vigilant.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    private static Jwt jwtWith(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(300))
                .claims(c -> c.putAll(claims))
                .build();
    }

    @Test
    void mapsRealmRolesToPrefixedAuthorities() {
        Jwt jwt = jwtWith(Map.of(
                "sub", "u1",
                "realm_access", Map.of("roles", List.of("fraud_analyst", "ops_viewer"))));

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_fraud_analyst", "ROLE_ops_viewer");
    }

    @Test
    void yieldsNoAuthoritiesWhenRealmAccessAbsent() {
        assertThat(converter.convert(jwtWith(Map.of("sub", "u1")))).isEmpty();
    }
}
