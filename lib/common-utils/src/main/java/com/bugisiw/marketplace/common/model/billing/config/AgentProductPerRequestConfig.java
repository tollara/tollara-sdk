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
@Table(name = "agent_product_per_request_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentProductPerRequestConfig implements Serializable {
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

    @Column(name = "billing_period")
    private String billingPeriod; // none, day, week, month, year

    @Column(name = "base_units")
    private BigDecimal baseUnits;

    @Column(name = "max_units")
    private BigDecimal maxUnits;

    @Column(name = "trial_days")
    private Integer trialDays;

    /** How usage is measured: PER_REQUEST, PER_TIME_UNIT, PER_TOKEN, PER_BYTE. Null = derive from unit_label. */
    @Column(name = "measurement_type", length = 20)
    private String measurementType;

    /** JTokkit encoding name (e.g. cl100k_base) for PER_TOKEN products. From product config only. */
    @Column(name = "tokenizer_encoding", length = 50)
    private String tokenizerEncoding;
}

