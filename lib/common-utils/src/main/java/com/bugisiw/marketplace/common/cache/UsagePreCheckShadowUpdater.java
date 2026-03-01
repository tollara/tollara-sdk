package com.bugisiw.marketplace.common.cache;

import com.bugisiw.marketplace.common.model.billing.BillingModelType;
import com.bugisiw.marketplace.common.model.billing.Subscription;
import com.bugisiw.marketplace.common.model.usage.UsageCalculation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Updates the Redis usage pre-check shadow after a successful /usage/record.
 * Same key layout as the gateway's UsagePreCheckService:
 * - PREPAID: {@code gateway:usage-precheck:credits:{userId}:{agentId}}
 * - SUBSCRIPTION/USAGE_POSTPAID: {@code gateway:usage-precheck:usage:{userId}:{agentId}} (hash)
 * Used by gateway (delegation) and by gateway-usage-consumer after successful record.
 */
@RequiredArgsConstructor
@Slf4j
public class UsagePreCheckShadowUpdater {

    private static final String CREDITS_KEY_PREFIX = "gateway:usage-precheck:credits:";
    private static final String USAGE_KEY_PREFIX = "gateway:usage-precheck:usage:";
    private static final String HASH_TOTAL_UNITS = "totalUnitsUsedThisCycle";
    private static final String HASH_TOTAL_OVERAGE_COST = "totalOverageCostThisCycle";

    private final JedisPool jedisPool;
    private final long ttlSeconds;

    /**
     * Updates the Redis shadow after a successful /usage/record.
     * No-op if any required argument is null.
     */
    public void updateShadowAfterRecord(Subscription subscription, UUID userId, String agentId, UsageCalculation finalCalc) {
        if (subscription == null || userId == null || agentId == null || finalCalc == null) {
            return;
        }
        BillingModelType billingModelType = subscription.getBillingModelType();
        if (billingModelType == null) {
            return;
        }
        try (Jedis jedis = jedisPool.getResource()) {
            if (billingModelType == BillingModelType.PREPAID) {
                BigDecimal remainingCredits = finalCalc.getUnitsRemaining() != null && subscription.getPrepaidUnitPrice() != null
                        ? finalCalc.getUnitsRemaining().multiply(subscription.getPrepaidUnitPrice())
                        : BigDecimal.ZERO;
                setCreditsKey(jedis, userId, agentId, remainingCredits);
                log.info("Updated usage pre-check shadow (credits): key=gateway:usage-precheck:credits:{}:{}, remainingCredits={}",
                        userId, agentId, remainingCredits);
            } else if (billingModelType == BillingModelType.SUBSCRIPTION || billingModelType == BillingModelType.USAGE_POSTPAID) {
                BigDecimal totalUnits = finalCalc.getTotalUnitsUsedThisCycle() != null ? finalCalc.getTotalUnitsUsedThisCycle() : BigDecimal.ZERO;
                BigDecimal totalOverageCost = finalCalc.getTotalOverageCost() != null ? finalCalc.getTotalOverageCost() : BigDecimal.ZERO;
                setUsageKey(jedis, userId, agentId, totalUnits, totalOverageCost);
                log.info("Updated usage pre-check shadow: key=gateway:usage-precheck:usage:{}:{}, totalUnits={}, totalOverageCost={}",
                        userId, agentId, totalUnits, totalOverageCost);
            }
        } catch (Exception e) {
            log.warn("Usage pre-check shadow update failed for user {} agent {}: {}", userId, agentId, e.getMessage());
        }
    }

    private void setCreditsKey(Jedis jedis, UUID userId, String agentId, BigDecimal remainingCredits) {
        String key = CREDITS_KEY_PREFIX + userId + ":" + agentId;
        jedis.set(key, remainingCredits.toPlainString());
        expireIfConfigured(jedis, key);
    }

    private void setUsageKey(Jedis jedis, UUID userId, String agentId, BigDecimal totalUnits, BigDecimal totalOverageCost) {
        String key = USAGE_KEY_PREFIX + userId + ":" + agentId;
        jedis.hset(key, HASH_TOTAL_UNITS, totalUnits.toPlainString());
        jedis.hset(key, HASH_TOTAL_OVERAGE_COST, totalOverageCost.toPlainString());
        expireIfConfigured(jedis, key);
    }

    private void expireIfConfigured(Jedis jedis, String key) {
        if (ttlSeconds > 0) {
            jedis.expire(key, (int) ttlSeconds);
        }
    }
}
