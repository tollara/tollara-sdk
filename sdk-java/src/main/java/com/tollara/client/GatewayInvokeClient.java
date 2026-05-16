package com.tollara.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * Gateway caller invoke (sync and async). See {@code docs-sdk/MAIN-SDK-API-SPEC.md} §1.1–1.2.
 */
public final class GatewayInvokeClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GatewayInvokeClient() {
    }

    /**
     * Invoke a service endpoint on the gateway.
     *
     * @param method       GET, POST, PUT, or DELETE (case-insensitive)
     * @param requestBody  optional body (e.g. JSON for POST); ignored for GET when empty
     * @param async        when true, calls {@code …/invoke/async}
     */
    public static GatewayInvokeResult invoke(
            HttpClient httpClient,
            String gatewayBaseUrl,
            String gatewayPathPrefix,
            String method,
            String serviceId,
            String endpointId,
            String serviceKey,
            String requestBody,
            boolean async)
            throws IOException, InterruptedException {
        String base = gatewayBaseUrl != null ? gatewayBaseUrl.replaceAll("/$", "") : "";
        String prefix = normalizePrefix(gatewayPathPrefix);
        String suffix = "/service/" + serviceId + "/endpoint/" + endpointId + "/invoke" + (async ? "/async" : "");
        String url = base + prefix + suffix;

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + (serviceKey != null ? serviceKey : ""));
        String body = requestBody != null ? requestBody : "";
        String m = method != null ? method.trim().toUpperCase() : "GET";
        if (!body.isEmpty() && ("POST".equals(m) || "PUT".equals(m))) {
            headers.putIfAbsent("Content-Type", "application/json; charset=UTF-8");
        }

        HttpResponse<String> resp = HttpSupport.send(httpClient, m, url, body.isEmpty() ? null : body, headers);
        int code = resp.statusCode();
        String text = resp.body() != null ? resp.body() : "";
        GatewayInvokeResult.AsyncInvokeEnvelope env = null;
        if (code == 202 && text != null && !text.isBlank()) {
            try {
                JsonNode n = MAPPER.readTree(text);
                if (n.hasNonNull("requestId")) {
                    env = GatewayInvokeResult.AsyncInvokeEnvelope.builder()
                            .requestId(textOrNull(n, "requestId"))
                            .callbackUrl(textOrNull(n, "callbackUrl"))
                            .progressUrl(textOrNull(n, "progressUrl"))
                            .build();
                }
            } catch (Exception ignored) {
                // leave env null
            }
        }
        return GatewayInvokeResult.builder()
                .statusCode(code)
                .body(text)
                .asyncEnvelope(env)
                .build();
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }

    private static String normalizePrefix(String gatewayPathPrefix) {
        if (gatewayPathPrefix == null || gatewayPathPrefix.isEmpty()) {
            return "";
        }
        String p = gatewayPathPrefix.startsWith("/") ? gatewayPathPrefix : "/" + gatewayPathPrefix;
        return p.replaceAll("/$", "");
    }
}
