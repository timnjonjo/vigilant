package com.turing.vigilant;

import com.turing.vigilant.ipreputation.AsnResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.InetAddress;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end vertical slice against real Neo4j and Postgres: issuance → redemption
 * → conversion → payout decision, with a device collision driving a HOLD that
 * lands in the case queue. Also serves as the context-load smoke test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class VigilantApplicationTests {

    private static final String DATACENTER_IP = "203.0.113.7";

    /**
     * Replaces the real {@code .mmdb}-backed resolver (no database file in tests).
     * {@code @MockitoBean} swaps the bean definition, so {@code MmdbAsnResolver}
     * is never constructed and cannot fail-fast on the missing database.
     */
    @MockitoBean
    AsnResolver asnResolver;

    @org.springframework.beans.factory.annotation.Autowired
    com.turing.vigilant.campaign.CampaignService campaignService;

    /** An ACTIVE campaign every host call in this slice runs against (spec §10a). */
    private String campaignId;

    @BeforeEach
    void stubResolver() {
        // Only the datacenter IP resolves to a cloud ASN (AWS 16509); anything
        // else is unresolved -> UNKNOWN, leaving the other slice's signals intact.
        when(asnResolver.resolveAsn(any())).thenAnswer(invocation -> {
            InetAddress address = invocation.getArgument(0);
            return DATACENTER_IP.equals(address.getHostAddress())
                    ? Optional.of(16509L)
                    : Optional.empty();
        });

        campaignId = campaignService.create(
                com.turing.vigilant.shared.TenantId.of("loob-bank"), "E2E Slice",
                new java.math.BigDecimal("350.00"), null, null,
                com.turing.vigilant.campaign.CampaignStatus.ACTIVE,
                com.turing.vigilant.campaign.ConversionCriteria.FIRST_DEPOSIT, null)
                .getCampaignId();
    }

    @Container
    @ServiceConnection
    static final Neo4jContainer<?> NEO4J = new Neo4jContainer<>("neo4j:5.26");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void openApiSpecIsServedAndDocumentsTheKeycloakScheme() throws Exception {
        // The docs endpoint itself is open (no token required).
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Vigilant API"))
                .andExpect(jsonPath("$.paths['/v1/decisions/payout-check']").exists())
                .andExpect(jsonPath("$.components.securitySchemes['keycloak-oauth2'].type").value("oauth2"))
                .andExpect(jsonPath("$.components.securitySchemes.tenantApiKey").doesNotExist());
    }

    @Test
    void runsFullSliceAndOpensAHoldCaseOnDeviceCollision() throws Exception {
        // 1. Referrer requests a code — always issued.
        String referralCode = issueCode("referrer-1", "device-referrer", "10.0.0.1");

        // 2. Two referees redeem sharing ONE device but from distinct /24 subnets,
        //    so the only overlap signal is the device collision (a clean HOLD).
        redeem(referralCode, "referee-A", "shared-device", "172.16.5.2", "2026-07-10T10:00:00Z");
        redeem(referralCode, "referee-B", "shared-device", "192.168.9.3", "2026-07-10T10:05:00Z");

        // 3. Referee A completes a qualifying action.
        convert(referralCode, "referee-A", "DEPOSIT", "2026-07-10T11:00:00Z");

        // 4. Payout check -> HOLD, with the device-collision reason and a case id.
        String payout = mockMvc.perform(post("/v1/decisions/payout-check")
                        .with(hostFor("loob-bank"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"loob-bank","campaignId":"%s","referralCode":"%s","refereeUserId":"referee-A"}
                                """.formatted(campaignId, referralCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("HOLD"))
                .andExpect(jsonPath("$.reasonCodes[0]").value("DEVICE_COLLISION"))
                .andExpect(jsonPath("$.caseId").isNumber())
                .andReturn().getResponse().getContentAsString();
        int caseId = com.jayway.jsonpath.JsonPath.read(payout, "$.caseId");

        // 5. The HOLD case is visible in the queue for the dashboard (Keycloak plane).
        mockMvc.perform(get("/v1/cases")
                        .with(analystFor("loob-bank"))
                        .param("tenantId", "loob-bank")
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].refereeUserId").value("referee-A"))
                .andExpect(jsonPath("$[0].decision").value("HOLD"))
                .andExpect(jsonPath("$[0].status").value("OPEN"));

        // 6. The case's subgraph renders for the analyst's graph explorer: the two
        //    referees appear as nodes and the shared-device edge is present.
        mockMvc.perform(get("/v1/cases/" + caseId + "/graph")
                        .with(analystFor("loob-bank"))
                        .param("tenantId", "loob-bank"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[?(@.userId=='referee-A')]").exists())
                .andExpect(jsonPath("$.edges[?(@.type=='SHARES_DEVICE')]").exists());
    }

    @Test
    void datacenterIpAtRedemptionDrivesAHoldAtPayout() throws Exception {
        // Referrer on a normal IP; a single referee redeems from a datacenter IP,
        // distinct device and subnet, so the datacenter signal is the only one.
        String referralCode = issueCode("referrer-dc", "device-referrer-dc", "10.1.0.1");
        redeem(referralCode, "referee-dc", "device-dc", DATACENTER_IP, "2026-07-10T10:00:00Z");
        convert(referralCode, "referee-dc", "DEPOSIT", "2026-07-10T11:00:00Z");

        mockMvc.perform(post("/v1/decisions/payout-check")
                        .with(hostFor("loob-bank"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"loob-bank","campaignId":"%s","referralCode":"%s","refereeUserId":"referee-dc"}
                                """.formatted(campaignId, referralCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("HOLD"))
                .andExpect(jsonPath("$.reasonCodes[0]").value("DATACENTER_OR_VPN_IP"))
                .andExpect(jsonPath("$.caseId").isNumber());
    }

    /** A Keycloak analyst token for the given tenant (dashboard plane). */
    private static RequestPostProcessor analystFor(String tenantId) {
        return jwt()
                .jwt(j -> j.claim("tenant_id", tenantId))
                .authorities(new SimpleGrantedAuthority("ROLE_fraud_analyst"));
    }

    /** A Keycloak client-credentials token for the given tenant (host plane). */
    private static RequestPostProcessor hostFor(String tenantId) {
        return jwt()
                .jwt(j -> j.claim("tenant_id", tenantId))
                .authorities(new SimpleGrantedAuthority("ROLE_host_integration"));
    }

    private String issueCode(String userId, String deviceId, String ip) throws Exception {
        String response = mockMvc.perform(post("/v1/codes/generate")
                        .with(hostFor("loob-bank"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"loob-bank","campaignId":"%s","userId":"%s","deviceId":"%s","ipAddress":"%s"}
                                """.formatted(campaignId, userId, deviceId, ip)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.referralCode");
    }

    private void redeem(String code, String userId, String deviceId, String ip, String ts) throws Exception {
        mockMvc.perform(post("/v1/events/redemption")
                        .with(hostFor("loob-bank"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"loob-bank","campaignId":"%s","referralCode":"%s","newUserId":"%s",
                                 "deviceId":"%s","ipAddress":"%s","timestamp":"%s"}
                                """.formatted(campaignId, code, userId, deviceId, ip, ts)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("ACCEPT"));
    }

    private void convert(String code, String userId, String type, String ts) throws Exception {
        mockMvc.perform(post("/v1/events/conversion")
                        .with(hostFor("loob-bank"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"loob-bank","campaignId":"%s","referralCode":"%s","refereeUserId":"%s",
                                 "conversionType":"%s","timestamp":"%s"}
                                """.formatted(campaignId, code, userId, type, ts)))
                .andExpect(status().isAccepted());
    }
}
