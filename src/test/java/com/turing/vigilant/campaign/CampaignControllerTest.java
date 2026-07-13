package com.turing.vigilant.campaign;

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

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Campaign management authorization: writes require {@code tenant_admin}; reads are
 * open to any authenticated caller; everything is tenant-scoped by the token claim.
 */
@WebMvcTest(CampaignController.class)
@Import({SecurityConfig.class, TenantAccessGuard.class, ApiExceptionHandler.class})
class CampaignControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CampaignService campaignService;
    @MockitoBean
    JwtDecoder jwtDecoder;

    private static RequestPostProcessor role(String tenantId, String role) {
        return jwt().jwt(j -> j.claim("tenant_id", tenantId))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static final String CREATE_BODY = """
            {"tenantId":"loob-bank","name":"Q3 Boost","bonusAmount":350,"conversionCriteria":"FIRST_DEPOSIT"}
            """;

    private Campaign stubCampaign() {
        return Campaign.create("loob-bank", "Q3 Boost", new BigDecimal("350.00"), null, null,
                CampaignStatus.ACTIVE, ConversionCriteria.FIRST_DEPOSIT, null, Instant.parse("2026-07-11T00:00:00Z"));
    }

    @Test
    void tenantAdminCanCreateACampaign() throws Exception {
        when(campaignService.create(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(stubCampaign());
        mockMvc.perform(post("/v1/campaigns").with(role("loob-bank", "tenant_admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void analystCannotCreateACampaign() throws Exception {
        mockMvc.perform(post("/v1/campaigns").with(role("loob-bank", "fraud_analyst"))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void opsViewerCannotPatchACampaign() throws Exception {
        mockMvc.perform(patch("/v1/campaigns/c1").with(role("loob-bank", "ops_viewer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"loob-bank","status":"PAUSED"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void analystMayReadCampaignsForTheFilter() throws Exception {
        when(campaignService.list(any())).thenReturn(java.util.List.of(stubCampaign()));
        mockMvc.perform(get("/v1/campaigns").with(role("loob-bank", "fraud_analyst"))
                        .param("tenantId", "loob-bank"))
                .andExpect(status().isOk());
    }

    @Test
    void createForAnotherTenantIsForbidden() throws Exception {
        // tenant_admin role, but the token is for a different tenant than the body.
        mockMvc.perform(post("/v1/campaigns").with(role("acme-sacco", "tenant_admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/v1/campaigns").param("tenantId", "loob-bank"))
                .andExpect(status().isUnauthorized());
    }
}
