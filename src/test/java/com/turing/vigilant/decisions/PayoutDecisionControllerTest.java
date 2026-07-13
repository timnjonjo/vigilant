package com.turing.vigilant.decisions;

import com.turing.vigilant.decisions.PayoutDecisionService.PayoutDecision;
import com.turing.vigilant.shared.Decision;
import com.turing.vigilant.shared.ReasonCode;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PayoutDecisionController.class)
@Import({SecurityConfig.class, TenantAccessGuard.class, ApiExceptionHandler.class})
class PayoutDecisionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PayoutDecisionService decisionService;
    @MockitoBean
    JwtDecoder jwtDecoder; // present so the resource-server chain builds; unused with jwt() post-processor

    private static final String BODY = """
            {"tenantId":"loob-bank","campaignId":"camp-1","referralCode":"LOOB-R1","refereeUserId":"u2"}
            """;

    private static RequestPostProcessor host(String tenantId) {
        return jwt().jwt(j -> j.claim("tenant_id", tenantId))
                .authorities(new SimpleGrantedAuthority("ROLE_host_integration"));
    }

    @Test
    void returnsApproveWithNoReasons() throws Exception {
        when(decisionService.decide(any(), any(), any(), any()))
                .thenReturn(new PayoutDecision(Decision.APPROVE, 0.1, List.of(), null));

        mockMvc.perform(post("/v1/decisions/payout-check")
                        .with(host("loob-bank"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("APPROVE"))
                .andExpect(jsonPath("$.score").value(0.1))
                .andExpect(jsonPath("$.reasonCodes").isEmpty());
    }

    @Test
    void returnsHoldWithReasonCodesAndCaseId() throws Exception {
        when(decisionService.decide(any(), any(), any(), any()))
                .thenReturn(new PayoutDecision(
                        Decision.HOLD, 0.5, List.of(ReasonCode.DEVICE_COLLISION), 42L));

        mockMvc.perform(post("/v1/decisions/payout-check")
                        .with(host("loob-bank"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("HOLD"))
                .andExpect(jsonPath("$.reasonCodes[0]").value("DEVICE_COLLISION"))
                .andExpect(jsonPath("$.caseId").value(42));
    }

    @Test
    void rejectsMissingToken() throws Exception {
        mockMvc.perform(post("/v1/decisions/payout-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenForAnotherTenant() throws Exception {
        mockMvc.perform(post("/v1/decisions/payout-check")
                        .with(host("acme-sacco"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }
}
