package com.turing.vigilant.codes;

import com.turing.vigilant.codes.CodeIssuanceService.IssuedCode;
import com.turing.vigilant.shared.ReferralCode;
import com.turing.vigilant.web.ApiExceptionHandler;
import com.turing.vigilant.web.SecurityConfig;
import com.turing.vigilant.web.TenantAccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Host integration is on the Keycloak plane: callers present a client-credentials
 * token carrying the {@code host_integration} role and a {@code tenant_id} claim
 * that must match the request body.
 */
@WebMvcTest(CodeGenerationController.class)
@Import({SecurityConfig.class, TenantAccessGuard.class, ApiExceptionHandler.class})
class CodeGenerationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CodeIssuanceService issuanceService;
    @MockitoBean
    JwtDecoder jwtDecoder; // present so the resource-server chain builds; unused with jwt() post-processor

    private static final String BODY = """
            {"tenantId":"loob-bank","campaignId":"camp-1","userId":"u1","deviceId":"d1","ipAddress":"10.0.0.1"}
            """;

    private static RequestPostProcessor host(String tenantId) {
        return jwt().jwt(j -> j.claim("tenant_id", tenantId))
                .authorities(new SimpleGrantedAuthority("ROLE_host_integration"));
    }

    @Test
    void issuesCodeWithRiskFlag() throws Exception {
        when(issuanceService.issue(any(), any(), any(), any(), any()))
                .thenReturn(new IssuedCode(ReferralCode.of("LOOB-ABC123"), true));

        mockMvc.perform(post("/v1/codes/generate")
                        .with(host("loob-bank"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.referralCode").value("LOOB-ABC123"))
                .andExpect(jsonPath("$.riskFlag").value(true));
    }

    @Test
    void rejectsMissingToken() throws Exception {
        mockMvc.perform(post("/v1/codes/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenWithoutHostIntegrationRole() throws Exception {
        RequestPostProcessor analyst = jwt().jwt(j -> j.claim("tenant_id", "loob-bank"))
                .authorities(new SimpleGrantedAuthority("ROLE_fraud_analyst"));
        mockMvc.perform(post("/v1/codes/generate")
                        .with(analyst)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsTokenForAnotherTenant() throws Exception {
        mockMvc.perform(post("/v1/codes/generate")
                        .with(host("acme-sacco"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsBlankRequiredFieldsBeforeIssuance() throws Exception {
        mockMvc.perform(post("/v1/codes/generate")
                        .with(host("loob-bank"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"loob-bank","campaignId":"camp-1","userId":" "}
                                """))
                .andExpect(status().isBadRequest());

        verify(issuanceService, never()).issue(any(), any(), any(), any(), any());
    }
}
