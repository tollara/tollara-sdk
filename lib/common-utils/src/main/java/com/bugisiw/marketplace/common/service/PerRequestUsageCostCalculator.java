package com.bugisiw.marketplace.common.service;

import com.bugisiw.marketplace.common.model.billing.BillingModelType;
import com.bugisiw.marketplace.common.model.billing.MeasurementType;
import com.bugisiw.marketplace.common.model.billing.Subscription;
import com.bugisiw.marketplace.common.model.usage.UsageCalculation;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Implementation of UsageCostCalculator for request-based billing.
 * Calculates costs based on request counts within the billing cycle.
 * Works with SUBSCRIPTION and USAGE_POSTPAID models that use request-based units.
 */
@Slf4j
public class PerRequestUsageCostCalculator implements UsageCostCalculator {

    @Override
    public UsageCalculation calculateCost(Subscription subscription,
                                          BigDecimal totalUnitsUsed,
                                          BigDecimal totalOverageCost,
                                          BigDecimal additionalUsedUnits) {
        if (subscription.getMeasurementType() != null && subscription.getMeasurementType() != MeasurementType.PER_REQUEST) {
            throw new IllegalArgumentException("PerRequestUsageCostCalculator can only handle PER_REQUEST subscriptions");
        }
        if (subscription.getBillingModelType() != BillingModelType.SUBSCRIPTION &&
                subscription.getBillingModelType() != BillingModelType.USAGE_POSTPAID) {
            throw new IllegalArgumentException("PerRequestUsageCostCalculator can only handle SUBSCRIPTION or USAGE_POSTPAID subscriptions");
        }

        Long baseRequestsLong = subscription.getPerRequestBaseRequests();
        Long maxRequestsLong = subscription.getPerRequestMaxRequests();
        long baseRequests = baseRequestsLong != null ? baseRequestsLong : 0L;
        long maxRequests = maxRequestsLong != null ? maxRequestsLong : Long.MAX_VALUE; // Unlimited if null
        BigDecimal overageSpendingCap = subscription.getOverageSpendingCap() != null ? 
                subscription.getOverageSpendingCap() : BigDecimal.ZERO;
        BigDecimal overageRate = subscription.getOverageRate() != null ? 
                subscription.getOverageRate() : BigDecimal.ZERO;
        // For USAGE_POSTPAID, overageRate should be the perUnitPrice (all usage is charged at this rate)
        // If it's still zero, we can't calculate costs - this indicates a configuration issue
        if (overageRate.compareTo(BigDecimal.ZERO) == 0 && 
            subscription.getBillingModelType() == BillingModelType.USAGE_POSTPAID) {
            throw new IllegalArgumentException("USAGE_POSTPAID subscription missing perUnitPrice (overageRate). " +
                    "Subscription ID: " + subscription.getId());
        }
        boolean isOverageAllowed = subscription.isOverageAllowed();

        // Convert long values to BigDecimal for consistent calculations
        BigDecimal baseRequestsDecimal = BigDecimal.valueOf(baseRequests);
        BigDecimal maxRequestsDecimal = BigDecimal.valueOf(maxRequests);

        // Calculate new totals with this request
        BigDecimal newTotalUnits = totalUnitsUsed.add(additionalUsedUnits);
        boolean isOverLimit = newTotalUnits.compareTo(baseRequestsDecimal) > 0;
        boolean exceedsMaxLimit = newTotalUnits.compareTo(maxRequestsDecimal) > 0;
        BigDecimal cumulativeOverageUnits = isOverLimit ? newTotalUnits.subtract(baseRequestsDecimal) : BigDecimal.ZERO;
        BigDecimal unitsRemaining = baseRequestsDecimal.subtract(newTotalUnits).max(BigDecimal.ZERO);

        // Calculate baseUnitsUsed for this transaction
        BigDecimal baseUnitsUsed;
        if (totalUnitsUsed.compareTo(baseRequestsDecimal) >= 0) {
            baseUnitsUsed = BigDecimal.ZERO; // No base units available
        } else if (newTotalUnits.compareTo(baseRequestsDecimal) <= 0) {
            baseUnitsUsed = additionalUsedUnits; // All units are base units
        } else {
            baseUnitsUsed = baseRequestsDecimal.subtract(totalUnitsUsed); // Use up to the limit
        }

        // Calculate overage units for THIS request only (not cumulative)
        BigDecimal overageUnitsThisRequest = additionalUsedUnits.subtract(baseUnitsUsed).max(BigDecimal.ZERO);

        BigDecimal chargeableOverageUnits = BigDecimal.ZERO;
        BigDecimal surplusOverageUnits = BigDecimal.ZERO;
        BigDecimal overageCost = BigDecimal.ZERO;

        if (isOverLimit && isOverageAllowed && !exceedsMaxLimit) {
            BigDecimal remainingCapBefore = overageSpendingCap.subtract(totalOverageCost);

            if (remainingCapBefore.compareTo(BigDecimal.ZERO) >= 0 && overageRate.compareTo(BigDecimal.ZERO) > 0) {
                // How many units can we charge within the remaining cap?
                // Use >= 0 to allow requests that exactly hit the cap
                BigDecimal affordableUnits = remainingCapBefore.divide(overageRate, 2, RoundingMode.DOWN).max(BigDecimal.ZERO);
                // Only charge for the overage units in THIS request, up to the affordable limit
                chargeableOverageUnits = overageUnitsThisRequest.min(affordableUnits);
                surplusOverageUnits = overageUnitsThisRequest.subtract(chargeableOverageUnits);
                overageCost = chargeableOverageUnits.multiply(overageRate).setScale(2, RoundingMode.UP);
            } else {
                // No remaining cap, all overage units are surplus
                surplusOverageUnits = overageUnitsThisRequest;
            }
        } else if (exceedsMaxLimit) {
            // No overage allowed beyond max limit
            surplusOverageUnits = overageUnitsThisRequest;
        }

        // isOverage should be true if:
        // 1. We're over the limit AND overage is allowed, OR
        // 2. There's actual overage cost (meaning overage occurred regardless of isOverageAllowed flag)
        // This ensures that if overage cost > 0, isOverage is true for proper tracking
        // IMPORTANT: When newTotalUnits exactly equals baseRequests, we're still within the limit (non-overage)
        boolean finalIsOverage = (isOverLimit && isOverageAllowed) || 
                                 (overageCost.compareTo(BigDecimal.ZERO) > 0);
        // Explicit check: if we're exactly at the limit (newTotalUnits == baseRequests), we're not overage
        if (newTotalUnits.compareTo(baseRequestsDecimal) == 0) {
            finalIsOverage = false;
        }
        log.info("[SPENDING_CAP_DEBUG] [CALC] isOverLimit={}, isOverageAllowed={}, overageCost={}, finalIsOverage={}, newTotalUnits={}, totalUnitsUsed={}, baseRequests={}", 
            isOverLimit, isOverageAllowed, overageCost, finalIsOverage, newTotalUnits, totalUnitsUsed, baseRequests);
        
        // Calculate remaining spending cap, ensuring it never goes negative due to rounding
        // Use max(0) to handle cases where rounding causes slight negative values
        BigDecimal calculatedRemainingCap = overageSpendingCap.subtract(totalOverageCost.add(overageCost));
        BigDecimal remainingSpendingCap = calculatedRemainingCap.max(BigDecimal.ZERO);
        
        return UsageCalculation.builder()
                .unitsUsed(additionalUsedUnits)
                .totalUnitsUsedThisCycle(newTotalUnits)
                .unitsRemaining(unitsRemaining)
                .overageUnits(cumulativeOverageUnits) // Cumulative overage units for reporting
                .chargeableOverageUnits(chargeableOverageUnits) // Chargeable overage units for THIS request
                .surplusOverageUnits(surplusOverageUnits) // Surplus overage units for THIS request
                .baseUnitsUsed(baseUnitsUsed)
                .overageCost(overageCost) // Cost for THIS request only
                .totalOverageCost(totalOverageCost.add(overageCost)) // Cumulative cost including this request
                .remainingSpendingCap(remainingSpendingCap) // Never negative due to rounding
                .isOverLimit(isOverLimit)
                // isOverage is true if over limit and overage is allowed, OR if there's actual overage cost
                // This ensures proper tracking of overage usage even if isOverageAllowed flag is inconsistent
                .isOverage(finalIsOverage)
                .isOverageAllowed(isOverageAllowed)
                .build();
    }
} 