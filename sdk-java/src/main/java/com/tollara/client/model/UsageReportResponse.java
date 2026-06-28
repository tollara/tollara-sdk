package com.tollara.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Response model from the usage service after reporting usage (reportSchemaVersion 2).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UsageReportResponse {
    private int reportSchemaVersion;
    private String status;
    private String warning;
    private String userId;
    private String serviceId;
    private String billingModelType;
    private String measurementType;
    private String unitLabel;
    private UsageBreakdown breakdown;
}
