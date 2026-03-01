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
@Table(name = "agent_product_per_timeunit_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentProductPerTimeUnitConfig implements Serializable {
    @Id
    @Column(name = "agent_product_id", columnDefinition = "UUID")
    private UUID agentProductId;

    @OneToOne
    @JoinColumn(name = "agent_product_id", nullable = false)
    @MapsId
    private AgentProduct agentProduct;

    @Column(name = "unit_label")
    private String unitLabel;

    @Column(name = "per_unit_price", nullable = false)
    private BigDecimal perUnitPrice;

    @Column(name = "billing_period", nullable = false)
    private String billingPeriod; // day, week, month, year

    @Column(name = "round_to_units")
    private BigDecimal roundToUnits;
}

