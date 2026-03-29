package com.agentvend.client;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Map;

/**
 * Caller-side gateway polling for async jobs (see docs/sdk-api-spec.md §1.3–1.4).
 */
@RequiredArgsConstructor
public class GatewayClient {

    private final HttpClient httpClient;

    /**
     * GET {@code {gatewayBaseUrl}{gatewayPathPrefix}/requests/{requestId}/status} with Bearer agent key.
     *
     * @param gatewayBaseUrl e.g. https://gateway.example.com
     * @param gatewayPathPrefix e.g. /api (default) or /gateway/api/v1 (ECS)
     */
    public GatewayHttpResponse getRequestStatus(
            String gatewayBaseUrl, String gatewayPathPrefix, String requestId, String agentKey) {
        return exchange(gatewayBaseUrl, gatewayPathPrefix, "/requests/" + requestId + "/status", agentKey);
    }

    /**
     * GET {@code {gatewayBaseUrl}{gatewayPathPrefix}/requests/{requestId}/result} with Bearer agent key.
     */
    public GatewayHttpResponse getRequestResult(
            String gatewayBaseUrl, String gatewayPathPrefix, String requestId, String agentKey) {
        return exchange(gatewayBaseUrl, gatewayPathPrefix, "/requests/" + requestId + "/result", agentKey);
    }

    private GatewayHttpResponse exchange(
            String gatewayBaseUrl, String gatewayPathPrefix, String pathSuffix, String agentKey) {
        String base = gatewayBaseUrl != null ? gatewayBaseUrl.replaceAll("/$", "") : "";
        String prefix = normalizePrefix(gatewayPathPrefix);
        String url = base + prefix + pathSuffix;
        Map<String, String> headers =
                Map.of("Authorization", "Bearer " + (agentKey != null ? agentKey : ""));
        try {
            var resp = HttpSupport.get(httpClient, url, headers);
            return new GatewayHttpResponse(resp.statusCode(), resp.body() != null ? resp.body() : "");
        } catch (IOException e) {
            throw new AgentVendHttpException("Gateway request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentVendHttpException("Gateway request interrupted", e);
        }
    }

    private static String normalizePrefix(String gatewayPathPrefix) {
        if (gatewayPathPrefix == null || gatewayPathPrefix.isEmpty()) {
            return "";
        }
        String p = gatewayPathPrefix.startsWith("/") ? gatewayPathPrefix : "/" + gatewayPathPrefix;
        return p.replaceAll("/$", "");
    }
}
