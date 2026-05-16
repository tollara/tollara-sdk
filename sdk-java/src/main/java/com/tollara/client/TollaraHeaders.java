package com.tollara.client;

/**
 * Canonical HTTP header names for Tollara gateway-signed requests and signed responses.
 */
public final class TollaraHeaders {

    private TollaraHeaders() {}

    public static final String SIGNATURE = "X-Tollara-Signature";
    public static final String TIMESTAMP = "X-Tollara-Timestamp";
    public static final String USER_ID = "X-Tollara-User-ID";
    public static final String PLAN = "X-Tollara-Plan";
    public static final String ROLES = "X-Tollara-Roles";
    public static final String QUOTA_REMAINING = "X-Tollara-Quota-Remaining";
    public static final String SUBSCRIPTION_ACTIVE = "X-Tollara-Subscription-Active";
    public static final String BILLING_MODEL = "X-Tollara-Billing-Model";
    public static final String MEASUREMENT_TYPE = "X-Tollara-Measurement-Type";
    public static final String UNIT_LABEL = "X-Tollara-Unit-Label";

    /**
     * Gateway HMAC user-context schema: {@code 2} = v2 suffix (leading {@code "2"}, no quota segment; see docs/sdk-api-spec.md §4).
     */
    public static final String SIGNING_VERSION = "X-Tollara-Signing-Version";
}
