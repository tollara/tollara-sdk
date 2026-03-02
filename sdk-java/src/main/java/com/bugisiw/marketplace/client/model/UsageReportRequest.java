package com.bugisiw.marketplace.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request model for reporting usage to the usage service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageReportRequest {
    private String userId;
    private String agentId;
    private BigDecimal unitsUsed;
    private Instant timestamp;
}
