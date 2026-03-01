package com.bugisiw.marketplace.common.model.usage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a usage log entry for tracking agent usage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageLog {
    /**
     * Unique identifier for the usage log.
     */
    private long id;
    
    /**
     * ID of the user product (subscription) associated with this usage.
     * This is the id from the user_products table.
     */
    private UUID userProductId;
    
    /**
     * ID of the user who made the request.
     */
    private UUID userId;
    
    /**
     * ID of the agent that was used.
     */
    private UUID agentId;
    
    /**
     * ID of the agent endpoint that was called.
     * Null for non-proxied agents that don't report endpoint information.
     */
    private UUID agentEndpointId;
    
    /**
     * Unique identifier for the request.
     */
    private String requestId;
    
    /**
     * Type of request (SYNC or ASYNC).
     */
    private RequestType requestType;
    
    /**
     * Type of billing for this usage.
     */
    private String billingType;
    
    /**
     * Cumulative units used in the current billing cycle.
     */
    private BigDecimal cumulativeUnits;
    
    /**
     * Total units used for this record.
     */
    private BigDecimal totalUnitsUsed;
    
    /**
     * Portion of total units that were base units.
     */
    private BigDecimal baseUnitsUsed;
    
    /**
     * Chargeable overage units up to the spending cap.
     */
    private BigDecimal chargeableOverageUnitsUsed;
    
    /**
     * Non-chargeable overage units beyond the spending cap.
     */
    private BigDecimal nonChargeableOverageUnitsUsed;
    
    /**
     * Cost of chargeable units.
     */
    private BigDecimal cost;
    
    /**
     * Whether this usage exceeds the user's subscription limits.
     * This field maps to 'is_over_limit' in the database.
     */
    private boolean isOverLimit;
    
    /**
     * Whether this usage is billable as an overage.
     * This field maps to 'is_overage' in the database.
     */
    private boolean isOverage;
    
    /**
     * Current status of the job.
     */
    private JobStatus status;
    
    /**
     * Time when the request started.
     */
    private Instant startTime;

    /**
     * Time when the request completed.
     */
    private Instant endTime;

    /**
     * Result of the request (may be truncated if large).
     */
    private String result;
    
    /**
     * URL where the full result can be accessed.
     */
    private String resultUrl;
    
    /**
     * Content type of the result.
     */
    private String contentType;
    
    /**
     * Error message if the job failed.
     */
    private String errorMessage;
    
    /**
     * HTTP status code from the agent endpoint response.
     * Null for non-proxied agents or if not available.
     */
    private Integer httpStatusCode;
    
    /**
     * Time when this record was created.
     */
    private Instant createdAt;

    /**
     * Time when this record was last updated.
     */
    private Instant updatedAt;
    
    /**
     * Whether this invocation was made by the agent owner (not billable).
     * When true, the agent owner is testing their own agent and should not be charged.
     */
    private boolean isAgentOwnerInvocation;

    /**
     * Whether this usage is billable.
     * false for requests that exceeded spending cap (SUBSCRIPTION) or insufficient credits (PREPAID).
     * Used for audit trail - all agent invocations are logged, but not all are billable.
     */
    private Boolean isBillable = true;
} 