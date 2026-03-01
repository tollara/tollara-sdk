package com.bugisiw.marketplace.common.model.billing;

/**
 * Enum representing the possible states of a user subscription.
 */
public enum SubscriptionStatus {
    /**
     * The subscription is active and in good standing.
     */
    ACTIVE,

    /**
     * The subscription has been canceled but is still valid until the end date.
     */
    CANCELLED,

    /**
     * The subscription has expired and is no longer valid.
     */
    EXPIRED,

    /**
     * The subscription is in a trial period.
     */
    TRIAL,

    /**
     * The subscription is incomplete due to a failed payment.
     */
    INCOMPLETE,

    /**
     * The subscription is scheduled to cancel at period end (confirmed via webhook).
     * Subscription has cancel_at_period_end=true in Stripe.
     */
    CANCELLING,

    /**
     * Cancellation was requested but waiting for webhook confirmation.
     * The cancellation request has been sent to Stripe but the webhook hasn't arrived yet.
     */
    CANCELLING_PENDING
} 