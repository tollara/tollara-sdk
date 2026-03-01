package com.bugisiw.marketplace.common.service;

import com.bugisiw.marketplace.common.model.security.TokenIntrospectionRequest;
import com.bugisiw.marketplace.common.model.security.TokenIntrospectionResponse;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for validating JWT tokens using AWS Cognito JWKS with a fallback to the security-service.
 * Implements local validation to minimize latency and avoid caching token validity
 * to enable real-time revocation.
 * 
 * This service supports optional features:
 * - Local JWT validation (if jwtProcessor is provided)
 * - Token revocation cache (if jedisPool is provided)
 * - Remote validation via security-service (always available as fallback)
 */
@Slf4j
public class TokenValidationService {

    private static final String REVOCATION_CHANNEL = "token_revocations";
    
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    private final RestTemplate restTemplate;
    private final JedisPool jedisPool;
    private final String userPoolId;
    private final String region;
    private final String introspectionUrl;
    
    private final AtomicBoolean isRevocationListenerRunning = new AtomicBoolean(false);
    private final Map<String, Boolean> tokenRevocationCache = new ConcurrentHashMap<>();
    
    // Store reference to the pubsub instance for proper shutdown
    private volatile JedisPubSub tokenRevocationPubSub;
    private java.util.concurrent.ExecutorService revocationExecutor;

    /**
     * Constructs a TokenValidationService with the required dependencies.
     * 
     * @param restTemplate RestTemplate for security-service calls (required)
     * @param introspectionUrl Full URL to the security-service introspection endpoint (required)
     * @param jwtProcessor JWT processor for local validation (optional - if null, only remote validation is used)
     * @param jedisPool JedisPool for Redis operations (optional - if null, revocation cache is disabled)
     * @param userPoolId AWS Cognito user pool ID (required if jwtProcessor is provided)
     * @param region AWS Cognito region (required if jwtProcessor is provided)
     */
    public TokenValidationService(
            RestTemplate restTemplate,
            String introspectionUrl,
            ConfigurableJWTProcessor<SecurityContext> jwtProcessor,
            JedisPool jedisPool,
            String userPoolId,
            String region) {
        this.restTemplate = restTemplate;
        this.introspectionUrl = introspectionUrl;
        this.jwtProcessor = jwtProcessor;
        this.jedisPool = jedisPool;
        this.userPoolId = userPoolId;
        this.region = region;
        
        // Start listening for token revocations only if jedisPool is provided
        if (jedisPool != null) {
            startRevocationListener();
        } else {
            log.debug("Token revocation cache disabled - jedisPool not provided");
        }
    }

    /**
     * Validates a JWT token and extracts user information.
     * Uses local validation first (if jwtProcessor is available), with fallback to the security-service.
     *
     * @param token The JWT token to validate
     * @return TokenIntrospectionResponse with user information if valid, or inactive response if invalid
     */
    public TokenIntrospectionResponse validateToken(String token) {
        if (token == null || token.isEmpty()) {
            return createInactiveResponse();
        }
        
        // Strip "Bearer " prefix if present
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        
        // Check if token is in the revocation cache (only if revocation is enabled)
        if (jedisPool != null) {
            boolean isRevoked = tokenRevocationCache.getOrDefault(token, false);
            if (isRevoked) {
                log.debug("Token found in revocation cache, rejecting");
                return createInactiveResponse();
            }
        }
        
        // Attempt local validation first if jwtProcessor is available
        if (jwtProcessor != null) {
            try {
                return validateTokenLocally(token);
            } catch (Exception e) {
                log.debug("Local token validation failed, falling back to security-service: {}", e.getMessage());
            }
        }
        
        // Fall back to security-service for validation
        return validateTokenRemotely(token);
    }

