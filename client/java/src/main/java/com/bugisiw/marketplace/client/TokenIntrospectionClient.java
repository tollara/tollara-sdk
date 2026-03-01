package com.bugisiw.marketplace.client;

import com.bugisiw.marketplace.common.model.security.TokenIntrospectionRequest;
import com.bugisiw.marketplace.common.model.security.TokenIntrospectionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Client for calling the security service token introspection endpoint.
 */
@Slf4j
public class TokenIntrospectionClient {

    private final String securityServiceUrl;
    private final RestTemplate restTemplate;

    /**
     * Creates a new TokenIntrospectionClient.
     *
     * @param securityServiceUrl The base URL of the security service
     * @param restTemplate RestTemplate for HTTP calls
     */
    public TokenIntrospectionClient(String securityServiceUrl, RestTemplate restTemplate) {
        this.securityServiceUrl = securityServiceUrl;
        this.restTemplate = restTemplate;
    }

    /**
     * Introspects a JWT token by calling the security service.
     *
     * @param token The JWT token to introspect
     * @return TokenIntrospectionResponse with token information, or null if introspection fails
     */
    public TokenIntrospectionResponse introspectToken(String token) {
        if (token == null || token.isEmpty()) {
            log.warn("Token is null or empty");
            return null;
        }

        try {
            String url = securityServiceUrl + "/auth/introspect";
            
            TokenIntrospectionRequest request = new TokenIntrospectionRequest(token);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<TokenIntrospectionRequest> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<TokenIntrospectionResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    TokenIntrospectionResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                TokenIntrospectionResponse body = response.getBody();
                if (body.isActive()) {
                    log.debug("Token introspection successful for user: {}", body.getUser_id());
                } else {
                    log.debug("Token introspection returned inactive token");
                }
                return body;
            } else {
                log.warn("Token introspection returned non-2xx status: {}", response.getStatusCode());
                return null;
            }
        } catch (RestClientException e) {
            log.error("Error calling token introspection endpoint: {}", e.getMessage(), e);
            return null;
        }
    }
}

