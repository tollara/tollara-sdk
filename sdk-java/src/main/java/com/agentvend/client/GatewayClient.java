package com.agentvend.client;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Caller-side gateway polling for async jobs (see docs/sdk-api-spec.md §1.3–1.4).
 */
@RequiredArgsConstructor
public class GatewayClient {

    private final RestTemplate restTemplate;

    /**
     * GET {@code {gatewayBaseUrl}{gatewayPathPrefix}/requests/{requestId}/status} with Bearer agent key.
     *
     * @param gatewayBaseUrl e.g. https://gateway.example.com
     * @param gatewayPathPrefix e.g. /api (default) or /gateway/api/v1 (ECS)
     */
    public ResponseEntity<String> getRequestStatus(
            String gatewayBaseUrl, String gatewayPathPrefix, String requestId, String agentKey) {
        return exchange(gatewayBaseUrl, gatewayPathPrefix, "/requests/" + requestId + "/status", agentKey);
    }

    /**
     * GET {@code {gatewayBaseUrl}{gatewayPathPrefix}/requests/{requestId}/result} with Bearer agent key.
     */
    public ResponseEntity<String> getRequestResult(
            String gatewayBaseUrl, String gatewayPathPrefix, String requestId, String agentKey) {
        return exchange(gatewayBaseUrl, gatewayPathPrefix, "/requests/" + requestId + "/result", agentKey);
    }

    private ResponseEntity<String> exchange(
            String gatewayBaseUrl, String gatewayPathPrefix, String pathSuffix, String agentKey) {
        String base = gatewayBaseUrl != null ? gatewayBaseUrl.replaceAll("/$", "") : "";
        String prefix = normalizePrefix(gatewayPathPrefix);
        String url = base + prefix + pathSuffix;
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + (agentKey != null ? agentKey : ""));
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private static String normalizePrefix(String gatewayPathPrefix) {
        if (gatewayPathPrefix == null || gatewayPathPrefix.isEmpty()) {
            return "";
        }
        String p = gatewayPathPrefix.startsWith("/") ? gatewayPathPrefix : "/" + gatewayPathPrefix;
        return p.replaceAll("/$", "");
    }
}
