package com.bugisiw.marketplace.common.service;

import com.bugisiw.marketplace.common.model.billing.MeasurementType;
import com.bugisiw.marketplace.common.model.billing.Subscription;
import com.bugisiw.marketplace.common.model.billing.TimeUnit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Interface for calculating total units used and total overage cost.
 * Different implementations can use different strategies (caching, direct DB queries, etc.)
 */
public interface TotalUnitsCalculator {
    
    /**
     * Calculates the total units used in the current billing cycle for a given subscription.
     *
     * @param userId The ID of the user
     * @param userProductId The ID of the user product (subscription)
     * @param baseTimePeriod The time period unit for the subscription's billing cycle
     * @param subscriptionStartDate The start date of the subscription
     * @return The total units used in the current billing cycle
     */
    BigDecimal calculateTotalUnitsUsed(String userId, String userProductId, TimeUnit baseTimePeriod, LocalDateTime subscriptionStartDate);
    
    /**
     * Calculates the total overage cost in the current billing cycle for a given subscription.
     *
     * @param userId The ID of the user
     * @param userProductId The ID of the user product (subscription)
     * @param subscriptionStartDate The start date of the subscription
     * @return The total overage cost in the current billing cycle
     */
    BigDecimal calculateTotalOverageCost(String userId, String userProductId, LocalDateTime subscriptionStartDate);
    
    /**
     * Convenience method to calculate both total units used and total overage cost at once.
     *
     * @param subscription The subscription details
     * @return A UsageData object containing both total units used and total overage cost
     */
    default UsageData calculateUsageData(Subscription subscription) {
        String userId = subscription.getUserId().toString();
        LocalDateTime startDate = subscription.getStartDate()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime();
        
        // Determine time period based on measurementType
        TimeUnit baseTimePeriod;
        MeasurementType measurementType = subscription.getMeasurementType();
        if (measurementType == null) {
            measurementType = MeasurementTypeResolver.fromUnitLabel(subscription.getUnitLabel());
        }
        if (subscription.getBillingModelType() == com.bugisiw.marketplace.common.model.billing.BillingModelType.SUBSCRIPTION ||
                subscription.getBillingModelType() == com.bugisiw.marketplace.common.model.billing.BillingModelType.USAGE_POSTPAID) {
            switch (measurementType) {
                case PER_TIME_UNIT -> baseTimePeriod = subscription.getPerTimeUnitBaseUnitsPeriod();
                case PER_TOKEN -> baseTimePeriod = subscription.getPerTokenTimeUnit();
                case PER_BYTE -> baseTimePeriod = subscription.getPerByteTimeUnit();
                case PER_REQUEST -> baseTimePeriod = subscription.getPerRequestTimeUnit();
                default -> baseTimePeriod = subscription.getPerRequestTimeUnit();
            }
        } else {
            baseTimePeriod = subscription.getPerRequestTimeUnit();
        }
                
        String userProductId = subscription.getId().toString();
        BigDecimal totalUnitsUsed = calculateTotalUnitsUsed(userId, userProductId, baseTimePeriod, startDate);
        BigDecimal totalOverageCost = calculateTotalOverageCost(userId, userProductId, startDate);
        
        return new UsageData(totalUnitsUsed, totalOverageCost);
    }
    
    /**
     * Data class to hold usage information.
     */
    class UsageData {
        private final BigDecimal totalUnitsUsed;
        private final BigDecimal totalOverageCost;
        
        public UsageData(BigDecimal totalUnitsUsed, BigDecimal totalOverageCost) {
            this.totalUnitsUsed = totalUnitsUsed;
            this.totalOverageCost = totalOverageCost;
        }
        
        public BigDecimal getTotalUnitsUsed() {
            return totalUnitsUsed;
        }
        
        public BigDecimal getTotalOverageCost() {
            return totalOverageCost;
        }
    }
} 