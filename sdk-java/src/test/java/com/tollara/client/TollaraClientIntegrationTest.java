package com.tollara.client;

import com.tollara.client.model.UsageReportResponse;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigDecimal;
import java.net.http.HttpClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.junit5.WireMockExtension.newInstance;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Single-host layout: Core, Usage, and Gateway paths on one origin (see {@link TollaraClient}).
 */
class TollaraClientIntegrationTest {

    private static final String SERVICE_KEY = "k";
    private static final String SERVICE_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String SERVICE_SECRET = "test-service-secret";

    @RegisterExtension
    static WireMockExtension wireMock = newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private String baseUrl;
    private TollaraClient client;

    @BeforeEach
    void setUp() {
        int port = wireMock.getPort();
        baseUrl = "http://localhost:" + port;
        client = TollaraClient.builder()
                .apiUrl(baseUrl)
                .serviceId(SERVICE_ID)
                .serviceSecret(SERVICE_SECRET)
                .httpClient(HttpClient.newHttpClient())
                .build();
    }

    @Test
    void getRequestStatus_usesDefaultGatewayPrefix() {
        wireMock.stubFor(
                get(urlPathEqualTo("/api/requests/job-1/status"))
                        .withHeader("Authorization", equalTo("Bearer " + SERVICE_KEY))
                        .willReturn(aResponse().withStatus(200).withBody("{\"state\":\"OK\"}")));

        GatewayHttpResponse res = client.getRequestStatus("job-1", SERVICE_KEY);
        assertThat(res.getStatusCode()).isEqualTo(200);
        assertThat(res.getBody()).contains("OK");
    }

    @Test
    void reportUsage_usesDefaultUsagePrefix() {
        wireMock.stubFor(
                post(urlPathEqualTo("/api/usage/report"))
                        .withHeader("X-Tollara-Signature", matching(".+"))
                        .withHeader("X-Tollara-Timestamp", matching("\\d+"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                        "{\"status\":\"ok\",\"warning\":null,\"isOverLimit\":false,\"remainingRequestsPerPeriod\":1,\"remainingTimeUnitsPerPeriod\":null,\"remainingSpendingCap\":null,\"overageRate\":null}")));

        UsageReportResponse resp = client.reportUsage("user-1", SERVICE_ID, BigDecimal.ONE);
        assertThat(resp.getStatus()).isEqualTo("ok");
    }

    @Test
    void customUsagePathPrefix_isUsedForReport() {
        TollaraClient custom = TollaraClient.builder()
                .apiUrl(baseUrl)
                .usagePathPrefix("/usage/api/v1")
                .serviceId(SERVICE_ID)
                .serviceSecret(SERVICE_SECRET)
                .httpClient(HttpClient.newHttpClient())
                .build();

        wireMock.stubFor(
                post(urlPathEqualTo("/usage/api/v1/report"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                        "{\"status\":\"ok\",\"warning\":null,\"isOverLimit\":false,\"remainingRequestsPerPeriod\":1,\"remainingTimeUnitsPerPeriod\":null,\"remainingSpendingCap\":null,\"overageRate\":null}")));

        UsageReportResponse resp = custom.reportUsage("user-1", SERVICE_ID, BigDecimal.ONE);
        assertThat(resp.getStatus()).isEqualTo("ok");
    }
}
