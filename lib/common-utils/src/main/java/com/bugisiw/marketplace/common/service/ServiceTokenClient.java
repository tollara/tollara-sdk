package com.bugisiw.marketplace.common.service;

import com.bugisiw.marketplace.common.model.security.ServiceTokenResponse;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Fetches and caches service tokens from security-service (client credentials).
 * Thread-safe; refreshes token shortly before expiry.
 */
@Slf4j
public class ServiceTokenClient {

    private static final int REFRESH_BEFORE_EXPIRY_SECONDS = 120;

    private final RestTemplate restTemplate;
    private final String tokenEndpointUrl;
    private final String clientId;
    private final String clientSecret;
    private final String audience;

    private volatile String cachedToken;
    private volatile Instant cachedExpiry;
    private final ReentrantLock lock = new ReentrantLock();

    public ServiceTokenClient(
            RestTemplate restTemplate,
            String tokenEndpointUrl,
            String clientId,
            String clientSecret) {
        this(restTemplate, tokenEndpointUrl, clientId, clientSecret, "internal");
    }

    public ServiceTokenClient(
            RestTemplate restTemplate,
            String tokenEndpointUrl,
            String clientId,
            String clientSecret,
            String audience) {
        this.restTemplate = restTemplate;
        this.tokenEndpointUrl = tokenEndpointUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.audience = audience != null && !audience.isBlank() ? audience : "internal";
    }

    /**
     * Returns a valid access token, refreshing if necessary.
     *
     * @return Bearer token value (without "Bearer " prefix)
     * @throws RuntimeException if token cannot be obtained
     */
    public String getAccessToken() {
        Instant now = Instant.now();
        if (cachedToken != null && cachedExpiry != null && now.plusSeconds(REFRESH_BEFORE_EXPIRY_SECONDS).isBefore(cachedExpiry)) {
            log.info(">>>>>STOKEN [client] using cached token client_id={} client_secret={} audience={} expiresAt={} tokenPreview={}",
                    clientId, clientSecret, audience, cachedExpiry, tokenPreview(cachedToken));
            return cachedToken;
        }
        lock.lock();
        try {
            if (cachedToken != null && cachedExpiry != null && now.plusSeconds(REFRESH_BEFORE_EXPIRY_SECONDS).isBefore(cachedExpiry)) {
                log.info(">>>>>STOKEN [client] using cached token (after lock) client_id={} client_secret={} expiresAt={} tokenPreview={}",
                        clientId, clientSecret, cachedExpiry, tokenPreview(cachedToken));
                return cachedToken;
            }
            log.info(">>>>>STOKEN [client] fetching new token client_id={} client_secret={} audience={} endpoint={}", clientId, clientSecret, audience, tokenEndpointUrl);
            fetchAndCache();
            return cachedToken;
        } finally {
            lock.unlock();
        }
    }

    private void fetchAndCache() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "audience", audience
        );
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<ServiceTokenResponse> response = restTemplate.postForEntity(
                tokenEndpointUrl,
                request,
                ServiceTokenResponse.class);

        if (response.getStatusCode().isError() || response.getBody() == null || response.getBody().getAccessToken() == null) {
            throw new RuntimeException("Service token request failed: " + response.getStatusCode());
        }

        ServiceTokenResponse tokenResponse = response.getBody();
        cachedToken = tokenResponse.getAccessToken();
        try {
            JWT jwt = JWTParser.parse(cachedToken);
            if (jwt.getJWTClaimsSet().getExpirationTime() != null) {
                cachedExpiry = jwt.getJWTClaimsSet().getExpirationTime().toInstant();
            } else {
                cachedExpiry = Instant.now().plusSeconds(tokenResponse.getExpiresIn());
            }
        } catch (Exception e) {
            log.warn("Could not parse token expiry, using expires_in: {}", e.getMessage());
            cachedExpiry = Instant.now().plusSeconds(tokenResponse.getExpiresIn());
        }
        log.info(">>>>>STOKEN [client] fetched and cached token client_id={} client_secret={} audience={} expiresAt={} tokenLength={} tokenPreview={}",
                clientId, clientSecret, audience, cachedExpiry, cachedToken != null ? cachedToken.length() : 0, tokenPreview(cachedToken));
        log.debug("Cached new service token for client_id={}, expires at {}", clientId, cachedExpiry);
    }

    private static String tokenPreview(String token) {
        if (token == null || token.isEmpty()) return "null";
        if (token.length() <= 50) return token;
        return token.substring(0, 30) + "..." + token.substring(token.length() - 15);
    }
}
