package com.bugisiw.marketplace.common.model.billing.config;

import com.bugisiw.marketplace.common.model.billing.AgentProduct;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "agent_product_tiered_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentProductTieredConfig implements Serializable {
    @Id
    @Column(name = "agent_product_id", columnDefinition = "UUID")
    private UUID agentProductId;

    @OneToOne
    @JoinColumn(name = "agent_product_id", nullable = false)
    @MapsId
    private AgentProduct agentProduct;

    @Column(name = "unit_label")
    private String unitLabel;

    @Column(name = "tiers_mode", nullable = false)
    private String tiersMode; // graduated | volume

    @Column(name = "billing_period", nullable = false)
    private String billingPeriod; // day, week, month, year

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_product_id", referencedColumnName = "agent_product_id")
    private List<AgentProductTieredTier> tiers;
}

