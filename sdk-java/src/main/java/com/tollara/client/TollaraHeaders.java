package com.tollara.client;

/**
 * Canonical HTTP header names for Tollara gateway-signed requests and signed responses.
 */
public final class TollaraHeaders {

    private TollaraHeaders() {}

    public static final String SIGNATURE = "X-Tollara-Signature";
    public static final String TIMESTAMP = "X-Tollara-Timestamp";
    public static final String USER_ID = "X-Tollara-User-ID";
    public static final String SERVICE_PRODUCT_ID = "X-Tollara-Service-Product-ID";
    public static final String ROLES = "X-Tollara-Roles";
    public static final String SUBSCRIPTION_STATUS = "X-Tollara-Subscription-Status";
    public static final String BILLING_MODEL = "X-Tollara-Billing-Model";
    public static final String MEASUREMENT_TYPE = "X-Tollara-Measurement-Type";
    public static final String UNIT_LABEL = "X-Tollara-Unit-Label";

    /** @deprecated v1/v2 only; use {@link #SERVICE_PRODUCT_ID} for signing version 3. */
    @Deprecated
    public static final String PLAN = "X-Tollara-Plan";

    /** @deprecated v1 only; omitted from v2/v3 HMAC material. */
    @Deprecated
    public static final String QUOTA_REMAINING = "X-Tollara-Quota-Remaining";

    /** @deprecated v1/v2 only; use {@link #SUBSCRIPTION_STATUS} for signing version 3. */
    @Deprecated
    public static final String SUBSCRIPTION_ACTIVE = "X-Tollara-Subscription-Active";

    /**
     * Gateway HMAC user-context schema: {@code 3} = v3 ({@code serviceProductId}, {@code subscriptionStatus});
     * {@code 2} = v2 (leading {@code "2"}, no quota segment).
     */
    public static final String SIGNING_VERSION = "X-Tollara-Signing-Version";
}