    /**
     * Validates a token locally using AWS Cognito JWKS.
     *
     * @param token The JWT token to validate
     * @return TokenIntrospectionResponse with user information
     * @throws Exception If validation fails
     */
    private TokenIntrospectionResponse validateTokenLocally(String token) throws Exception {
        if (jwtProcessor == null) {
            throw new IllegalStateException("Local validation not available - jwtProcessor is null");
        }
        
        // Process the token - this will verify the signature using the JWKS
        JWTClaimsSet claimsSet = jwtProcessor.process(token, null);
        
        // Validate token has not expired
        java.util.Date expirationTime = claimsSet.getExpirationTime();
        if (expirationTime == null || expirationTime.before(new java.util.Date())) {
            throw new IllegalStateException("Token has expired");
        }
        
        // Extract required claims
        String userId = claimsSet.getSubject();
        if (userId == null) {
            throw new IllegalStateException("Token does not contain a subject (user ID)");
        }
        
        // Validate issuer matches our Cognito user pool (if userPoolId and region are provided)
        if (userPoolId != null && region != null) {
            String expectedIssuer = String.format("https://cognito-idp.%s.amazonaws.com/%s", region, userPoolId);
            String tokenIssuer = claimsSet.getIssuer();
            if (!expectedIssuer.equals(tokenIssuer)) {
                throw new IllegalStateException("Token issuer does not match expected Cognito user pool");
            }
        }
        
        // Extract roles from token
        List<String> roles = extractRoles(claimsSet);
        
        // Extract subscription tier
        String subscriptionTier = extractSubscriptionTier(claimsSet);
        
        // Build and return the response
        return TokenIntrospectionResponse.builder()
                .active(true)
                .user_id(userId)
                .roles(roles)
                .subscription_tier(subscriptionTier)
                .expires_at(expirationTime.toInstant())
                .build();
    }

    /**
     * Validates a token remotely using the security-service.
     *
     * @param token The JWT token to validate
     * @return TokenIntrospectionResponse with user information
     */
    private TokenIntrospectionResponse validateTokenRemotely(String token) {
        try {
            TokenIntrospectionRequest request = new TokenIntrospectionRequest(token);
            ResponseEntity<TokenIntrospectionResponse> response = 
                    restTemplate.postForEntity(introspectionUrl, request, TokenIntrospectionResponse.class);
            
            TokenIntrospectionResponse validationResponse = response.getBody();
            if (validationResponse != null && !validationResponse.isActive() && jedisPool != null) {
                // Add to revocation cache if token is invalid and revocation is enabled
                tokenRevocationCache.put(token, true);
            }
            
            return validationResponse != null ? validationResponse : createInactiveResponse();
        } catch (Exception e) {
            log.error("Error validating token with security-service: {}", e.getMessage());
            return createInactiveResponse();
        }
    }

    /**
     * Extracts roles from JWT claims.
     *
     * @param claimsSet The JWT claims set
     * @return List of roles
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRoles(JWTClaimsSet claimsSet) {
        List<String> roles = new ArrayList<>();
        
        try {
            // Cognito usually stores groups/roles in 'cognito:groups' or custom attribute
            Object cognitoGroups = claimsSet.getClaim("cognito:groups");
            if (cognitoGroups instanceof List) {
                roles.addAll((List<String>) cognitoGroups);
            }
            
            // Also check for custom role attribute
            Object customRoles = claimsSet.getClaim("roles");
            if (customRoles instanceof List) {
                roles.addAll((List<String>) customRoles);
            }
        } catch (Exception e) {
            log.warn("Error extracting roles from token: {}", e.getMessage());
        }
        
        return roles;
    }

    /**
     * Extracts subscription tier from JWT claims.
     *
     * @param claimsSet The JWT claims set
     * @return Subscription tier
     */
    private String extractSubscriptionTier(JWTClaimsSet claimsSet) {
        try {
            // Try to get from custom claim
            return claimsSet.getStringClaim("subscription_tier");
        } catch (ParseException e) {
            log.debug("No subscription_tier found in token");
            return "free"; // Default tier
        }
    }

    /**
     * Creates an inactive token response.
     *
     * @return TokenIntrospectionResponse with active=false
     */
    private TokenIntrospectionResponse createInactiveResponse() {
        return TokenIntrospectionResponse.builder()
                .active(false)
                .build();
    }

