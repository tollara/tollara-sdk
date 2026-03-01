package com.bugisiw.marketplace.common.model.usage;

/**
 * Status of a Stripe usage report or batch.
 */
public enum StripeReportStatus {
    /**
     * Report is pending and waiting to be processed.
     */
    PENDING,
    
    /**
     * Report has been successfully sent to Stripe.
     */
    REPORTED,
    
    /**
     * Report failed to be sent to Stripe.
     */
    FAILED,
    
    /**
     * Report was reconciled (caught by invoice.upcoming webhook or reconciliation job).
     */
    RECONCILED
}

