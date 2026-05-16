package com.agentvend.client;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentVendClientTest {

    @Test
    void build_succeedsWithDefaultApiUrlWhenOnlyServiceSecret() {
        AgentVendClient.builder()
                .serviceSecret("s")
                .httpClient(HttpClient.newHttpClient())
                .build();
    }

    @Test
    void build_succeedsWithExplicitApiUrl() {
        AgentVendClient.builder()
                .apiUrl("http://127.0.0.1:9")
                .serviceId("a")
                .serviceSecret("s")
                .httpClient(HttpClient.newHttpClient())
                .build();
    }

    @Test
    void build_throwsWhenServiceSecretMissingAndEnvUnset() {
        assertThatThrownBy(() -> AgentVendClient.builder()
                        .apiUrl("http://127.0.0.1:9")
                        .serviceId("a")
                        .httpClient(HttpClient.newHttpClient())
                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(AgentVendClient.ENV_SERVICE_SECRET);
    }

    @Test
    void build_succeedsWithoutServiceIdWhenOptional() {
        AgentVendClient.builder()
                .apiUrl("http://127.0.0.1:9")
                .serviceSecret("s")
                .httpClient(HttpClient.newHttpClient())
                .build();
    }
}
