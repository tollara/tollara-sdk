using System.Globalization;
using System.Linq;
using System.Text.Json;

namespace Tollara;

public record UserContext(
    string? UserId,
    string? ServiceProductId,
    IReadOnlyList<string> Roles,
    string? SubscriptionStatus,
    string? BillingModelType,
    string? MeasurementType,
    string? UnitLabel,
    string? Plan = null,
    decimal? QuotaRemaining = null,
    bool SubscriptionActive = false);

/// <summary>User fields in inbound HMAC userContextString (docs-sdk/MAIN-SDK-API-SPEC.md §4).</summary>
public record SignedUserContext(
    string? UserId,
    string? ServiceProductId,
    IReadOnlyList<string> Roles,
    string? SubscriptionStatus,
    string? BillingModelType = null,
    string? MeasurementType = null,
    string? UnitLabel = null,
    string? Plan = null,
    decimal? QuotaRemaining = null,
    bool SubscriptionActive = false);

public record InboundHmacRequest(
    string Signature,
    string Timestamp,
    object? Payload,
    SignedUserContext SignedUserContext,
    string? SigningVersion = null);

public static class Verifier
{
    public const string SigningVersionV3 = "3";

    private static readonly HashSet<string> InvokeEligibleStatuses = new(StringComparer.Ordinal)
    {
        "ACTIVE", "TRIAL", "CANCELLING", "CANCELLING_PENDING",
    };

    /// <summary>
    /// Returns true when <paramref name="subscriptionStatus"/> is invoke-eligible
    /// (ACTIVE, TRIAL, CANCELLING, CANCELLING_PENDING).
    /// </summary>
    public static bool GrantsAccess(string? subscriptionStatus)
    {
        if (string.IsNullOrWhiteSpace(subscriptionStatus)) return false;
        return InvokeEligibleStatuses.Contains(subscriptionStatus.Trim());
    }

    /// <summary>
    /// HMAC user-context v3: literal <c>3</c> then userId, serviceProductId, roles, subscriptionStatus,
    /// billingModelType, measurementType, unitLabel. Null strings become empty.
    /// </summary>
    public static string BuildGatewayUserContextStringV3(
        string? userId,
        string? serviceProductId,
        IReadOnlyList<string>? roles,
        string? subscriptionStatus,
        string? billingModelType,
        string? measurementType,
        string? unitLabel)
    {
        var r = roles != null && roles.Count > 0 ? string.Join(",", roles) : "";
        return $"3{userId ?? ""}{serviceProductId ?? ""}{r}{subscriptionStatus ?? ""}{billingModelType ?? ""}{measurementType ?? ""}{unitLabel ?? ""}";
    }

    /// <summary>Gateway inbound suffix v1: userId + plan + roles + quota + subscription + billing fields.</summary>
    public static string BuildGatewayUserContextString(
        string? userId,
        string? plan,
        IReadOnlyList<string>? roles,
        decimal? quotaRemaining,
        bool subscriptionActive,
        string? billingModelType,
        string? measurementType,
        string? unitLabel)
    {
        var r = roles != null && roles.Count > 0 ? string.Join(",", roles) : "";
        var q = FormatQuota(quotaRemaining);
        var sub = subscriptionActive ? "true" : "false";
        return $"{userId ?? ""}{plan ?? ""}{r}{q}{sub}{billingModelType ?? ""}{measurementType ?? ""}{unitLabel ?? ""}";
    }

    /// <summary>HMAC user-context v2: literal <c>2</c> then userId, plan, roles, subscription, billing fields (no quota).</summary>
    public static string BuildGatewayUserContextStringV2(
        string? userId,
        string? plan,
        IReadOnlyList<string>? roles,
        bool subscriptionActive,
        string? billingModelType,
        string? measurementType,
        string? unitLabel)
    {
        var r = roles != null && roles.Count > 0 ? string.Join(",", roles) : "";
        var sub = subscriptionActive ? "true" : "false";
        return $"2{userId ?? ""}{plan ?? ""}{r}{sub}{billingModelType ?? ""}{measurementType ?? ""}{unitLabel ?? ""}";
    }

