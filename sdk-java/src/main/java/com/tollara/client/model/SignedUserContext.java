package com.tollara.client.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * User fields that participate in the inbound HMAC {@code userContextString}
 * (see docs-sdk/MAIN-SDK-API-SPEC.md §4), including subscription and billing metadata from gateway headers.
 */
@Value
@Builder
public class SignedUserContext {
    String userId;
    String serviceProductId;
    @Builder.Default
    List<String> roles = Collections.emptyList();
    String subscriptionStatus;
    String billingModelType;
    String measurementType;
    String unitLabel;

    /** @deprecated v1/v2 only; use {@link #serviceProductId} for signing version 3. */
    @Deprecated
    String plan;

    /** @deprecated v1 only; omitted from v2/v3 HMAC material. */
    @Deprecated
    BigDecimal quotaRemaining;

    /** @deprecated v1/v2 only; use {@link #subscriptionStatus} for signing version 3. */
    @Deprecated
    @Builder.Default
    boolean subscriptionActive = false;
}
