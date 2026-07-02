package com.tollara.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Shared usage breakdown for estimate and report responses (see docs-sdk/MAIN-SDK-API-SPEC.md).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsageBreakdown {

    private BigDecimal unitsUsed;
    private BigDecimal baseUnitsUsed;
    private BigDecimal overageUnits;
    private BigDecimal chargeableOverageUnits;
    private BigDecimal surplusOverageUnits;
    private BigDecimal overageCost;
    private BigDecimal totalOverageCost;
    private BigDecimal unitsRemaining;
    /** PREPAID: credit balance after this chunk; null for other billing models. */
    private BigDecimal remainingCredits;
    private BigDecimal remainingSpendingCap;
    private BigDecimal totalUnitsUsedThisCycle;

    @JsonProperty("isOverLimit")
    private Boolean overLimit;

    @JsonProperty("isOverage")
    private Boolean overage;

    @JsonProperty("isOverageAllowed")
    private Boolean overageAllowed;
}
