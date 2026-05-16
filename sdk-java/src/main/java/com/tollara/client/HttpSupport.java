package com.tollara.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Internal helpers for {@link java.net.http.HttpClient}.
 */
final class HttpSupport {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private HttpSupport() {
    }

    static HttpResponse<String> postJson(
            HttpClient client,
            String url,
            String jsonBody,
            Map<String, String> requestHeaders) throws IOException, InterruptedException {
        Objects.requireNonNull(client, "httpClient");
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        if (requestHeaders != null) {
            requestHeaders.forEach(b::header);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    static HttpResponse<String> get(HttpClient client, String url, Map<String, String> requestHeaders)
            throws IOException, InterruptedException {
        Objects.requireNonNull(client, "httpClient");
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(DEFAULT_TIMEOUT).GET();
        if (requestHeaders != null) {
            requestHeaders.forEach(b::header);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * Arbitrary HTTP method with optional UTF-8 body (e.g. gateway invoke). Caller supplies all headers including auth.
     */
    static HttpResponse<String> send(
            HttpClient client,
            String method,
            String url,
            String body,
            Map<String, String> requestHeaders)
            throws IOException, InterruptedException {
        Objects.requireNonNull(client, "httpClient");
        String m = method == null ? "GET" : method.trim().toUpperCase();
        HttpRequest.BodyPublisher publisher =
                body == null || body.isEmpty()
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(DEFAULT_TIMEOUT);
        if (requestHeaders != null) {
            requestHeaders.forEach(b::header);
        }
        HttpRequest req = b.method(m, publisher).build();
        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
