package com.bugisiw.marketplace.common.service;

import com.bugisiw.marketplace.common.model.billing.BillingModelType;
import com.bugisiw.marketplace.common.model.billing.Subscription;
import com.bugisiw.marketplace.common.model.billing.TierDto;
import com.bugisiw.marketplace.common.model.usage.UsageCalculation;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of UsageCostCalculator for tiered pricing (USAGE_POSTPAID with tiers).
 * Calculates costs based on graduated tiered pricing where each tier applies to units in that tier range.
 * 
 * For graduated tiered pricing:
 * - Each tier applies to units in that tier's range
 * - The cost for a single request is based on which tier the cumulative units (after this request) fall into
 * 
 * Example:
 * Tier 1: 0-10 requests @ $1.00 per request
 * Tier 2: 11-20 requests @ $0.75 per request
 * Tier 3: 21+ requests @ $0.50 per request
 * 
 * If cumulative units before request = 5, and this request adds 1 unit:
 * - Cumulative units after = 6
 * - This request falls in Tier 1, so cost = $1.00
 * 
 * If cumulative units before request = 10, and this request adds 1 unit:
 * - Cumulative units after = 11
 * - This request falls in Tier 2, so cost = $0.75
 */
@Slf4j
public class TieredUsageCostCalculator implements UsageCostCalculator {

    @Override
    public UsageCalculation calculateCost(Subscription subscription,
                                          BigDecimal totalUnitsUsed,
                                          BigDecimal totalOverageCost,
                                          BigDecimal additionalUsedUnits) {
        // Validate billing model type
        if (subscription.getBillingModelType() != BillingModelType.USAGE_POSTPAID) {
            throw new IllegalArgumentException("TieredUsageCostCalculator can only handle USAGE_POSTPAID billing model");
        }

        List<TierDto> tiers = subscription.getTiers();
        if (tiers == null || tiers.isEmpty()) {
            throw new IllegalArgumentException("TieredUsageCostCalculator requires tiers configuration");
        }

        // Sort tiers by threshold (ascending)
        List<TierDto> sortedTiers = tiers.stream()
                .sorted(Comparator.comparing(TierDto::getThreshold))
                .collect(Collectors.toList());

        // Calculate cumulative units after this request
        BigDecimal cumulativeUnitsAfter = totalUnitsUsed.add(additionalUsedUnits);

        // Determine which tier this request falls into based on cumulative units after the request
        BigDecimal costPerUnit = findTierUnitAmount(cumulativeUnitsAfter, sortedTiers);
        
        // Calculate cost for this single request
        BigDecimal overageCost = additionalUsedUnits.multiply(costPerUnit)
                .setScale(2, RoundingMode.HALF_UP);

        // For tiered pricing, all units are charged (no base/overage distinction)
        // The cost is stored in overageCost field for consistency with other calculators
        BigDecimal spendingCap = subscription.getOverageSpendingCap();
        BigDecimal remainingSpendingCap = BigDecimal.ZERO;
        boolean wouldExceedCap = false;
        
        if (spendingCap != null && spendingCap.compareTo(BigDecimal.ZERO) > 0) {
            // Calculate total cost after this request
            BigDecimal totalCostAfter = calculateTotalTieredCost(cumulativeUnitsAfter, sortedTiers);
            remainingSpendingCap = spendingCap.subtract(totalCostAfter).max(BigDecimal.ZERO);
            wouldExceedCap = totalCostAfter.compareTo(spendingCap) > 0;
        }

        return UsageCalculation.builder()
                .unitsUsed(additionalUsedUnits)
                .totalUnitsUsedThisCycle(cumulativeUnitsAfter)
                .unitsRemaining(BigDecimal.ZERO) // No base units for tiered pricing
                .overageUnits(additionalUsedUnits) // All units are "overage" (charged)
                .chargeableOverageUnits(wouldExceedCap ? BigDecimal.ZERO : additionalUsedUnits)
                .surplusOverageUnits(wouldExceedCap ? additionalUsedUnits : BigDecimal.ZERO)
                .baseUnitsUsed(BigDecimal.ZERO) // No base units for tiered pricing
                .overageCost(overageCost) // Cost for this request
                .totalOverageCost(calculateTotalTieredCost(cumulativeUnitsAfter, sortedTiers)) // Total cost after this request
                .remainingSpendingCap(remainingSpendingCap)
                .isOverLimit(false) // No hard limits for tiered pricing (only spending cap)
                .isOverage(false) // All usage is "overage" (charged)
                .isOverageAllowed(true) // Tiered pricing always allows usage
                .build();
    }

