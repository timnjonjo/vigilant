package com.turing.vigilant.events;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@Import({SecurityConfig.class, TenantAccessGuard.class, ApiExceptionHandler.class})
class EventControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    EventIngestionService ingestionService;
    @MockitoBean
    JwtDecoder jwtDecoder; // present so the resource-server chain builds; unused with jwt() post-processor

    private static RequestPostProcessor host(String tenantId) {
        return jwt().jwt(j -> j.claim("tenant_id", tenantId))
                .authorities(new SimpleGrantedAuthority("ROLE_host_integration"));
    }

    @Test
    void redemptionAlwaysAccepts() throws Exception {
        String body = """
                {"tenantId":"loob-bank","campaignId":"camp-1","referralCode":"LOOB-R1","newUserId":"u2",
                 "deviceId":"d2","ipAddress":"10.0.0.2","timestamp":"2026-07-10T10:00:00Z"}
                """;

        mockMvc.perform(post("/v1/events/redemption")
                        .with(host("loob-bank"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("ACCEPT"));

        verify(ingestionService).recordRedemption(any(), any(), any(), eq("u2"), any(), any(), any());
    }

    @Test
    void conversionIsAccepted() throws Exception {
        String body = """
                {"tenantId":"loob-bank","campaignId":"camp-1","referralCode":"LOOB-R1","refereeUserId":"u2",
                 "conversionType":"DEPOSIT","timestamp":"2026-07-10T11:00:00Z"}
                """;

        mockMvc.perform(post("/v1/events/conversion")
                        .with(host("loob-bank"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        verify(ingestionService).recordConversion(any(), any(), any(), eq("u2"), eq("DEPOSIT"), any());
    }

    @Test
    void rejectsUnauthenticatedRedemption() throws Exception {
        String body = """
                {"tenantId":"loob-bank","campaignId":"camp-1","referralCode":"LOOB-R1","newUserId":"u2"}
                """;

        mockMvc.perform(post("/v1/events/redemption")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenForAnotherTenant() throws Exception {
        String body = """
                {"tenantId":"loob-bank","campaignId":"camp-1","referralCode":"LOOB-R1","newUserId":"u2",
                 "deviceId":"d2","ipAddress":"10.0.0.2","timestamp":"2026-07-10T10:00:00Z"}
                """;

        mockMvc.perform(post("/v1/events/redemption")
                        .with(host("acme-sacco"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsBlankRequiredEventFieldsBeforeIngestion() throws Exception {
        mockMvc.perform(post("/v1/events/redemption")
                        .with(host("loob-bank"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"loob-bank","campaignId":"camp-1","referralCode":" ","newUserId":"u2"}
                                """))
                .andExpect(status().isBadRequest());

        verify(ingestionService, never()).recordRedemption(any(), any(), any(), any(), any(), any(), any());
    }
}
