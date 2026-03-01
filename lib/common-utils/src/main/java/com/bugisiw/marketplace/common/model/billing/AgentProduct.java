package com.bugisiw.marketplace.common.model.billing;

import com.bugisiw.marketplace.common.model.agent.Agent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
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
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "agent_products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AgentProduct implements Serializable {
    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "agent_id", nullable = false)
    @JsonIgnore // Exclude from JSON - use getAgentId() instead
    private Agent agent;

    @Column(nullable = false)
    private String productName;

    @Column
    private String stripeProductId;

    @Column
    private String stripePriceId;

    @Column(name = "stripe_overage_price_id")
    private String stripeOveragePriceId; // For SUBSCRIPTION model only (metered overage price)

    @Column(name = "stripe_meter_id")
    private String stripeMeterId; // For USAGE_POSTPAID and SUBSCRIPTION (overage) models

    @Column(name = "stripe_meter_event_name")
    private String stripeMeterEventName; // Event name for the meter (e.g., "usage_<product_id>")

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "billing_model_id", nullable = false)
    // billingModel is included in JSON for frontend compatibility
    // getBillingModelId() and getBillingModelType() remain for backward compatibility
    private BillingModel billingModel;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "retirement_date")
    private Instant retirementDate;

    @Column(name = "retired_by", columnDefinition = "UUID")
    private UUID retiredBy;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    // Transient field for tiered pricing tiers (populated by controller for JSON serialization)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient List<Tier> tiers;

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
     * Get agent ID for JSON serialization.
     * This allows the frontend to access agentId directly without needing to navigate the nested agent object.
     */
    @JsonProperty("agentId")
    public String getAgentId() {
        return agent != null ? agent.getId().toString() : null;
    }

    /**
     * Get agent name for JSON serialization.
     * This allows the frontend to access agent name directly without needing to navigate the nested agent object.
     */
    @JsonProperty("agentName")
    public String getAgentName() {
        return agent != null ? agent.getName() : null;
    }

    /**
     * Get billing model ID for JSON serialization.
     * This allows the frontend to access billingModelId directly without needing to navigate the nested billingModel object.
     */
    @JsonProperty("billingModelId")
    public String getBillingModelId() {
        return billingModel != null ? billingModel.getId().toString() : null;
    }

    /**
     * Get billing model type for JSON serialization.
     * This allows the frontend to access the model type without needing the full billingModel object.
     */
    @JsonProperty("billingModelType")
    public String getBillingModelType() {
        return billingModel != null && billingModel.getModelType() != null 
                ? billingModel.getModelType().name() : null;
    }

    /**
     * Checks if the product is retired (retirement date has passed or is now).
     *
     * @return true if retirementDate is set and has passed or is now
     */
    public boolean isRetired() {
        if (retirementDate == null) {
            return false;
        }
        Instant now = Instant.now();
        return retirementDate.isBefore(now) || retirementDate.equals(now);
    }

    /**
     * Checks if the product is in grace period (retirement date is in the future).
     *
     * @return true if retirementDate is set and is in the future
     */
    public boolean isInGracePeriod() {
        return retirementDate != null && retirementDate.isAfter(Instant.now());
    }

    /**
     * Get tiers for JSON serialization.
     * This allows the frontend to access tiers directly for tiered pricing display.
     * Populated by controller when product has tiered config.
     */
    @JsonProperty("tiers")
    public List<Tier> getTiers() {
        return tiers;
    }

    /**
     * Set tiers (used by controller to populate for JSON serialization).
     */
    public void setTiers(List<Tier> tiers) {
        this.tiers = tiers;
    }
}

