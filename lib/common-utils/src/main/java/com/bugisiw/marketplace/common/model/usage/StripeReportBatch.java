package com.bugisiw.marketplace.common.model.usage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a batch of usage reports sent to Stripe.
 * Multiple stripe_usage_reports can belong to the same batch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripeReportBatch {
    /**
     * Unique identifier for the batch.
     */
    private Long id;
    
    /**
     * Unique batch identifier (UUID string).
     */
    private String batchId;
    
    /**
     * ID of the user product (subscription) associated with this batch.
     */
    private UUID userProductId;
    
    /**
     * Stripe subscription item ID this batch was reported to.
     */
    private String stripeSubscriptionItemId;
    
    /**
     * Total units aggregated across all reports in this batch.
     */
    private BigDecimal totalUnits;
    
    /**
     * Number of reports included in this batch.
     */
    private Integer reportCount;
    
    /**
     * Stripe usage record ID (returned by Stripe API after reporting).
     */
    private String stripeUsageRecordId;
    
    /**
     * Current status of the batch.
     */
    private StripeReportStatus reportStatus;
    
    /**
     * Error message if reporting failed.
     */
    private String errorMessage;
    
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

