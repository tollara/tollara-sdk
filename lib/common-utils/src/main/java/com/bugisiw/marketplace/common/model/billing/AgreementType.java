package com.bugisiw.marketplace.common.model.billing;

/**
 * Enum representing the type of agreement between a user and an agent product.
 * Not all agreements are subscriptions (e.g., PREPAID, INSTANT_ONLY).
 */
public enum AgreementType {
    SUBSCRIPTION,
    METERED_SUBSCRIPTION,
    PREPAID,
    INSTANT_ONLY
}

