package com.tollara.client.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Wire + parsed result for Core usage estimate endpoints (see docs-sdk/MAIN-SDK-API-SPEC.md §2.3).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UsageEstimateResult {

    private boolean sufficientCredits;
    private boolean wouldExceedCap;
    private boolean wouldAllow;
    private BigDecimal estimatedCost;
    private String billingModelType;
    private String measurementType;
    private String unitLabel;
    private UsageBreakdown breakdown;
    private int estimateSchemaVersion;
    private long timestamp;

    /** HTTP status of the Core response (200, 403, 429, …). Set by the client after parsing. */
    @JsonIgnore
    private int httpStatus;
}
