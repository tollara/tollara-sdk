package com.bugisiw.marketplace.common.model.usage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a usage report to be sent to Stripe.
 * One-to-one relationship with usage_logs.
 * Multiple reports can be batched together for efficient reporting.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripeUsageReport {
    /**
     * Unique identifier for the usage report.
     */
    private Long id;
    
    /**
     * ID of the usage log this report is associated with.
     */
    private Long usageLogId;
    
    /**
     * Batch ID this report belongs to (if batched).
     */
    private String batchId;
    
    /**
     * ID of the user product (subscription) associated with this usage.
     */
    private UUID userProductId;
    
    /**
     * Stripe subscription item ID to report usage to.
     */
    private String stripeSubscriptionItemId;
    
    /**
     * Unique identifier for the request.
     */
    private String requestId;
    
    /**
     * Units to report to Stripe.
     */
    private BigDecimal units;
    
    /**
     * Current status of the report.
     */
    private StripeReportStatus reportStatus;
    
    /**
     * Stripe usage record ID (returned by Stripe API after reporting).
     */
    private String stripeUsageRecordId;
    
    /**
     * Idempotency key for Stripe API call.
     */
    private String idempotencyKey;
    
    /**
     * Error message if reporting failed.
     */
    private String errorMessage;
    
    /**
     * Number of retry attempts.
     */
    private Integer retryCount;
    
    /**
     * Time of last retry attempt.
     */
    private Instant lastRetryAt;
    
    /**
     * Time when usage was reported to Stripe.
     */
    private Instant reportedAt;
    
    /**
     * Time when this record was created.
     */
    private Instant createdAt;
    
    /**
     * Time when this record was last updated.
     */
    private Instant updatedAt;
}

