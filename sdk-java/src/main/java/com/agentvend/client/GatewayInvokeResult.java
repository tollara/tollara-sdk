package com.agentvend.client;

import lombok.Builder;
import lombok.Value;

/**
 * Result of gateway service invoke (sync or async). Async success (HTTP 202) may populate {@link #asyncEnvelope()}.
 */
@Value
@Builder
public class GatewayInvokeResult {
    int statusCode;
    String body;
    /** Present when HTTP 202 and JSON body contains async fields (camelCase). */
    AsyncInvokeEnvelope asyncEnvelope;

    @Value
    @Builder
    public static class AsyncInvokeEnvelope {
        String requestId;
        String callbackUrl;
        String progressUrl;
    }
}
