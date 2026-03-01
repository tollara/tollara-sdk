package com.bugisiw.marketplace.common.model.billing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a user's product agreement with an agent's product.
 * This class tracks the user-specific details of a product agreement, including
 * usage limits, trial periods, and billing status. Not all agreements are subscriptions
 * (e.g., PREPAID, INSTANT_ONLY).
 */
@Entity
@Table(name = "user_products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class UserProduct implements Serializable {
    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "UUID")
    private UUID userId;

    @Transient
    private Object user; // Transient field for User entity (populated in core-service only)

    @Column(name = "agent_id", nullable = false, length = 50)
    private String agentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false)
    private SubscriptionPlan plan;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "agent_product_id", nullable = false)
    private AgentProduct product;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "billing_model_id")
    private BillingModel billingModel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column
    private String stripeSubscriptionId; // Nullable for PREPAID and USAGE_INSTANT

    @Column(name = "stripe_base_subscription_item_id")
    private String stripeBaseSubscriptionItemId; // Licensed item for base subscription (hybrid SUBSCRIPTION)

    @Column(name = "stripe_metered_subscription_item_id")
    private String stripeMeteredSubscriptionItemId; // Metered item for overage (hybrid SUBSCRIPTION) or all usage (USAGE_POSTPAID)

    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_type")
    private AgreementType agreementType;

    @Column(name = "is_overage_allowed")
    private boolean isOverageAllowed;

    @Column(name = "overage_spending_cap")
    private BigDecimal overageSpendingCap;

    @Column(name = "remaining_credits")
    private BigDecimal remainingCredits;

    @Column(name = "total_units_used_this_cycle", nullable = false)
    @Builder.Default
    private BigDecimal totalUnitsUsedThisCycle = BigDecimal.ZERO;

    @Column(name = "total_overage_cost_this_cycle", nullable = false)
    @Builder.Default
    private BigDecimal totalOverageCostThisCycle = BigDecimal.ZERO;

    @Column(name = "cycle_start_date", nullable = false)
    private Instant cycleStartDate;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "current_tier")
    private String currentTier;

    @Column(name = "trial_end_date")
    private Instant trialEndDate;

    @Column(nullable = false)
    private Instant startDate;

    private Instant endDate;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        // Set cycleStartDate to startDate if not already set (defaults to start of billing cycle)
        if (cycleStartDate == null) {
            cycleStartDate = startDate != null ? startDate : Instant.now();
        }
        // Set agentId from product if not already set
        if (agentId == null && product != null && product.getAgent() != null) {
            agentId = product.getAgent().getId().toString();
        }
        // Ensure userId is set - if user field is available and is a User entity, extract userId from it
        if (userId == null && user != null) {
            try {
                // Use reflection to get the id from the user object if it's a User entity
                java.lang.reflect.Method getIdMethod = user.getClass().getMethod("getId");
                Object userIdObj = getIdMethod.invoke(user);
                if (userIdObj instanceof UUID) {
                    userId = (UUID) userIdObj;
                }
            } catch (Exception e) {
                // Ignore reflection errors - userId must be set explicitly
            }
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

