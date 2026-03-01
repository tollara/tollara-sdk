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
@Table(name = "agent_product_subscription_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentProductSubscriptionConfig implements Serializable {
    @Id
    @Column(name = "agent_product_id", columnDefinition = "UUID")
    private UUID agentProductId;

    @OneToOne
    @JoinColumn(name = "agent_product_id", nullable = false)
    @MapsId
    private AgentProduct agentProduct;

    @Column(nullable = false)
    private String interval; // day, week, month, year

    @Column(name = "interval_count")
    private Integer intervalCount;

    @Column(name = "trial_days")
    private Integer trialDays;

    @Column(name = "included_units")
    private BigDecimal includedUnits;

    @Column(name = "overage_rate")
    private BigDecimal overageRate;

    @Column(name = "unit_label")
    private String unitLabel;

    /** How usage is measured: PER_REQUEST, PER_TIME_UNIT, PER_TOKEN, PER_BYTE. Null = derive from unit_label. */
    @Column(name = "measurement_type", length = 20)
    private String measurementType;

    /** JTokkit encoding name (e.g. cl100k_base) for PER_TOKEN products. From product config only. */
    @Column(name = "tokenizer_encoding", length = 50)
    private String tokenizerEncoding;
}

