package com.agentvend.client;

import com.agentvend.client.model.CompletionStatus;
import com.agentvend.client.model.UsageReportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestTemplate;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static com.github.tomakehurst.wiremock.junit5.WireMockExtension.newInstance;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Integration tests for UsageServiceClient against WireMock stubs of the AgentVend Usage API
 * (see docs/sdk-api-spec.md §3).
 */
class UsageServiceClientIntegrationTest {

    private static final String AGENT_SECRET = "test-agent-secret";

    @RegisterExtension
    static WireMockExtension wireMock = newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private UsageServiceClient client;
    private String usageBaseUrl;

    @BeforeEach
    void setUp() {
        int port = wireMock.getPort();
        usageBaseUrl = "http://localhost:" + port;
        client = new UsageServiceClient(usageBaseUrl, AGENT_SECRET, new RestTemplate());
    }

    @Test
    void reportUsage_sendsSignedRequest_andReturnsResponse() {
        wireMock.stubFor(
                post(urlPathEqualTo("/api/usage/report"))
                        .withHeader("Content-Type", containing("application/json"))
                        .withHeader("X-AgentVend-Signature", matching(".+"))
                        .withHeader("X-AgentVend-Timestamp", matching("\\d+"))
                        .withRequestBody(matchingJsonPath("$.userId", equalTo("user-1")))
                        .withRequestBody(matchingJsonPath("$.agentId", equalTo("agent-1")))
                        .withRequestBody(matchingJsonPath("$.unitsUsed"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                    {"status":"ok","warning":null,"isOverLimit":false,"remainingRequestsPerPeriod":99,"remainingTimeUnitsPerPeriod":null,"remainingSpendingCap":null,"overageRate":null}
                                    """))
        );

        UsageReportResponse response = client.reportUsage("user-1", "agent-1", BigDecimal.ONE);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("ok");
        assertThat(response.isOverLimit()).isFalse();
        assertThat(response.getRemainingRequestsPerPeriod()).isEqualTo(99L);
    }

    @Test
    void reportUsage_throwsWhenServiceReturnsNon2xx() {
        wireMock.stubFor(
                post(urlPathEqualTo("/api/usage/report"))
                        .willReturn(aResponse().withStatus(500).withBody("Internal error"))
        );

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.client.RestClientException.class,
                () -> client.reportUsage("user-1", "agent-1", BigDecimal.ONE)
        );
    }

    @Test
    void sendProgressUpdate_postsToProgressUrlWithSignature() {
        String requestId = "req-123";
        String timestamp = "1700000000";
        String progressPath = "/api/usage/progress/" + requestId;
        wireMock.stubFor(
                post(urlPathEqualTo(progressPath))
                        .withHeader("X-AgentVend-Signature", matching(".+"))
                        .withHeader("X-AgentVend-Timestamp", equalTo(timestamp))
                        .withRequestBody(matchingJsonPath("$.stage", equalTo("processing")))
                        .withRequestBody(matchingJsonPath("$.percentageComplete"))
                        .willReturn(aResponse().withStatus(200))
        );

        String progressUrl = usageBaseUrl + progressPath + "?signature=ignored&timestamp=" + timestamp;
        boolean ok = client.sendProgressUpdate(progressUrl, requestId, "processing", 50);

        assertThat(ok).isTrue();
    }

    @Test
    void sendCompletion_postsToCallbackUrlWithSignature() {
        String requestId = "req-456";
        String timestamp = "1700000001";
        String completePath = "/api/usage/complete/" + requestId;
        wireMock.stubFor(
                post(urlPathEqualTo(completePath))
                        .withHeader("X-AgentVend-Signature", matching(".+"))
                        .withHeader("X-AgentVend-Timestamp", equalTo(timestamp))
                        .withRequestBody(matchingJsonPath("$.status", equalTo("COMPLETED")))
                        .withRequestBody(matchingJsonPath("$.units"))
                        .willReturn(aResponse().withStatus(200))
        );

        String callbackUrl = usageBaseUrl + completePath + "?signature=ignored&timestamp=" + timestamp;
        boolean ok = client.sendCompletion(callbackUrl, requestId, CompletionStatus.COMPLETED, "done", BigDecimal.ONE);

        assertThat(ok).isTrue();
    }

    @Test
    void sendProgressUpdate_returnsFalse_whenUrlMissingTimestamp() {
        String progressUrl = usageBaseUrl + "/api/usage/progress/req-1";
        boolean ok = client.sendProgressUpdate(progressUrl, "req-1", "stage", 0);
        assertThat(ok).isFalse();
    }
}
