package com.agentvend.client;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.junit5.WireMockExtension.newInstance;
import static org.assertj.core.api.Assertions.assertThat;

class GatewayClientIntegrationTest {

    private static final String AGENT_KEY = "test-agent-key";

    @RegisterExtension
    static WireMockExtension wireMock = newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private GatewayClient client;
    private String gatewayBase;

    @BeforeEach
    void setUp() {
        int port = wireMock.getPort();
        gatewayBase = "http://localhost:" + port;
        client = new GatewayClient(new RestTemplate());
    }

    @Test
    void getRequestStatus_sendsBearerAndReturnsBody() {
        wireMock.stubFor(
                get(urlPathEqualTo("/api/requests/job-1/status"))
                        .withHeader("Authorization", equalTo("Bearer " + AGENT_KEY))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"state\":\"PENDING\"}")));

        ResponseEntity<String> res =
                client.getRequestStatus(gatewayBase, "/api", "job-1", AGENT_KEY);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("PENDING");
    }

    @Test
    void getRequestResult_usesEcsStylePrefix() {
        wireMock.stubFor(
                get(urlPathEqualTo("/gateway/api/v1/requests/job-2/result"))
                        .withHeader("Authorization", equalTo("Bearer " + AGENT_KEY))
                        .willReturn(aResponse().withStatus(200).withBody("{}")));

        ResponseEntity<String> result =
                client.getRequestResult(gatewayBase, "/gateway/api/v1", "job-2", AGENT_KEY);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
