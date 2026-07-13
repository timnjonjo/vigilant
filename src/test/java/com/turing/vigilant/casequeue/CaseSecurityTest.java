package com.turing.vigilant.casequeue;

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

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Enforcement of the dashboard auth config — testing our SecurityConfig + guard,
 * not Keycloak. Tokens are synthesised with crafted claims/roles.
 */
@WebMvcTest(CaseController.class)
@Import({SecurityConfig.class, TenantAccessGuard.class, ApiExceptionHandler.class})
class CaseSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    FraudCaseRepository repository;
    @MockitoBean
    CaseResolutionService resolutionService;
    @MockitoBean
    com.turing.vigilant.graph.GraphStore graphStore;
    @MockitoBean
    JwtDecoder jwtDecoder; // present so the resource-server chain builds; unused with jwt() post-processor

    private static RequestPostProcessor analyst(String tenantId) {
        return jwt().jwt(j -> j.claim("tenant_id", tenantId))
                .authorities(new SimpleGrantedAuthority("ROLE_fraud_analyst"));
    }

    private static RequestPostProcessor opsViewer(String tenantId) {
        return jwt().jwt(j -> j.claim("tenant_id", tenantId))
                .authorities(new SimpleGrantedAuthority("ROLE_ops_viewer"));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/v1/cases").param("tenantId", "loob-bank"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenForOneTenantCannotReadAnothersCases() throws Exception {
        // Valid analyst role, but for the wrong tenant → forbidden, no data touched.
        mockMvc.perform(get("/v1/cases").with(analyst("loob-bank")).param("tenantId", "acme-sacco"))
                .andExpect(status().isForbidden());
    }

    @Test
    void opsViewerCannotReadCases() throws Exception {
        mockMvc.perform(get("/v1/cases").with(opsViewer("loob-bank")).param("tenantId", "loob-bank"))
                .andExpect(status().isForbidden());
    }

    @Test
    void opsViewerCannotResolveACase() throws Exception {
        mockMvc.perform(post("/v1/cases/1/resolve")
                        .with(opsViewer("loob-bank"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"loob-bank","resolution":"REJECT","resolvedBy":"x"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void analystForMatchingTenantCanReadCases() throws Exception {
        when(repository.findByTenantIdAndStatusOrderByOpenedAtDesc("loob-bank", CaseStatus.OPEN))
                .thenReturn(List.of());

        mockMvc.perform(get("/v1/cases")
                        .with(analyst("loob-bank"))
                        .param("tenantId", "loob-bank")
                        .param("status", "OPEN"))
                .andExpect(status().isOk());
    }
}
