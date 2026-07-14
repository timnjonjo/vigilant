package com.turing.vigilant.casequeue;

import com.turing.vigilant.web.ApiExceptionHandler;
import com.turing.vigilant.web.SecurityConfig;
import com.turing.vigilant.web.TenantAccessGuard;
import com.turing.vigilant.shared.TenantId;
import com.turing.vigilant.web.pagination.CursorPage;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
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
    CasePageService casePageService;
    @MockitoBean
    AuditPageService auditPageService;
    @MockitoBean
    CaseResolutionService resolutionService;
    @MockitoBean
    com.turing.vigilant.graph.GraphStore graphStore;
    @MockitoBean
    JwtDecoder jwtDecoder; // present so the resource-server chain builds; unused with jwt() post-processor

    private static RequestPostProcessor analyst(String tenantId) {
        return jwt().jwt(j -> j.subject("subject-123")
                        .claim("tenant_id", tenantId)
                        .claim("preferred_username", "analyst-loob"))
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
        when(casePageService.page(
                "loob-bank", CaseStatus.OPEN, null, null, null, "score", null, 25))
                .thenReturn(new CursorPage<>(List.of(), null));

        mockMvc.perform(get("/v1/cases")
                        .with(analyst("loob-bank"))
                        .param("tenantId", "loob-bank")
                        .param("status", "OPEN"))
                .andExpect(status().isOk());
    }

    @Test
    void resolutionAuditActorComesFromTheValidatedToken() throws Exception {
        FraudCase fraudCase = mock(FraudCase.class);
        when(resolutionService.resolve(eq(TenantId.of("loob-bank")), eq(1L), eq(com.turing.vigilant.shared.Decision.REJECT), eq("analyst-loob")))
                .thenReturn(fraudCase);

        mockMvc.perform(post("/v1/cases/1/resolve")
                        .with(analyst("loob-bank"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"loob-bank","resolution":"REJECT","resolvedBy":"forged-admin"}
                                """))
                .andExpect(status().isOk());

        verify(resolutionService).resolve(
                TenantId.of("loob-bank"), 1L, com.turing.vigilant.shared.Decision.REJECT, "analyst-loob");
    }
}
