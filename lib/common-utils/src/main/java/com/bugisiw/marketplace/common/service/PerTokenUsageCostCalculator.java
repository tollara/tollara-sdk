package com.bugisiw.marketplace.common.service;

import com.bugisiw.marketplace.common.model.billing.BillingModelType;
import com.bugisiw.marketplace.common.model.billing.MeasurementType;
import com.bugisiw.marketplace.common.model.billing.Subscription;
import com.bugisiw.marketplace.common.model.usage.UsageCalculation;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Implementation of UsageCostCalculator for token-based billing.
 * Same formula as PerRequestUsageCostCalculator but uses perTokenBaseUnits, perTokenMaxUnits, perTokenTimeUnit.
 */
@Slf4j
public class PerTokenUsageCostCalculator implements UsageCostCalculator {

    @Override
    public UsageCalculation calculateCost(Subscription subscription,
                                          BigDecimal totalUnitsUsed,
                                          BigDecimal totalOverageCost,
                                          BigDecimal additionalUsedUnits) {
        if (subscription.getMeasurementType() != MeasurementType.PER_TOKEN ||
                (subscription.getBillingModelType() != BillingModelType.SUBSCRIPTION &&
                        subscription.getBillingModelType() != BillingModelType.USAGE_POSTPAID)) {
            throw new IllegalArgumentException("PerTokenUsageCostCalculator can only handle token-based SUBSCRIPTION or USAGE_POSTPAID subscriptions");
        }

        BigDecimal baseUnits = subscription.getPerTokenBaseUnits() != null ? subscription.getPerTokenBaseUnits() : BigDecimal.ZERO;
        BigDecimal maxUnits = subscription.getPerTokenMaxUnits() != null ? subscription.getPerTokenMaxUnits() : new BigDecimal(Long.MAX_VALUE);
        BigDecimal overageSpendingCap = subscription.getOverageSpendingCap() != null ? subscription.getOverageSpendingCap() : BigDecimal.ZERO;
        BigDecimal overageRate = subscription.getOverageRate() != null ? subscription.getOverageRate() : BigDecimal.ZERO;
        if (overageRate.compareTo(BigDecimal.ZERO) == 0 && subscription.getBillingModelType() == BillingModelType.USAGE_POSTPAID) {
            throw new IllegalArgumentException("USAGE_POSTPAID subscription missing perUnitPrice (overageRate). Subscription ID: " + subscription.getId());
        }
        boolean isOverageAllowed = subscription.isOverageAllowed();

        BigDecimal newTotalUnits = totalUnitsUsed.add(additionalUsedUnits);
        boolean isOverLimit = newTotalUnits.compareTo(baseUnits) > 0;
        boolean exceedsMaxLimit = newTotalUnits.compareTo(maxUnits) > 0;
        BigDecimal cumulativeOverageUnits = isOverLimit ? newTotalUnits.subtract(baseUnits) : BigDecimal.ZERO;
        BigDecimal unitsRemaining = baseUnits.subtract(newTotalUnits).max(BigDecimal.ZERO);

        BigDecimal baseUnitsUsed;
        if (totalUnitsUsed.compareTo(baseUnits) >= 0) {
            baseUnitsUsed = BigDecimal.ZERO;
        } else if (newTotalUnits.compareTo(baseUnits) <= 0) {
            baseUnitsUsed = additionalUsedUnits;
        } else {
            baseUnitsUsed = baseUnits.subtract(totalUnitsUsed);
        }

        BigDecimal overageUnitsThisRequest = additionalUsedUnits.subtract(baseUnitsUsed).max(BigDecimal.ZERO);
        BigDecimal chargeableOverageUnits = BigDecimal.ZERO;
        BigDecimal surplusOverageUnits = BigDecimal.ZERO;
        BigDecimal overageCost = BigDecimal.ZERO;

        if (isOverLimit && isOverageAllowed && !exceedsMaxLimit) {
            BigDecimal remainingCapBefore = overageSpendingCap.subtract(totalOverageCost);
            if (remainingCapBefore.compareTo(BigDecimal.ZERO) >= 0 && overageRate.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal affordableUnits = remainingCapBefore.divide(overageRate, 2, RoundingMode.DOWN).max(BigDecimal.ZERO);
                chargeableOverageUnits = overageUnitsThisRequest.min(affordableUnits);
                surplusOverageUnits = overageUnitsThisRequest.subtract(chargeableOverageUnits);
                overageCost = chargeableOverageUnits.multiply(overageRate).setScale(2, RoundingMode.UP);
            } else {
                surplusOverageUnits = overageUnitsThisRequest;
            }
        } else if (exceedsMaxLimit) {
            surplusOverageUnits = overageUnitsThisRequest;
        }

        boolean finalIsOverage = (isOverLimit && isOverageAllowed) || (overageCost.compareTo(BigDecimal.ZERO) > 0);
        if (newTotalUnits.compareTo(baseUnits) == 0) {
            finalIsOverage = false;
        }

        BigDecimal calculatedRemainingCap = overageSpendingCap.subtract(totalOverageCost.add(overageCost));
        BigDecimal remainingSpendingCap = calculatedRemainingCap.max(BigDecimal.ZERO);

        return UsageCalculation.builder()
                .unitsUsed(additionalUsedUnits)
                .totalUnitsUsedThisCycle(newTotalUnits)
                .unitsRemaining(unitsRemaining)
                .overageUnits(cumulativeOverageUnits)
                .chargeableOverageUnits(chargeableOverageUnits)
                .surplusOverageUnits(surplusOverageUnits)
                .baseUnitsUsed(baseUnitsUsed)
                .overageCost(overageCost)
                .totalOverageCost(totalOverageCost.add(overageCost))
                .remainingSpendingCap(remainingSpendingCap)
                .isOverLimit(isOverLimit)
                .isOverage(finalIsOverage)
                .isOverageAllowed(isOverageAllowed)
                .build();
    }
}
