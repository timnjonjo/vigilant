package com.turing.vigilant.webhook;

import com.turing.vigilant.shared.TenantId;
import com.turing.vigilant.tenant.TenantConfig;
import com.turing.vigilant.tenant.TenantRegistry;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Delivers async decision webhooks to the host's per-tenant callback URL. I/O is
 * wrapped as {@link UncheckedIOException} at this boundary rather than declaring
 * checked exceptions upward.
 */
@Component
public class WebhookDispatcher {

    private final TenantRegistry tenantRegistry;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WebhookDispatcher(TenantRegistry tenantRegistry, HttpClient httpClient, ObjectMapper objectMapper) {
        this.tenantRegistry = tenantRegistry;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public void dispatch(TenantId tenantId, ResolutionCallback callback) {
        TenantConfig tenant = tenantRegistry.require(tenantId);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tenant.callbackUrl()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(serialise(callback)))
                .build();
        try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException("failed to deliver webhook to " + tenant.callbackUrl(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebhookDeliveryException("interrupted delivering webhook", e);
        }
    }

    private String serialise(ResolutionCallback callback) {
        // Jackson 3 throws an unchecked JacksonException, so no checked wrapping here.
        return objectMapper.writeValueAsString(callback);
    }
}