    public static bool VerifyInboundHmac(string agentSecret, InboundHmacRequest request)
    {
        var s = request.SignedUserContext;
        if (request.SigningVersion == SigningVersionV3)
        {
            return VerifySignatureV3(agentSecret, request.Signature, request.Timestamp, request.Payload,
                s.UserId, s.ServiceProductId, s.Roles, s.SubscriptionStatus,
                s.BillingModelType, s.MeasurementType, s.UnitLabel);
        }
        return VerifySignature(agentSecret, request.Signature, request.Timestamp, request.Payload,
            s.UserId, s.Plan, s.Roles, s.QuotaRemaining, s.SubscriptionActive,
            s.BillingModelType, s.MeasurementType, s.UnitLabel, request.SigningVersion);
    }

    /// <summary>Verifies inbound HMAC; returns user context if valid, otherwise null.</summary>
    public static UserContext? VerifyInboundHmacAndGetUserContext(string agentSecret, IReadOnlyDictionary<string, string?> headers, object? payload)
    {
        if (!VerifySignatureFromHeaders(agentSecret, headers, payload)) return null;
        return GetUserContext(headers);
    }

    public static bool VerifySignatureFromHeaders(string agentSecret, IReadOnlyDictionary<string, string?> headers, object? payload)
    {
        var signature = GetHeaderIgnoreCase(headers, TollaraHeaders.Signature);
        var timestamp = GetHeaderIgnoreCase(headers, TollaraHeaders.Timestamp);
        if (string.IsNullOrEmpty(signature) || string.IsNullOrEmpty(timestamp)) return false;

        var rolesHeader = GetHeaderIgnoreCase(headers, TollaraHeaders.Roles);
        var roles = ParseRoles(rolesHeader);
        decimal? quota = ParseQuota(GetHeaderIgnoreCase(headers, TollaraHeaders.QuotaRemaining));
        var subRaw = GetHeaderIgnoreCase(headers, TollaraHeaders.SubscriptionActive);
        var subscriptionActive = subRaw == "true" || subRaw == "1";

        var bm = EmptyToNull(GetHeaderIgnoreCase(headers, TollaraHeaders.BillingModel));
        var mt = EmptyToNull(GetHeaderIgnoreCase(headers, TollaraHeaders.MeasurementType));
        var ul = EmptyToNull(GetHeaderIgnoreCase(headers, TollaraHeaders.UnitLabel));
        var signingVersion = GetHeaderIgnoreCase(headers, TollaraHeaders.SigningVersion);

        var signed = new SignedUserContext(
            GetHeaderIgnoreCase(headers, TollaraHeaders.UserId),
            EmptyToNull(GetHeaderIgnoreCase(headers, TollaraHeaders.ServiceProductId)),
            roles,
            EmptyToNull(GetHeaderIgnoreCase(headers, TollaraHeaders.SubscriptionStatus)),
            bm,
            mt,
            ul,
            GetHeaderIgnoreCase(headers, TollaraHeaders.Plan),
            quota,
            subscriptionActive);

        return VerifyInboundHmac(agentSecret, new InboundHmacRequest(signature, timestamp, payload, signed,
            string.IsNullOrEmpty(signingVersion) ? null : signingVersion));
    }

    public static bool VerifySignatureV3(string agentSecret, string signature, string timestamp,
        object? payload, string? userId, string? serviceProductId, IReadOnlyList<string>? roles,
        string? subscriptionStatus, string? billingModelType = null, string? measurementType = null, string? unitLabel = null)
    {
        if (string.IsNullOrEmpty(signature) || string.IsNullOrEmpty(timestamp) || string.IsNullOrEmpty(agentSecret))
            return false;
        try
        {
            var payloadString = PayloadToString(payload);
            var userContextString = BuildGatewayUserContextStringV3(
                userId, serviceProductId, roles, subscriptionStatus, billingModelType, measurementType, unitLabel);
            var dataToSign = payloadString + timestamp + userContextString;
            var expected = Hmac.CalculateHmac(dataToSign, agentSecret);
            return Hmac.ConstantTimeEquals(expected, signature);
        }
        catch { return false; }
    }

