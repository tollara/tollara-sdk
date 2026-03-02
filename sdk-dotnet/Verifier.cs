using System.Linq;
using System.Text.Json;

namespace Marketplace.AgentSdk;

public record UserContext(
    string? UserId,
    string? Plan,
    IReadOnlyList<string> Roles,
    decimal? QuotaRemaining,
    bool SubscriptionActive
);

public static class Verifier
{
    public static bool VerifySignature(string agentSecret, string signature, string timestamp,
        object? payload, string? userId, string? plan, IReadOnlyList<string>? roles, decimal? quotaRemaining)
    {
        if (string.IsNullOrEmpty(signature) || string.IsNullOrEmpty(timestamp) || string.IsNullOrEmpty(agentSecret))
            return false;
        try
        {
            var payloadString = payload == null ? "" : payload is string s ? s : JsonSerializer.Serialize(payload);
            var userContextString = (userId ?? "") + (plan ?? "") + (roles != null ? string.Join(",", roles) : "") + (quotaRemaining?.ToString() ?? "");
            var dataToSign = payloadString + timestamp + userContextString;
            var expected = Hmac.CalculateHmac(dataToSign, agentSecret);
            return Hmac.ConstantTimeEquals(expected, signature);
        }
        catch { return false; }
    }

    public static UserContext GetUserContext(IReadOnlyDictionary<string, string?> headers)
    {
        headers.TryGetValue("X-Marketplace-User-ID", out var uid);
        headers.TryGetValue("X-Marketplace-Plan", out var plan);
        headers.TryGetValue("X-Marketplace-Roles", out var rolesHeader);
        var roles = string.IsNullOrEmpty(rolesHeader) ? Array.Empty<string>() : rolesHeader.Split(',').Select(x => x.Trim()).Where(x => x.Length > 0).ToList();
        headers.TryGetValue("X-Marketplace-Quota-Remaining", out var q);
        decimal? quotaRemaining = null;
        if (!string.IsNullOrEmpty(q) && decimal.TryParse(q, out var qv)) quotaRemaining = qv;
        headers.TryGetValue("X-Marketplace-Subscription-Active", out var sub);
        var subscriptionActive = sub == "true" || sub == "1";
        return new UserContext(uid, plan, roles, quotaRemaining, subscriptionActive);
    }
}
