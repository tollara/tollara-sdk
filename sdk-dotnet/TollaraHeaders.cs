namespace Tollara;

/// <summary>Canonical Tollara HTTP header names.</summary>
public static class TollaraHeaders
{
    public const string Signature = "X-Tollara-Signature";
    public const string Timestamp = "X-Tollara-Timestamp";
    public const string UserId = "X-Tollara-User-ID";
    public const string Plan = "X-Tollara-Plan";
    public const string Roles = "X-Tollara-Roles";
    public const string QuotaRemaining = "X-Tollara-Quota-Remaining";
    public const string SubscriptionActive = "X-Tollara-Subscription-Active";
    public const string BillingModel = "X-Tollara-Billing-Model";
    public const string MeasurementType = "X-Tollara-Measurement-Type";
    public const string UnitLabel = "X-Tollara-Unit-Label";

    /// <summary>Gateway HMAC user-context schema: <c>2</c> = v2 suffix (leading <c>2</c>, no quota segment).</summary>
    public const string SigningVersion = "X-Tollara-Signing-Version";
}
