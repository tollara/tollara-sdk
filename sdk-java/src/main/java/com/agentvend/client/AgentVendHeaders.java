package com.agentvend.client;

/**
 * Canonical HTTP header names for AgentVend gateway-signed requests and signed responses.
 */
public final class AgentVendHeaders {

    private AgentVendHeaders() {}

    public static final String SIGNATURE = "X-AgentVend-Signature";
    public static final String TIMESTAMP = "X-AgentVend-Timestamp";
    public static final String USER_ID = "X-AgentVend-User-ID";
    public static final String PLAN = "X-AgentVend-Plan";
    public static final String ROLES = "X-AgentVend-Roles";
    public static final String QUOTA_REMAINING = "X-AgentVend-Quota-Remaining";
    public static final String SUBSCRIPTION_ACTIVE = "X-AgentVend-Subscription-Active";
}
