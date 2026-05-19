package com.tollara.client.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * User fields that participate in the inbound HMAC {@code userContextString}
 * (see docs/hmac-spec.md), including subscription and billing metadata from gateway headers.
 */
@Value
@Builder
public class SignedUserContext {
    String userId;
    String plan;
    @Builder.Default
    List<String> roles = Collections.emptyList();
    BigDecimal quotaRemaining;
    @Builder.Default
    boolean subscriptionActive = false;
    String billingModelType;
    String measurementType;
    String unitLabel;
}
