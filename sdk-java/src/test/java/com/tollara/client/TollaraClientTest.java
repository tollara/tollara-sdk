package com.tollara.client;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TollaraClientTest {

    @Test
    void build_succeedsWithDefaultApiUrlWhenOnlyServiceSecret() {
        TollaraClient.builder()
                .serviceSecret("s")
                .httpClient(HttpClient.newHttpClient())
                .build();
    }

    @Test
    void build_succeedsWithExplicitApiUrl() {
        TollaraClient.builder()
                .apiUrl("http://127.0.0.1:9")
                .serviceId("a")
                .serviceSecret("s")
                .httpClient(HttpClient.newHttpClient())
                .build();
    }

    @Test
    void build_throwsWhenServiceSecretMissingAndEnvUnset() {
        assertThatThrownBy(() -> TollaraClient.builder()
                        .apiUrl("http://127.0.0.1:9")
                        .serviceId("a")
                        .httpClient(HttpClient.newHttpClient())
                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(TollaraClient.ENV_SERVICE_SECRET);
    }

    @Test
    void build_succeedsWithoutServiceIdWhenOptional() {
        TollaraClient.builder()
                .apiUrl("http://127.0.0.1:9")
                .serviceSecret("s")
                .httpClient(HttpClient.newHttpClient())
                .build();
    }
}
