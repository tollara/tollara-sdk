package com.bugisiw.marketplace.common.model.billing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * DTO for tiered pricing tier information.
 * Used in Subscription DTO to represent tiered pricing configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TierDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Tier threshold (starting point for this tier)
     */
    private BigDecimal threshold;

    /**
     * Price per unit in this tier
     */
    private BigDecimal unitAmount;

    /**
     * Tier order (1, 2, 3, etc.)
     */
    private Integer ordering;
}

