package com.bugisiw.marketplace.common.model.usage;

import com.fasterxml.jackson.annotation.JsonGetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Represents the calculation of costs based on usage.
 * Used to determine billing details for usage reporting.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageCalculation {
    private BigDecimal unitsUsed;          // Total units used (requests for PER_REQUEST, time units for PER_TIMEUNIT)
    private BigDecimal baseUnitsUsed;      // Total base units used (requests for PER_REQUEST, time units for PER_TIMEUNIT)
    private BigDecimal overageUnits;       // Total units over the limit
    private BigDecimal chargeableOverageUnits; // Overage units that can be charged within the spending cap
    private BigDecimal surplusOverageUnits;  // Overage units beyond the spending cap (not charged)
    private BigDecimal overageCost;        // Cost for the overage units
    private BigDecimal totalOverageCost;   // Cumulative overage cost for the period
    private BigDecimal unitsRemaining;     // Remaining quota after the current request/job
    private BigDecimal remainingSpendingCap; // Remaining spending cap
    private BigDecimal totalUnitsUsedThisCycle; // All units used this billing cycle
    private boolean isOverLimit;           // True if usage exceeds the limit
    private boolean isOverage;             // True if usage exceeds the limit and overages are allowed
    private boolean isOverageAllowed;
    
    // Explicit getters for Jackson serialization (Lombok generates isIsOverLimit() which Jackson doesn't recognize)
    @JsonGetter("isOverLimit")
    public boolean isOverLimit() {
        return isOverLimit;
    }
    
    @JsonGetter("isOverage")
    public boolean isOverage() {
        return isOverage;
    }
    
    @JsonGetter("isOverageAllowed")
    public boolean isOverageAllowed() {
        return isOverageAllowed;
    }
} 