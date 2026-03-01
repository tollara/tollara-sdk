package com.bugisiw.marketplace.common.tracing;

/**
 * Shared constants for trace ID propagation across gateway and core services.
 * Used for HTTP header name and MDC key so logs can be correlated across the invoke chain.
 */
public final class TraceIdConstants {

    /** HTTP header name for trace ID (gateway sends to core; client may send to gateway). */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** MDC key for trace ID (both gateway and core set this for log pattern). */
    public static final String MDC_TRACE_ID = "traceId";

    private TraceIdConstants() {
    }
}
