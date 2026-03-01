package com.bugisiw.marketplace.common.model.billing.config;

import com.bugisiw.marketplace.common.model.billing.AgentProduct;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "agent_product_tiered_tiers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentProductTieredTier implements Serializable {
    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "agent_product_id", nullable = false)
    private AgentProduct agentProduct;

    @Column(nullable = false)
    private BigDecimal threshold;

    @Column(name = "unit_amount", nullable = false)
    private BigDecimal unitAmount;

    @Column(nullable = false)
    private Integer ordering;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}

