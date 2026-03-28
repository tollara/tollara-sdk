package com.agentvend.client.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * User fields that participate in the inbound HMAC {@code userContextString}
 * (see docs/hmac-spec.md). Does not include unsigned headers such as subscription active.
 */
@Value
@Builder
public class SignedUserContext {
    String userId;
    String plan;
    @Builder.Default
    List<String> roles = Collections.emptyList();
    BigDecimal quotaRemaining;
}
