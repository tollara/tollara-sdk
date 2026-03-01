package com.bugisiw.marketplace.common.model.billing;

/**
 * Enum representing the settlement strategy for billing models.
 * Determines how charges are settled with Stripe.
 */
public enum SettlementStrategy {
    SUBSCRIPTION_INVOICE,
    METERED_SUBSCRIPTION,
    INSTANT_CHARGE,
    PREPAID
}