    public static bool VerifySignature(string agentSecret, string signature, string timestamp,
        object? payload, string? userId, string? plan, IReadOnlyList<string>? roles, decimal? quotaRemaining,
        bool subscriptionActive, string? billingModelType = null, string? measurementType = null, string? unitLabel = null,
        string? signingVersion = null)
    {
        if (signingVersion == SigningVersionV3) return false;
        if (string.IsNullOrEmpty(signature) || string.IsNullOrEmpty(timestamp) || string.IsNullOrEmpty(agentSecret))
            return false;
        try
        {
            var payloadString = PayloadToString(payload);
            var userContextString = signingVersion == "2"
                ? BuildGatewayUserContextStringV2(userId, plan, roles, subscriptionActive, billingModelType,
                    measurementType, unitLabel)
                : BuildGatewayUserContextString(
                    userId, plan, roles, quotaRemaining, subscriptionActive, billingModelType, measurementType, unitLabel);
            var dataToSign = payloadString + timestamp + userContextString;
            var expected = Hmac.CalculateHmac(dataToSign, agentSecret);
            return Hmac.ConstantTimeEquals(expected, signature);
        }
        catch { return false; }
    }

    public static UserContext GetUserContext(IReadOnlyDictionary<string, string?> headers)
    {
        var roles = ParseRoles(GetHeaderIgnoreCase(headers, TollaraHeaders.Roles));
        var quotaRemaining = ParseQuota(GetHeaderIgnoreCase(headers, TollaraHeaders.QuotaRemaining));
        var sub = GetHeaderIgnoreCase(headers, TollaraHeaders.SubscriptionActive);
        var subscriptionActive = sub == "true" || sub == "1";
        var bm = EmptyToNull(GetHeaderIgnoreCase(headers, TollaraHeaders.BillingModel));
        var mt = EmptyToNull(GetHeaderIgnoreCase(headers, TollaraHeaders.MeasurementType));
        var ul = EmptyToNull(GetHeaderIgnoreCase(headers, TollaraHeaders.UnitLabel));
        return new UserContext(
            GetHeaderIgnoreCase(headers, TollaraHeaders.UserId),
            EmptyToNull(GetHeaderIgnoreCase(headers, TollaraHeaders.ServiceProductId)),
            roles,
            EmptyToNull(GetHeaderIgnoreCase(headers, TollaraHeaders.SubscriptionStatus)),
            bm,
            mt,
            ul,
            GetHeaderIgnoreCase(headers, TollaraHeaders.Plan),
            quotaRemaining,
            subscriptionActive);
    }

    private static string PayloadToString(object? payload) =>
        payload == null ? "" : payload is string s ? s : JsonSerializer.Serialize(payload);

    private static string? EmptyToNull(string? s) => string.IsNullOrEmpty(s) ? null : s;

    private static IReadOnlyList<string> ParseRoles(string? rolesHeader) =>
        string.IsNullOrEmpty(rolesHeader)
            ? Array.Empty<string>()
            : rolesHeader.Split(',').Select(x => x.Trim()).Where(x => x.Length > 0).ToArray();

    private static decimal? ParseQuota(string? quotaHeader)
    {
        if (string.IsNullOrEmpty(quotaHeader)) return null;
        return decimal.TryParse(quotaHeader, NumberStyles.Any, CultureInfo.InvariantCulture, out var qv) ? qv : null;
    }

    private static string FormatQuota(decimal? quotaRemaining)
    {
        if (quotaRemaining == null) return "";
        var d = quotaRemaining.Value;
        if (d == decimal.Truncate(d)) return decimal.ToInt64(d).ToString(CultureInfo.InvariantCulture);
        return d.ToString(CultureInfo.InvariantCulture);
    }

    private static string? GetHeaderIgnoreCase(IReadOnlyDictionary<string, string?> headers, string canonicalName)
    {
        foreach (var kv in headers)
        {
            if (kv.Key.Equals(canonicalName, StringComparison.OrdinalIgnoreCase))
                return kv.Value;
        }
        return null;
    }
}
