package com.bugisiw.marketplace.common.service;

import com.bugisiw.marketplace.common.model.billing.BillingModelType;
import com.bugisiw.marketplace.common.model.billing.MeasurementType;
import com.bugisiw.marketplace.common.model.billing.Subscription;
import com.bugisiw.marketplace.common.model.usage.UsageCalculation;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Implementation of UsageCostCalculator for time-unit based billing.
 * Calculates costs based on time unit usage (e.g., hours) within the billing cycle.
 * Works with SUBSCRIPTION and USAGE_POSTPAID models that use time-based units.
 */
@Slf4j
public class PerTimeUnitUsageCostCalculator implements UsageCostCalculator {

    @Override
    public UsageCalculation calculateCost(Subscription subscription,
                                          BigDecimal totalUnitsUsed,
                                          BigDecimal totalOverageCost,
                                          BigDecimal additionalUsedUnits) {
        if (subscription.getMeasurementType() != MeasurementType.PER_TIME_UNIT) {
            throw new IllegalArgumentException("PerTimeUnitUsageCostCalculator can only handle PER_TIME_UNIT subscriptions");
        }
        if (subscription.getBillingModelType() != BillingModelType.SUBSCRIPTION &&
                subscription.getBillingModelType() != BillingModelType.USAGE_POSTPAID) {
            throw new IllegalArgumentException("PerTimeUnitUsageCostCalculator can only handle SUBSCRIPTION or USAGE_POSTPAID subscriptions");
        }

        BigDecimal baseTimeUnits = subscription.getPerTimeUnitBaseUnits();
        BigDecimal maxTimeUnits = subscription.getPerTimeUnitMaxUnits();
        BigDecimal overageSpendingCap = subscription.getOverageSpendingCap() != null ? 
                subscription.getOverageSpendingCap() : BigDecimal.ZERO;
        BigDecimal overageRate = subscription.getOverageRate() != null ? 
                subscription.getOverageRate() : BigDecimal.ZERO;
        boolean isOverageAllowed = subscription.isOverageAllowed();

        // Defensive: treat null accumulators as zero (e.g. legacy rows or first request in cycle)
        BigDecimal totalUnitsUsedSafe = totalUnitsUsed != null ? totalUnitsUsed : BigDecimal.ZERO;
        BigDecimal totalOverageCostSafe = totalOverageCost != null ? totalOverageCost : BigDecimal.ZERO;

        // Calculate new totals with this request
        BigDecimal newTotalUnits = totalUnitsUsedSafe.add(additionalUsedUnits).setScale(2, RoundingMode.HALF_UP);
        boolean isOverLimit = newTotalUnits.compareTo(baseTimeUnits) > 0;
        boolean exceedsMaxLimit = newTotalUnits.compareTo(maxTimeUnits) > 0;
        BigDecimal cumulativeOverageUnits = isOverLimit ? newTotalUnits.subtract(baseTimeUnits) : BigDecimal.ZERO;
        BigDecimal unitsRemaining = baseTimeUnits.subtract(newTotalUnits).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        // Calculate baseUnitsUsed for this transaction
        BigDecimal baseUnitsUsed;
        if (totalUnitsUsedSafe.compareTo(baseTimeUnits) >= 0) {
            baseUnitsUsed = BigDecimal.ZERO; // No base units available
        } else if (newTotalUnits.compareTo(baseTimeUnits) <= 0) {
            baseUnitsUsed = additionalUsedUnits; // All units are base units
        } else {
            baseUnitsUsed = baseTimeUnits.subtract(totalUnitsUsedSafe); // Use up to the limit
        }

        // Calculate overage units for THIS request only (not cumulative)
        BigDecimal overageUnitsThisRequest = additionalUsedUnits.subtract(baseUnitsUsed).max(BigDecimal.ZERO);

        BigDecimal chargeableOverageUnits = BigDecimal.ZERO;
        BigDecimal surplusOverageUnits = BigDecimal.ZERO;
        BigDecimal overageCost = BigDecimal.ZERO;

        if (isOverLimit && isOverageAllowed && !exceedsMaxLimit) {
            BigDecimal remainingCapBefore = overageSpendingCap.subtract(totalOverageCostSafe);

            if (remainingCapBefore.compareTo(BigDecimal.ZERO) >= 0) {
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
            surplusOverageUnits = overageUnitsThisRequest; // No overage allowed beyond max limit
        }

        return UsageCalculation.builder()
                .unitsUsed(additionalUsedUnits)
                .totalUnitsUsedThisCycle(newTotalUnits)
                .unitsRemaining(unitsRemaining)
                .overageUnits(cumulativeOverageUnits) // Cumulative overage units for reporting
                .chargeableOverageUnits(chargeableOverageUnits) // Chargeable overage units for THIS request
                .surplusOverageUnits(surplusOverageUnits) // Surplus overage units for THIS request
                .baseUnitsUsed(baseUnitsUsed)
                .overageCost(overageCost) // Cost for THIS request only
                .totalOverageCost(totalOverageCostSafe.add(overageCost)) // Cumulative cost including this request
                .remainingSpendingCap(overageSpendingCap.subtract(totalOverageCostSafe.add(overageCost)))
                .isOverLimit(isOverLimit)
                // isOverage should be true if over limit and overage is allowed, regardless of maxTimeUnits
                // maxTimeUnits only affects whether we can charge for it (chargeable vs surplus), not whether it's overage
                .isOverage(isOverLimit && isOverageAllowed)
                .isOverageAllowed(isOverageAllowed)
                .build();
    }
} 