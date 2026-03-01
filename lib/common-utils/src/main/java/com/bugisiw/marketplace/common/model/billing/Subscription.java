package com.bugisiw.marketplace.common.model.billing;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a subscription in the system.
 */
@Entity
@Table(name = "subscriptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(nullable = false)
    private UUID userId;  // Internal User UUID

    /**
     * External user ID (Cognito sub) - used for API calls.
     * This is a transient field populated by SubscriptionMapperService.
     */
    @Transient
    private String extUserId;  // Cognito sub (String)

    @Column(nullable = false)
    private UUID agentId;

    /**
     * AgentProduct ID for this subscription.
     * Used by usage-service to call billing endpoints that require agentProductId.
     */
    @Column(name = "agent_product_id")
    private UUID agentProductId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column
    private String stripeSubscriptionId; // Nullable for PREPAID and USAGE_INSTANT models
    
    /**
     * Stripe subscription item ID for metered usage reporting.
     * Used for hybrid SUBSCRIPTION model (overage) and USAGE_POSTPAID model (all usage).
     * This is a transient field populated by SubscriptionMapperService from UserProduct.
     */
    @Transient
    @com.fasterxml.jackson.annotation.JsonProperty("stripeMeteredSubscriptionItemId")
    private String stripeMeteredSubscriptionItemId;
    
    /**
     * Type of billing model for this subscription.
     * Directly represents the billing model (SUBSCRIPTION, USAGE_POSTPAID, USAGE_INSTANT, PREPAID).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_model_type", nullable = false)
    private BillingModelType billingModelType;

    /**
     * How usage is measured (per request, per time unit, per token, per byte).
     * Populated by SubscriptionMapperService from config or derived from unitLabel when measurement_type is null.
     */
    @Transient
    private MeasurementType measurementType;

    /////////////////////////////////////////////////////////////////////////
    // Per TimeUnit (prev PER_HOUR)
    /////////////////////////////////////////////////////////////////////////

    /**
     * Time unit for rates and base units in PER_TIMEUNIT billing.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "per_time_unit")
    private TimeUnit perTimeUnit; // i.e. HOURS

    /**
     * Base units included in the subscription for PER_TIMEUNIT billing type.
     */
    @Column(name = "per_time_unit_base_units")
    private BigDecimal perTimeUnitBaseUnits; // e.g. 3 HOURS
    
    /**
     * Period over which the max units limit applies in PER_TIMEUNIT billing (previously 'Month').
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "per_time_unit_base_units_period")
    private TimeUnit perTimeUnitBaseUnitsPeriod; // e.g. Month

    /**
     * Maximum units a user can use including overage, regardless of their individual spending cap
     */
    @Column(name = "per_time_unit_max_units")
    private BigDecimal perTimeUnitMaxUnits; // e.g. 5 hours (3 hours above, plus 2 hours overage)

    /////////////////////////////////////////////////////////////////////////
    // Per Request
    /////////////////////////////////////////////////////////////////////////

    /**
     * Maximum requests allowed per time period for PER_REQUEST billing type.
     */
    @Column(name = "per_request_base_requests")
    private long perRequestBaseRequests; // e.g. Base requests per period, e.g. 100 requests
    
    /**
     * Time unit for the max requests limit in PER_REQUEST billing.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "per_request_time_unit")
    private TimeUnit perRequestTimeUnit; // e.g. MONTH -> 100 requests per MONTH

    /**
     * Maximum units a user can use including overage, regardless of their individual spending cap
     */
    @Column(name = "per_request_max_requests")
    private long perRequestMaxRequests; // e.g. 150 requests (100 request above, plus 50 overage)

    /////////////////////////////////////////////////////////////////////////
    // Per Token (for PER_TOKEN measurement)
    /////////////////////////////////////////////////////////////////////////

    /** Base units included for PER_TOKEN (e.g. 100_000 tokens per period). Populated by mapper from config. */
    @Transient
    private BigDecimal perTokenBaseUnits;

    /** Maximum units for PER_TOKEN including overage. Populated by mapper from config. */
    @Transient
    private BigDecimal perTokenMaxUnits;

    /** Time unit for per-token quota period (e.g. MONTH). Populated by mapper from config. */
    @Transient
    private TimeUnit perTokenTimeUnit;

    /** JTokkit encoding name (e.g. cl100k_base) for token counting. From product config only, not request header. */
    @Transient
    private String tokenizerEncoding;

    /////////////////////////////////////////////////////////////////////////
    // Per Byte (for PER_BYTE measurement)
    /////////////////////////////////////////////////////////////////////////

    /** Base units included for PER_BYTE (e.g. 10_000_000 bytes per period). Populated by mapper from config. */
    @Transient
    private BigDecimal perByteBaseUnits;

    /** Maximum units for PER_BYTE including overage. Populated by mapper from config. */
    @Transient
    private BigDecimal perByteMaxUnits;

    /** Time unit for per-byte quota period (e.g. MONTH). Populated by mapper from config. */
    @Transient
    private TimeUnit perByteTimeUnit;

    /////////////////////////////////////////////////////////////////////////
    // Rate Limit fields
    /////////////////////////////////////////////////////////////////////////

    /**
     * Rate limit units
     */
    @Column(name = "rate_limit_units")
    private BigDecimal rateLimitUnits; // e.g. 100

    /**
     * Time unit for the rate limit.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "rate_limit_time_unit")
    private TimeUnit rateLimitTimeUnit; // e.g. HOUR -> allow 100 requests per HOUR

    /**
     * Token rate limit units
     */
    @Column(name = "token_bucket_units")
    private BigDecimal tokenBucketUnits; // e.g. 10

    /**
     * Time unit for the token limits
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "token_bucket_time_unit")
    private TimeUnit tokenBucketTimeUnit; // e.g. SECOND -> allow 10 requests per SECOND, throttle if exceeded

    /////////////////////////////////////////////////////////////////////////
    // Common fields
    /////////////////////////////////////////////////////////////////////////

    /**
     * Whether overage is allowed beyond the subscription limits.
     */
    @Column(name = "is_overage_allowed")
    private boolean isOverageAllowed;
    
    /**
     * Maximum amount that can be spent on overage.
     */
    @Column(name = "overage_spending_cap")
    private BigDecimal overageSpendingCap;
    
    /**
     * Rate per unit of overage (cost of a request for PER_REQUEST, cost of time period for PER_TIMEUNIT)
     */
    @Column(name = "overage_rate")
    private BigDecimal overageRate;

    /**
     * Remaining prepaid credits for PREPAID billing model
     */
    @Column(name = "remaining_credits")
    private BigDecimal remainingCredits;

    /**
     * Unit label for identifying unit type (request, token, hour, etc.)
     * Used by gateway/usage services to determine calculation strategy
     */
    @Column(name = "unit_label")
    private String unitLabel;

    /**
     * Unit price for PREPAID billing model.
     * Calculated as product.price / config.packUnits
     */
    @Column(name = "prepaid_unit_price")
    private BigDecimal prepaidUnitPrice;

    /**
     * Tiered pricing configuration for USAGE_POSTPAID products with tiered config.
     * Each tier contains threshold, unitAmount, and ordering.
     * This field is populated by SubscriptionMapperService and is not stored in the database.
     */
    @Transient
    private List<TierDto> tiers;

    /**
     * Total units used in the current billing cycle (accumulator).
     * This field is populated by SubscriptionMapperService from UserProduct and is not stored in the database.
     */
    @Transient
    private BigDecimal totalUnitsUsedThisCycle;

    /**
     * Total overage cost in the current billing cycle (accumulator).
     * This field is populated by SubscriptionMapperService from UserProduct and is not stored in the database.
     */
    @Transient
    private BigDecimal totalOverageCostThisCycle;

    /**
     * Start date of the current billing cycle.
     * This field is populated by SubscriptionMapperService from UserProduct and is not stored in the database.
     */
    @Transient
    private Instant cycleStartDate;

    @Column(nullable = false)
    private Instant startDate;

    private Instant endDate;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
} 