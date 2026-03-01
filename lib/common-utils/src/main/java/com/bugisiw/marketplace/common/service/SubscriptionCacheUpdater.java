package com.bugisiw.marketplace.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Listens for subscription updates via Redis pub/sub and invalidates cache entries.
 * Uses virtual threads for non-blocking operation.
 */
@Slf4j
@Component
public class SubscriptionCacheUpdater {
    private static final String SUBSCRIPTION_UPDATES_CHANNEL = "subscription_updates";
    
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private final SubscriptionClientService subscriptionClientService;
    private final ExecutorService virtualExecutor;
    private final AtomicBoolean running;
    private JedisPubSub subscriber;
    
    public SubscriptionCacheUpdater(JedisPool jedisPool, ObjectMapper objectMapper, SubscriptionClientService subscriptionClientService) {
        this.jedisPool = jedisPool;
        this.objectMapper = objectMapper;
        this.subscriptionClientService = subscriptionClientService;
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.running = new AtomicBoolean(true);
        
        log.info("SubscriptionCacheUpdater initialized");
    }
    
    /**
     * Subscribe to the Redis pub/sub channel for subscription updates.
     * Uses a virtual thread to avoid blocking the main application thread.
     */
    @PostConstruct
    public void subscribe() {
        subscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                try {
                    log.debug("Received subscription update: {}", message);
                    
                    // Try to parse as JSON first (new format)
                    try {
                        JsonNode update = objectMapper.readTree(message);
                        if (update.has("userId") && update.has("agentId")) {
                            String userId = update.get("userId").asText();
                            String agentId = update.get("agentId").asText();
                            subscriptionClientService.invalidateSubscriptionCache(userId, agentId);
                            return;
                        }
                    } catch (Exception jsonEx) {
                        // Not JSON, try colon-separated format (legacy format from SubscriptionChangePublisher)
                        if (message != null && message.contains(":")) {
                            String[] parts = message.split(":", 2);
                            if (parts.length == 2) {
                                String userId = parts[0].trim();
                                String agentId = parts[1].trim();
                                log.debug("Parsed colon-separated format: userId={}, agentId={}", userId, agentId);
                                subscriptionClientService.invalidateSubscriptionCache(userId, agentId);
                                return;
                            }
                        }
                        // If neither JSON nor colon-separated, log error
                        log.error("Received subscription update in unknown format: {}", message);
                    }
                } catch (Exception e) {
                    log.error("Error processing subscription update: {}", e.getMessage(), e);
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
            
            @Override
            public void onPSubscribe(String pattern, int subscribedChannels) {
                log.info("Subscribed to pattern: {} (total: {})", pattern, subscribedChannels);
            }
            
            @Override
            public void onPUnsubscribe(String pattern, int subscribedChannels) {
                log.info("Unsubscribed from pattern: {} (remaining: {})", pattern, subscribedChannels);
            }
            
            @Override
            public void onPMessage(String pattern, String channel, String message) {
                log.debug("Received pattern message: {} -> {}", channel, message);
            }
        };
        
        virtualExecutor.submit(() -> {
            log.info("Starting subscription listener on channel: {}", SUBSCRIPTION_UPDATES_CHANNEL);
            while (running.get()) {
                Jedis jedis = null;
                try {
                    jedis = jedisPool.getResource();
                    jedis.subscribe(subscriber, SUBSCRIPTION_UPDATES_CHANNEL);
                } catch (Exception e) {
                    log.error("Error in subscription listener: {}", e.getMessage(), e);
                    
                    // If we were unsubscribed or the connection failed, sleep and try again
                    if (running.get()) {
                        try {
                            Thread.sleep(5000); // Wait 5 seconds before reconnecting
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                } finally {
                    // Manually close jedis connection, handling potential pool validation errors
                    if (jedis != null) {
                        try {
                            jedis.close();
                        } catch (Exception e) {
                            // Log but don't fail on cleanup errors, especially during shutdown
                            // This can happen with embedded Redis or connection pool validation issues
                            log.debug("Error closing Jedis connection (non-critical): {}", e.getMessage());
                        }
                    }
                }
            }
            log.info("Subscription listener stopped");
        });
        
        log.info("Subscription listener started");
    }
    
    /**
     * Manually publish a subscription update.
     * This can be used by internal services to trigger cache invalidation.
     * 
     * @param userId The ID of the user whose subscription was updated
     * @param agentId The ID of the agent associated with the subscription
     * @return True if the message was published successfully
     */
    public boolean publishSubscriptionUpdate(String userId, String agentId) {
        log.info("Publishing subscription update for user: {} and agent: {}", userId, agentId);
        
        try (Jedis jedis = jedisPool.getResource()) {
            String message = String.format("{\"userId\":\"%s\",\"agentId\":\"%s\"}", userId, agentId);
            long recipients = jedis.publish(SUBSCRIPTION_UPDATES_CHANNEL, message);
            log.info("Subscription update published to {} listeners", recipients);
            return recipients > 0;
        } catch (Exception e) {
            log.error("Error publishing subscription update: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Clean up resources when the application is shutting down.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down subscription listener");
        running.set(false);
        
        if (subscriber != null) {
            try {
                subscriber.unsubscribe();
            } catch (Exception e) {
                // During test shutdown, Redis may already be stopped, so connection errors are expected
                // Only log as warning, not error, to avoid cluttering test output
                if (e instanceof redis.clients.jedis.exceptions.JedisConnectionException ||
                    e.getCause() instanceof java.net.ConnectException ||
                    e.getCause() instanceof java.net.SocketException) {
                    log.debug("Redis connection error during shutdown (expected in tests): {}", e.getMessage());
                } else {
                    log.warn("Error unsubscribing from Redis: {}", e.getMessage());
                }
            }
        }
        
        virtualExecutor.shutdown();
    }
} 