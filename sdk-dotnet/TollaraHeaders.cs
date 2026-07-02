namespace Tollara;

/// <summary>Canonical Tollara HTTP header names.</summary>
public static class TollaraHeaders
{
    public const string Signature = "X-Tollara-Signature";
    public const string Timestamp = "X-Tollara-Timestamp";
    public const string UserId = "X-Tollara-User-ID";
    public const string ServiceProductId = "X-Tollara-Service-Product-ID";
    public const string Roles = "X-Tollara-Roles";
    public const string SubscriptionStatus = "X-Tollara-Subscription-Status";
    public const string BillingModel = "X-Tollara-Billing-Model";
    public const string MeasurementType = "X-Tollara-Measurement-Type";
    public const string UnitLabel = "X-Tollara-Unit-Label";

    /// <summary>v1/v2 only; use <see cref="ServiceProductId"/> for signing version 3.</summary>
    [Obsolete("v1/v2 only; use ServiceProductId for signing version 3.")]
    public const string Plan = "X-Tollara-Plan";

    /// <summary>v1 only; omitted from v2/v3 HMAC material.</summary>
    [Obsolete("v1 only; omitted from v2/v3 HMAC material.")]
    public const string QuotaRemaining = "X-Tollara-Quota-Remaining";

    /// <summary>v1/v2 only; use <see cref="SubscriptionStatus"/> for signing version 3.</summary>
    [Obsolete("v1/v2 only; use SubscriptionStatus for signing version 3.")]
    public const string SubscriptionActive = "X-Tollara-Subscription-Active";

    /// <summary>Gateway HMAC user-context schema: <c>3</c> = v3; <c>2</c> = v2 (leading <c>2</c>, no quota segment).</summary>
    public const string SigningVersion = "X-Tollara-Signing-Version";
}
