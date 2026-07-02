package com.tollara.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * Result of a progress or completion callback POST to the usage service.
 */
@Value
@Builder
public class UsageCallbackResult {
    boolean success;
    int httpStatus;
    String httpStatusText;
    String requestUrl;
    String responseBody;
    String networkError;
}
