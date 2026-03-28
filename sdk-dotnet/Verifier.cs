using System.Globalization;
using System.Linq;
using System.Text.Json;

namespace AgentVend;

public record UserContext(
    string? UserId,
    string? Plan,
    IReadOnlyList<string> Roles,
    decimal? QuotaRemaining,
    bool SubscriptionActive
);

/// <summary>User fields in inbound HMAC userContextString (hmac-spec.md).</summary>
public record SignedUserContext(
    string? UserId,
    string? Plan,
    IReadOnlyList<string> Roles,
    decimal? QuotaRemaining
);

public record InboundHmacRequest(
    string Signature,
    string Timestamp,
    object? Payload,
    SignedUserContext SignedUserContext
);

public static class Verifier
{
    public static bool VerifyInboundHmac(string agentSecret, InboundHmacRequest request)
    {
        var s = request.SignedUserContext;
        return VerifySignature(agentSecret, request.Signature, request.Timestamp, request.Payload,
            s.UserId, s.Plan, s.Roles, s.QuotaRemaining);
    }

    public static bool VerifySignatureFromHeaders(string agentSecret, IReadOnlyDictionary<string, string?> headers, object? payload)
    {
        var signature = GetHeaderIgnoreCase(headers, AgentVendHeaders.Signature);
        var timestamp = GetHeaderIgnoreCase(headers, AgentVendHeaders.Timestamp);
        if (string.IsNullOrEmpty(signature) || string.IsNullOrEmpty(timestamp)) return false;
        var rolesHeader = GetHeaderIgnoreCase(headers, AgentVendHeaders.Roles);
        var roles = string.IsNullOrEmpty(rolesHeader)
            ? Array.Empty<string>()
            : rolesHeader.Split(',').Select(x => x.Trim()).Where(x => x.Length > 0).ToList();
        decimal? quota = null;
        var q = GetHeaderIgnoreCase(headers, AgentVendHeaders.QuotaRemaining);
        if (!string.IsNullOrEmpty(q) && decimal.TryParse(q, NumberStyles.Any, CultureInfo.InvariantCulture, out var qv))
            quota = qv;
        var signed = new SignedUserContext(
            GetHeaderIgnoreCase(headers, AgentVendHeaders.UserId),
            GetHeaderIgnoreCase(headers, AgentVendHeaders.Plan),
            roles,
            quota
        );
        return VerifyInboundHmac(agentSecret, new InboundHmacRequest(signature, timestamp, payload, signed));
    }

    public static bool VerifySignature(string agentSecret, string signature, string timestamp,
        object? payload, string? userId, string? plan, IReadOnlyList<string>? roles, decimal? quotaRemaining)
    {
        if (string.IsNullOrEmpty(signature) || string.IsNullOrEmpty(timestamp) || string.IsNullOrEmpty(agentSecret))
            return false;
        try
        {
            var payloadString = payload == null ? "" : payload is string s ? s : JsonSerializer.Serialize(payload);
            var userContextString = (userId ?? "") + (plan ?? "") + (roles != null ? string.Join(",", roles) : "") + FormatQuota(quotaRemaining);
            var dataToSign = payloadString + timestamp + userContextString;
            var expected = Hmac.CalculateHmac(dataToSign, agentSecret);
            return Hmac.ConstantTimeEquals(expected, signature);
        }
        catch { return false; }
    }

    private static string FormatQuota(decimal? quotaRemaining)
    {
        if (quotaRemaining == null) return "";
        var d = quotaRemaining.Value;
        if (d == decimal.Truncate(d)) return decimal.ToInt64(d).ToString(CultureInfo.InvariantCulture);
        return d.ToString(CultureInfo.InvariantCulture);
    }

    public static UserContext GetUserContext(IReadOnlyDictionary<string, string?> headers)
    {
        var rolesHeader = GetHeaderIgnoreCase(headers, AgentVendHeaders.Roles);
        var roles = string.IsNullOrEmpty(rolesHeader)
            ? Array.Empty<string>()
            : rolesHeader.Split(',').Select(x => x.Trim()).Where(x => x.Length > 0).ToList();
        decimal? quotaRemaining = null;
        var q = GetHeaderIgnoreCase(headers, AgentVendHeaders.QuotaRemaining);
        if (!string.IsNullOrEmpty(q) && decimal.TryParse(q, NumberStyles.Any, CultureInfo.InvariantCulture, out var qv))
            quotaRemaining = qv;
        var sub = GetHeaderIgnoreCase(headers, AgentVendHeaders.SubscriptionActive);
        var subscriptionActive = sub == "true" || sub == "1";
        return new UserContext(
            GetHeaderIgnoreCase(headers, AgentVendHeaders.UserId),
            GetHeaderIgnoreCase(headers, AgentVendHeaders.Plan),
            roles,
            quotaRemaining,
            subscriptionActive
        );
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
