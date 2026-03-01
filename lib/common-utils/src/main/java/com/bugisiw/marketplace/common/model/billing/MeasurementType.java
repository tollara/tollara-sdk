package com.bugisiw.marketplace.common.model.billing;

/**
 * How usage is measured for a subscription (per request, per time unit, per token, or per byte).
 * Used instead of inferring from unitLabel for type-safe branching in gateway, usage-service, and core.
 */
public enum MeasurementType {
    /** Count of requests in a period */
    PER_REQUEST,
    /** Time duration (hours, minutes, etc.) */
    PER_TIME_UNIT,
    /** Token count (request + response, via JTokkit) */
    PER_TOKEN,
    /** Byte count (request + response body size) */
    PER_BYTE
}
