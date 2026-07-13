package com.turing.vigilant.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger-UI metadata. The whole API is on one Keycloak-backed OAuth2
 * scheme ({@code keycloak-oauth2}) with two grant flows:
 * <ul>
 *   <li><b>authorizationCode</b> (+ PKCE) — analysts on the dashboard endpoints
 *       ({@code /v1/cases}, {@code /v1/monitoring}, {@code /v1/tenants}).</li>
 *   <li><b>clientCredentials</b> — server-to-server host integration
 *       ({@code /v1/codes}, {@code /v1/events}, {@code /v1/decisions}) via a
 *       confidential service-account client.</li>
 * </ul>
 * Keycloak's auth/token URLs are derived from the same {@code issuer-uri} the
 * resource server validates against, so there is no second copy to drift.
 */
@Configuration
public class OpenApiConfiguration {

    private static final String OAUTH2_SCHEME = "keycloak-oauth2";

    private final String issuerUri;

    OpenApiConfiguration(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8081/realms/vigilant}") String issuerUri) {
        this.issuerUri = issuerUri;
    }

    @Bean
    OpenAPI vigilantOpenApi() {
        String base = issuerUri.replaceAll("/$", "");
        String authorizationUrl = base + "/protocol/openid-connect/auth";
        String tokenUrl = base + "/protocol/openid-connect/token";

        return new OpenAPI()
                .info(new Info()
                        .title("Vigilant API")
                        .version("v1")
                        .description("Graph-based referral-fraud detection. Gates the payout, "
                                + "never the signup. All calls are tenant-scoped and authenticated "
                                + "with a Keycloak token (analysts via authorization-code; host "
                                + "systems via client-credentials)."))
                .addSecurityItem(new SecurityRequirement().addList(OAUTH2_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(OAUTH2_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.OAUTH2)
                                        .description("Keycloak. Analysts: Authorization Code + PKCE "
                                                + "(dashboard endpoints). Host systems: Client "
                                                + "Credentials with a confidential service-account "
                                                + "client (codes/events/decisions endpoints).")
                                        .flows(new OAuthFlows()
                                                .authorizationCode(new OAuthFlow()
                                                        .authorizationUrl(authorizationUrl)
                                                        .tokenUrl(tokenUrl)
                                                        .scopes(new Scopes()
                                                                .addString("openid", "OpenID Connect")))
                                                .clientCredentials(new OAuthFlow()
                                                        .tokenUrl(tokenUrl)
                                                        .scopes(new Scopes())))));
    }
}
