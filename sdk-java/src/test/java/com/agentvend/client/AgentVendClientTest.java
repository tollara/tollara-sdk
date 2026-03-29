package com.agentvend.client;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentVendClientTest {

    @Test
    void build_throwsWhenApiUrlMissingAndEnvUnset() {
        assertThatThrownBy(() -> AgentVendClient.builder()
                        .agentId("a")
                        .agentSecret("s")
                        .httpClient(HttpClient.newHttpClient())
                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(AgentVendClient.ENV_API_URL);
    }

    @Test
    void build_succeedsWithExplicitApiUrl() {
        AgentVendClient.builder()
                .apiUrl("http://127.0.0.1:9")
                .agentId("a")
                .agentSecret("s")
                .httpClient(HttpClient.newHttpClient())
                .build();
    }

    @Test
    void build_throwsWhenAgentSecretMissingAndEnvUnset() {
        assertThatThrownBy(() -> AgentVendClient.builder()
                        .apiUrl("http://127.0.0.1:9")
                        .agentId("a")
                        .httpClient(HttpClient.newHttpClient())
                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(AgentVendClient.ENV_AGENT_SECRET);
    }

    @Test
    void build_succeedsWithoutAgentIdWhenOptional() {
        AgentVendClient.builder()
                .apiUrl("http://127.0.0.1:9")
                .agentSecret("s")
                .httpClient(HttpClient.newHttpClient())
                .build();
    }
}