    /**
     * Starts the Redis subscription listener for token revocations.
     * Only starts if jedisPool is provided.
     */
    private void startRevocationListener() {
        if (jedisPool == null) {
            return;
        }
        
        if (isRevocationListenerRunning.compareAndSet(false, true)) {
            // Create JedisPubSub instance BEFORE submitting to executor (like SubscriptionCacheUpdater does)
            tokenRevocationPubSub = new JedisPubSub() {
                @Override
                public void onMessage(String channel, String message) {
                    if ("token_revocations".equals(channel)) {
                        tokenRevocationCache.put(message, true);
                        log.debug("Token revocation received and added to cache (cache size: {})", tokenRevocationCache.size());
                    } else {
                        log.warn("Received message on unexpected channel: {} (expected 'token_revocations')", channel);
                    }
                }
                
                @Override
                public void onSubscribe(String channel, int subscribedChannels) {
                    log.info("Subscribed to channel: {} (total: {})", channel, subscribedChannels);
                }
                
                @Override
                public void onUnsubscribe(String channel, int subscribedChannels) {
                    log.info("Unsubscribed from channel: {} (remaining: {})", channel, subscribedChannels);
                }
            };
            
            // Use virtual thread executor like SubscriptionCacheUpdater does
            revocationExecutor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
            revocationExecutor.submit(() -> {
                Thread.currentThread().setName("token-revocation-listener");
                log.info("Starting token revocation listener");
                
                // Reconnection loop similar to SubscriptionCacheUpdater
                while (isRevocationListenerRunning.get()) {
                    Jedis jedis = null;
                    try {
                        jedis = jedisPool.getResource();
                        jedis.subscribe(tokenRevocationPubSub, REVOCATION_CHANNEL);
                        // If we reach here, subscribe() returned (connection lost or unsubscribed)
                        log.info("Redis subscription ended, will attempt to reconnect");
                    } catch (Exception e) {
                        log.error("Error in token revocation listener: {}", e.getMessage(), e);
                        
                        // If we're still supposed to be running, wait and reconnect
                        if (isRevocationListenerRunning.get()) {
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    } finally {
                        // Close the Jedis connection if it exists
                        if (jedis != null) {
                            try {
                                jedis.close();
                            } catch (Exception e) {
                                log.debug("Error closing Jedis connection (non-critical): {}", e.getMessage());
                            }
                        }
                    }
                }
                
                log.info("Token revocation listener stopped");
            });
        }
    }
    
    /**
     * Clean up resources when the application is shutting down.
     */
    @PreDestroy
    public void shutdown() {
        if (jedisPool == null) {
            return;
        }
        
        log.info("Shutting down token revocation listener");
        isRevocationListenerRunning.set(false);
        
        if (tokenRevocationPubSub != null) {
            try {
                tokenRevocationPubSub.unsubscribe();
            } catch (Exception e) {
                // During test shutdown, Redis may already be stopped, so connection errors are expected
                // Only log as debug, not warning, to avoid cluttering test output
                if (e instanceof redis.clients.jedis.exceptions.JedisConnectionException ||
                    e.getCause() instanceof java.net.ConnectException ||
                    e.getCause() instanceof java.net.SocketException) {
                    log.debug("Redis connection error during shutdown (expected in tests): {}", e.getMessage());
                } else {
                    log.warn("Error unsubscribing from Redis: {}", e.getMessage());
                }
            }
        }
        
        if (revocationExecutor != null) {
            revocationExecutor.shutdown();
            try {
                if (!revocationExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    revocationExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                revocationExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Extracts roles from JWT token.
     * Provides a convenient way for other services to get user roles from a token.
     *
     * @param token The JWT token
     * @return List of roles or empty list if extraction fails
     */
    public List<String> getRoles(String token) {
        if (token == null || token.isEmpty()) {
            return Collections.emptyList();
        }
        
        if (jwtProcessor == null) {
            // If local validation not available, try to extract from validation response
            TokenIntrospectionResponse response = validateTokenRemotely(token);
            return response != null && response.isActive() ? response.getRoles() : Collections.emptyList();
        }
        
        try {
            // Process the token to get claims
            JWTClaimsSet claimsSet = jwtProcessor.process(token, null);
            return extractRoles(claimsSet);
        } catch (Exception e) {
            log.warn("Failed to extract roles from token: {}", e.getMessage());
            // Try to extract from validation response as fallback
            TokenIntrospectionResponse response = validateTokenRemotely(token);
            return response != null && response.isActive() ? response.getRoles() : Collections.emptyList();
        }
    }
}

