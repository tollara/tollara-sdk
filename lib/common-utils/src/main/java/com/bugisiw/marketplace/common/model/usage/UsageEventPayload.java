package com.bugisiw.marketplace.common.model.usage;

import com.bugisiw.marketplace.common.model.billing.Subscription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload for event-driven usage recording. Emitted by the gateway after a successful 2xx
 * agent response; consumed by gateway-usage-consumer to call /usage/record, write usage_logs,
 * queue Stripe, and invalidate caches / update Redis pre-check shadow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageEventPayload {

    private UUID userId;
    /** User ID to use for the Redis pre-check shadow key (matches gateway pre-check key). When set, consumer uses this for shadow; core /usage/record always uses userId. */
    private UUID preCheckKeyUserId;
    private String agentId;
    private UUID userProductId;
    private BigDecimal actualUnitsUsed;
    private String requestId;
    private RequestType requestType;
    private Instant startTime;
    private Instant endTime;
    private String result;
    private String contentType;
    private Integer httpStatusCode;
    private UUID agentEndpointId;
    private Subscription subscription;
    private String agentKey;
    /** External user ID (e.g. Cognito sub) for subscription cache invalidation. */
    private String extUserId;
}
