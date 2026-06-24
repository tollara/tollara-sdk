package com.tollara.client;

import com.tollara.client.model.CompletionStatus;
import com.tollara.client.model.UsageCallbackResult;
import com.tollara.client.model.UsageReportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import java.math.BigDecimal;
import java.net.http.HttpClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static com.github.tomakehurst.wiremock.junit5.WireMockExtension.newInstance;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Integration tests for UsageServiceClient against WireMock stubs of the Tollara Usage API
 * (see docs/sdk-api-spec.md §3).
 */
class UsageServiceClientIntegrationTest {

    private static final String SERVICE_SECRET = "test-service-secret";

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
        client = new UsageServiceClient(usageBaseUrl, SERVICE_SECRET, HttpClient.newHttpClient());
    }

    @Test
    void reportUsage_sendsSignedRequest_andReturnsResponse() {
        wireMock.stubFor(
                post(urlPathEqualTo("/api/usage/report"))
                        .withHeader("Content-Type", containing("application/json"))
                        .withHeader("X-Tollara-Signature", matching(".+"))
                        .withHeader("X-Tollara-Timestamp", matching("\\d+"))
                        .withRequestBody(matchingJsonPath("$.userId", equalTo("user-1")))
                        .withRequestBody(matchingJsonPath("$.serviceId", equalTo("svc-1")))
                        .withRequestBody(matchingJsonPath("$.unitsUsed"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                    {"status":"ok","warning":null,"isOverLimit":false,"remainingRequestsPerPeriod":99,"remainingTimeUnitsPerPeriod":null,"remainingSpendingCap":null,"overageRate":null}
                                    """))
        );

        UsageReportResponse response = client.reportUsage("user-1", "svc-1", BigDecimal.ONE);

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
                TollaraHttpException.class,
                () -> client.reportUsage("user-1", "svc-1", BigDecimal.ONE)
        );
    }

    @Test
    void sendProgressUpdate_postsToProgressUrlWithSignature() {
        String requestId = "req-123";
        String timestamp = "1700000000";
        String progressPath = "/api/usage/progress/" + requestId;
        wireMock.stubFor(
                post(urlPathEqualTo(progressPath))
                        .withHeader("X-Tollara-Signature", matching(".+"))
                        .withHeader("X-Tollara-Timestamp", equalTo(timestamp))
                        .withRequestBody(matchingJsonPath("$.stage", equalTo("processing")))
                        .withRequestBody(matchingJsonPath("$.percentageComplete"))
                        .willReturn(aResponse().withStatus(200))
        );

        String progressUrl = usageBaseUrl + progressPath + "?signature=ignored&timestamp=" + timestamp;
        UsageCallbackResult result = client.sendProgressUpdate(progressUrl, requestId, "processing", 50);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getHttpStatus()).isEqualTo(200);
    }

    @Test
    void sendCompletion_postsToCallbackUrlWithSignature() {
        String requestId = "req-456";
        String timestamp = "1700000001";
        String completePath = "/api/usage/complete/" + requestId;
        wireMock.stubFor(
                post(urlPathEqualTo(completePath))
                        .withHeader("X-Tollara-Signature", matching(".+"))
                        .withHeader("X-Tollara-Timestamp", equalTo(timestamp))
                        .withRequestBody(matchingJsonPath("$.status", equalTo("COMPLETED")))
                        .withRequestBody(matchingJsonPath("$.units"))
                        .willReturn(aResponse().withStatus(200))
        );

        String callbackUrl = usageBaseUrl + completePath + "?signature=ignored&timestamp=" + timestamp;
        UsageCallbackResult result = client.sendCompletion(callbackUrl, requestId, CompletionStatus.COMPLETED, "done", BigDecimal.ONE);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getHttpStatus()).isEqualTo(200);
    }

    @Test
    void sendCompletion_usesStatusTimestampUnitsFieldOrder() {
        String requestId = "req-order";
        String timestamp = "1700000002";
        String completePath = "/api/usage/complete/" + requestId;
        wireMock.stubFor(
                post(urlPathEqualTo(completePath))
                        .willReturn(aResponse().withStatus(200))
        );

        String callbackUrl = usageBaseUrl + completePath + "?timestamp=" + timestamp;
        client.sendCompletion(callbackUrl, requestId, CompletionStatus.COMPLETED, "done", BigDecimal.ONE);

        String body = wireMock.getAllServeEvents().get(0).getRequest().getBodyAsString();
        int statusIdx = body.indexOf("\"status\"");
        int timestampIdx = body.indexOf("\"timestamp\"");
        int unitsIdx = body.indexOf("\"units\"");
        int resultIdx = body.indexOf("\"result\"");
        assertThat(statusIdx).isLessThan(timestampIdx);
        assertThat(timestampIdx).isLessThan(unitsIdx);
        assertThat(unitsIdx).isLessThan(resultIdx);
    }

    @Test
    void sendProgressUpdate_returnsFailure_whenUrlMissingTimestamp() {
        String progressUrl = usageBaseUrl + "/api/usage/progress/req-1";
        UsageCallbackResult result = client.sendProgressUpdate(progressUrl, "req-1", "stage", 0);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getHttpStatus()).isZero();
    }

    @Test
    void sendProgressUpdate_returnsFailure_whenUrlNull() {
        UsageCallbackResult result = client.sendProgressUpdate(null, "req-1", "stage", 0);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getHttpStatus()).isZero();
        assertThat(result.getHttpStatusText()).isEqualTo("Missing or invalid callback/progress URL");
    }

    @Test
    void sendProgressUpdate_returnsHttpStatusAndBody_onFailure() {
        String requestId = "req-1";
        String progressPath = "/api/usage/progress/" + requestId;
        wireMock.stubFor(
                post(urlPathEqualTo(progressPath))
                        .willReturn(aResponse()
                                .withStatus(404)
                                .withBody("Invalid requestId: req-1"))
        );

        String progressUrl = usageBaseUrl + progressPath + "?timestamp=1700000000";
        UsageCallbackResult result = client.sendProgressUpdate(progressUrl, requestId, "processing", 25);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getHttpStatus()).isEqualTo(404);
        assertThat(result.getResponseBody()).isEqualTo("Invalid requestId: req-1");
    }
}
