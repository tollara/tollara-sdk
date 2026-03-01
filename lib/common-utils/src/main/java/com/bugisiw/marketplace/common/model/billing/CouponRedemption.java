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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coupon_redemptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CouponRedemption implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "coupon_id", nullable = false)
    @JsonIgnore // Exclude from JSON - use getCouponId() instead
    private Coupon coupon;

    @Column(name = "user_id", nullable = false, columnDefinition = "UUID")
    private UUID userId;

    @Column(name = "stripe_customer_id", nullable = false)
    private String stripeCustomerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_product_id")
    @JsonIgnore // Exclude from JSON - use getUserProductId() instead
    private UserProduct userProduct; // Links to the user product that used the coupon (works for all billing models)

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (redeemedAt == null) {
            redeemedAt = Instant.now();
        }
    }

    /**
     * Get coupon ID for JSON serialization.
     */
    @JsonProperty("couponId")
    public String getCouponId() {
        return coupon != null ? coupon.getId().toString() : null;
    }

    /**
     * Get user product ID for JSON serialization.
     */
    @JsonProperty("userProductId")
    public String getUserProductId() {
        return userProduct != null ? userProduct.getId().toString() : null;
    }
}
