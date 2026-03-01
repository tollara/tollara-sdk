package com.bugisiw.marketplace.common.model.billing;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coupons")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Coupon implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "agent_product_id", nullable = false)
    @JsonIgnore // Exclude from JSON - use getAgentProductId() instead
    private AgentProduct agentProduct;

    @Column(name = "created_by", nullable = false, columnDefinition = "UUID")
    private UUID createdBy; // Agent owner UUID (user.id)

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String code; // Promotion code (case-insensitive, unique)

    @Column(name = "stripe_coupon_id", nullable = false, unique = true)
    private String stripeCouponId;

    @Column(name = "stripe_promotion_code_id", nullable = false, unique = true)
    private String stripePromotionCodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue; // Percent (0-100) or amount in cents

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Duration duration;

    @Column(name = "duration_in_months")
    private Integer durationInMonths; // For REPEATING duration

    @Column(name = "expires_at")
    private Instant expiresAt; // Optional expiration date

    @Column(name = "max_redemptions_total")
    private Integer maxRedemptionsTotal; // Global limit

    @Column(name = "max_redemptions_per_customer")
    private Integer maxRedemptionsPerCustomer; // Per-customer limit

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Get agent product ID for JSON serialization.
     */
    @JsonProperty("agentProductId")
    public String getAgentProductId() {
        return agentProduct != null ? agentProduct.getId().toString() : null;
    }

    /**
     * Checks if the coupon is expired.
     *
     * @return true if expiresAt is set and has passed
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return expiresAt.isBefore(Instant.now());
    }

    /**
     * Checks if the coupon is valid (active and not expired).
     *
     * @return true if coupon is active and not expired
     */
    public boolean isValid() {
        return Boolean.TRUE.equals(isActive) && !isExpired();
    }

    /**
     * Discount type enum: PERCENT or AMOUNT
     */
    public enum DiscountType {
        PERCENT,  // 0-100
        AMOUNT    // Amount in cents
    }

    /**
     * Duration enum: ONCE, REPEATING, or FOREVER
     */
    public enum Duration {
        ONCE,      // One-time discount
        REPEATING, // Applies for N billing cycles
        FOREVER   // Applies indefinitely
    }
}
