package com.turing.vigilant.webhook;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/** Provides the shared HTTP client used for outbound webhooks. */
@Configuration
public class WebhookConfiguration {

    @Bean
    HttpClient webhookHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }
}
