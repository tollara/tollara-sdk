package com.bugisiw.marketplace.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Response model from the usage service after reporting usage.
 * Contains status information and rate limit details.
 */
@Data
public class UsageReportResponse {
    private String status;
    private String warning;
    @JsonProperty("isOverLimit")
    private boolean overLimit;
    private long remainingRequestsPerPeriod;
    private BigDecimal remainingTimeUnitsPerPeriod;
    private BigDecimal remainingSpendingCap;
    private BigDecimal overageRate;
}

