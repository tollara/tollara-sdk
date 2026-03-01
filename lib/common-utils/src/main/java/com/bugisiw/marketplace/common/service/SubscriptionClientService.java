package com.bugisiw.marketplace.common.service;

import com.bugisiw.marketplace.common.model.billing.Subscription;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Service for fetching subscription data from the billing service.
 * This is a shared component used by both gateway-service and usage-service.
 */
@Slf4j
@Service
public class SubscriptionClientService {

    private static final String SUBSCRIPTION_KEY_PREFIX = "subscription:";
    private static final int SUBSCRIPTION_TTL = 300; // 5 minutes

    private final RestTemplate restTemplate;
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private final String coreServiceUrl;

    public SubscriptionClientService(
            RestTemplate restTemplate,
            JedisPool jedisPool,
            ObjectMapper objectMapper,
            @Value("${agent-hub.core-service.url}") String coreServiceUrl) {
        this.restTemplate = restTemplate;
        this.jedisPool = jedisPool;
        this.objectMapper = objectMapper;
        this.coreServiceUrl = coreServiceUrl;
    }
    
    /**
     * Fetches a subscription for a specific user and agent combination.
     * First checks Redis cache, then falls back to billing service if not found.
     *
     * @param userId The user ID
     * @param agentId The agent ID
     * @return The subscription if found, null otherwise
     */
    public Subscription fetchSubscription(String userId, String agentId) {
        log.info("Fetching subscription for user {} and agent {}", userId, agentId);
        
        // Check Redis cache first
        Subscription subscription = getCachedSubscription(userId, agentId);
        
        if (subscription != null) {
            log.debug("Found cached subscription for user {} and agent {}", userId, agentId);
            return subscription;
        }
        
        // Not in cache, fetch from core-service (billing endpoint). Path relative to core context root.
        try {
            String baseUrl = coreServiceUrl != null ? coreServiceUrl.replaceAll("/$", "") : "";
            String url = baseUrl + "/billing/subscriptions/user/" + userId + "/agent/" + agentId;

            try {
                subscription = restTemplate.getForObject(url, Subscription.class);
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                throw e;
            } catch (org.springframework.web.client.HttpServerErrorException e) {
                throw e;
            } catch (org.springframework.web.client.UnknownContentTypeException e) {
                throw e;
            }
            
            if (subscription != null) {
                log.debug("Successfully fetched subscription from billing service - id: {}, billingModelType: {}", 
                    subscription.getId(), subscription.getBillingModelType());
                // Cache the subscription
                cacheSubscription(userId, agentId, subscription);
            } else {
                log.debug("No subscription found for user {} and agent {} (endpoint returned null)", userId, agentId);
            }
            
            return subscription;
        } catch (RestClientException e) {
            log.error("Error fetching subscription from billing service: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Caches a subscription with a composite key of userId and agentId.
     */
    private void cacheSubscription(String userId, String agentId, Subscription subscription) {
        try (Jedis jedis = jedisPool.getResource()) {
            String subscriptionJson = objectMapper.writeValueAsString(subscription);
            String cacheKey = getCacheKey(userId, agentId);
            jedis.setex(cacheKey, SUBSCRIPTION_TTL, subscriptionJson);
            log.debug("Cached subscription for user {} and agent {}", userId, agentId);
        } catch (Exception e) {
            log.error("Error caching subscription: {}", e.getMessage(), e);
        }
    }

    /**
     * Retrieves a cached subscription using the composite key.
     */
    private Subscription getCachedSubscription(String userId, String agentId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String cacheKey = getCacheKey(userId, agentId);
            String subscriptionJson = jedis.get(cacheKey);
            if (subscriptionJson != null) {
                return objectMapper.readValue(subscriptionJson, Subscription.class);
            }
        } catch (Exception e) {
            log.error("Error getting cached subscription: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Gets the cached subscription without fetching from the service.
     * Returns null if not in cache.
     *
     * @param userId The user ID
     * @param agentId The agent ID
     * @return The cached subscription, or null if not in cache
     */
    public Subscription getCachedSubscriptionOnly(String userId, String agentId) {
        return getCachedSubscription(userId, agentId);
    }

    /**
     * Fetches a subscription from the billing service, bypassing the cache.
     * This is useful for PREPAID subscriptions where credit balance changes frequently.
     *
     * @param userId The user ID
     * @param agentId The agent ID
     * @return The subscription if found, null otherwise
     */
    public Subscription fetchSubscriptionFresh(String userId, String agentId) {
        log.info("Fetching fresh subscription (bypassing cache) for user {} and agent {}", userId, agentId);
        
        // Invalidate cache first to ensure fresh fetch
        invalidateSubscriptionCache(userId, agentId);
        
        // Fetch from core-service (billing endpoint). Path relative to core context root.
        try {
            String baseUrl = coreServiceUrl != null ? coreServiceUrl.replaceAll("/$", "") : "";
            String url = baseUrl + "/billing/subscriptions/user/" + userId + "/agent/" + agentId;

            Subscription subscription = restTemplate.getForObject(url, Subscription.class);
            
            if (subscription != null) {
                log.debug("Successfully fetched fresh subscription from billing service - id: {}, billingModelType: {}", 
                    subscription.getId(), subscription.getBillingModelType());
                // Cache the subscription for future use
                cacheSubscription(userId, agentId, subscription);
            } else {
                log.debug("No subscription found for user {} and agent {} (endpoint returned null)", userId, agentId);
            }
            
            return subscription;
        } catch (RestClientException e) {
            log.error("Error fetching fresh subscription from billing service: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Invalidates the cache for a specific user-agent subscription.
     */
    public void invalidateSubscriptionCache(String userId, String agentId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String cacheKey = getCacheKey(userId, agentId);
            jedis.del(cacheKey);
            log.debug("Invalidated subscription cache for user {} and agent {}", userId, agentId);
        } catch (Exception e) {
            log.error("Error invalidating subscription cache: {}", e.getMessage(), e);
        }
    }

    /**
     * Generates a cache key for a user-agent subscription.
     */
    private String getCacheKey(String userId, String agentId) {
        return SUBSCRIPTION_KEY_PREFIX + userId + ":" + agentId;
    }
} 