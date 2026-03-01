package com.bugisiw.marketplace.common.service;

import com.bugisiw.marketplace.common.model.billing.Subscription;
import com.bugisiw.marketplace.common.model.usage.UsageCalculation;

import java.math.BigDecimal;

/**
 * Interface for calculating usage costs in the AI Agent Marketplace.
 * Implementations calculate costs based on usage data and subscription details.
 * This interface does not handle data retrieval, as that is managed by TotalUnitsCalculator.
 */
public interface UsageCostCalculator {
    /**
     * Calculates the cost of usage for a given subscription, considering the subscription's billing cycle
     * and the provided total units used and total overage costs.
     *
     * @param subscription       The subscription details containing user and agent information.
     * @param totalUnitsUsed     The total units used in the current billing cycle.
     * @param totalOverageCost   The total overage cost in the current billing cycle.
     * @param additionalUnitsUsed The additional units used in this request (e.g., 1 request or time duration).
     * @return A UsageCalculation object containing detailed cost information.
     */
    UsageCalculation calculateCost(Subscription subscription,
                                   BigDecimal totalUnitsUsed,
                                   BigDecimal totalOverageCost,
                                   BigDecimal additionalUnitsUsed);
} 