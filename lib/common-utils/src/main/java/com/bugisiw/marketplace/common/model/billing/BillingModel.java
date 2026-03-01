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

@Entity
@Table(name = "billing_models")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class BillingModel implements Serializable {
    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_type", nullable = false)
    private BillingModelType modelType;

    @Column(name = "stripe_usage_type")
    private String stripeUsageType; // licensed | metered | NULL

    @Column(name = "stripe_billing_scheme")
    private String stripeBillingScheme; // per_unit | tiered | NULL

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_strategy", nullable = false)
    private SettlementStrategy settlementStrategy;

    @Column(name = "is_recurring", nullable = false)
    private Boolean isRecurring;

    @Column(name = "unit_label")
    private String unitLabel; // request, token, hour, etc.

    @Column(name = "default_included_units")
    private BigDecimal defaultIncludedUnits;

    @Column(nullable = false)
    private BigDecimal overageRate;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
} 