namespace AgentVend;

/// <summary>Canonical AgentVend HTTP header names.</summary>
public static class AgentVendHeaders
{
    public const string Signature = "X-AgentVend-Signature";
    public const string Timestamp = "X-AgentVend-Timestamp";
    public const string UserId = "X-AgentVend-User-ID";
    public const string Plan = "X-AgentVend-Plan";
    public const string Roles = "X-AgentVend-Roles";
    public const string QuotaRemaining = "X-AgentVend-Quota-Remaining";
    public const string SubscriptionActive = "X-AgentVend-Subscription-Active";
    public const string BillingModel = "X-AgentVend-Billing-Model";
    public const string MeasurementType = "X-AgentVend-Measurement-Type";
    public const string UnitLabel = "X-AgentVend-Unit-Label";
}