    /**
     * Finds the unit amount (price per unit) for the tier that the cumulative units fall into.
     * For graduated tiered pricing, each request's cost is based on which tier the cumulative units (after this request) fall into.
     * 
     * For graduated tiered pricing:
     * - Tier 1 (threshold 0): applies to units 0-10 (inclusive of 10)
     * - Tier 2 (threshold 10): applies to units 11-20 (inclusive of 20)
     * - Tier 3 (threshold 20): applies to units 21+
     * 
     * So when cumulativeUnits equals a threshold, we use the previous tier (the tier with the lower threshold).
     * 
     * @param cumulativeUnits The cumulative units used (after this request)
     * @param sortedTiers Tiers sorted by threshold (ascending)
     * @return The unit amount for the appropriate tier
     */
    private BigDecimal findTierUnitAmount(BigDecimal cumulativeUnits, List<TierDto> sortedTiers) {
        if (cumulativeUnits.compareTo(BigDecimal.ZERO) <= 0) {
            // No units used - return first tier's unit amount
            return sortedTiers.get(0).getUnitAmount();
        }

        // Find the tier that this cumulative unit count falls into
        // For graduated pricing, we find the highest tier threshold that is strictly less than cumulative units
        // When cumulativeUnits equals a threshold, we use the previous tier
        TierDto applicableTier = sortedTiers.get(0); // Default to first tier
        
        for (int i = 0; i < sortedTiers.size(); i++) {
            TierDto tier = sortedTiers.get(i);
            BigDecimal tierThreshold = tier.getThreshold();
            
            // If cumulative units are strictly greater than this tier's threshold, this tier applies
            // When cumulativeUnits equals the threshold, we use the previous tier (already set)
            if (cumulativeUnits.compareTo(tierThreshold) > 0) {
                applicableTier = tier;
            } else {
                // We've reached or passed the threshold - use the tier we've already found
                break;
            }
        }
        
        return applicableTier.getUnitAmount();
    }

    /**
     * Calculates the total cost for all cumulative units using graduated tiered pricing.
     * This is used for spending cap checks.
     * 
     * @param totalUnits Total cumulative units
     * @param sortedTiers Tiers sorted by threshold (ascending)
     * @return Total cost for all units
     */
    private BigDecimal calculateTotalTieredCost(BigDecimal totalUnits, List<TierDto> sortedTiers) {
        if (totalUnits.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal remainingUnits = totalUnits;

        for (int i = 0; i < sortedTiers.size() && remainingUnits.compareTo(BigDecimal.ZERO) > 0; i++) {
            TierDto tier = sortedTiers.get(i);
            BigDecimal tierThreshold = tier.getThreshold();
            BigDecimal tierUnitAmount = tier.getUnitAmount();

            // Determine the upper bound for this tier
            BigDecimal tierUpperBound;
            if (i < sortedTiers.size() - 1) {
                // Not the last tier - upper bound is next tier's threshold
                tierUpperBound = sortedTiers.get(i + 1).getThreshold();
            } else {
                // Last tier - no upper bound (infinite)
                tierUpperBound = null;
            }

            // Calculate units in this tier
            BigDecimal unitsInTier;
            if (totalUnits.compareTo(tierThreshold) <= 0) {
                // Total units haven't reached this tier yet
                break;
            } else if (tierUpperBound == null || totalUnits.compareTo(tierUpperBound) <= 0) {
                // Units are within this tier range
                unitsInTier = totalUnits.subtract(tierThreshold);
            } else {
                // Units exceed this tier - use full tier range
                unitsInTier = tierUpperBound.subtract(tierThreshold);
            }

            // Calculate cost for this tier
            BigDecimal tierCost = unitsInTier.multiply(tierUnitAmount);
            totalCost = totalCost.add(tierCost);
            remainingUnits = remainingUnits.subtract(unitsInTier);

            log.debug("Tier {}: {} units @ ${}/unit = ${}", 
                tier.getOrdering(), unitsInTier, tierUnitAmount, tierCost);
        }

        return totalCost.setScale(2, RoundingMode.HALF_UP);
    }
}
