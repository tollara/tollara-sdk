using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json;

namespace AgentVend;

public record AgentKeyValidationResult(
    string? UserId,
    string? AgentId,
    string? Plan,
    IReadOnlyList<string> Roles,
    decimal? QuotaRemaining,
    bool SubscriptionActive,
    string? BillingModelType,
    string? MeasurementType,
    string? UnitLabel);

/// <summary>Wire result for Core agent-key usage estimate (signed JSON).</summary>
public record UsageEstimateResult(
    bool SufficientCredits,
    bool WouldExceedCap,
    bool WouldAllow,
    decimal? EstimatedCost,
    decimal? RemainingCredits,
    decimal? RemainingSpendingCap,
    string? BillingModelType,
    string? MeasurementType,
    string? UnitLabel,
    System.Text.Json.JsonElement? Breakdown,
    int EstimateSchemaVersion,
    long Timestamp,
    int HttpStatus);

public static class ValidationClient
{
    /// <summary>Validate using the SDK default API origin and Core path prefix (same as <see cref="AgentVendClient"/>).</summary>
    public static Task<AgentKeyValidationResult?> ValidateAgentKeyAsync(HttpClient http, string agentKey, string? agentId,
        string agentSecret, CancellationToken ct = default) =>
        ValidateAgentKeyAsync(http, DefaultCoreServiceRoot(), agentKey, agentId, agentSecret, ct);

    /// <summary>Validate against an explicit Core service base (include path prefix, e.g. custom or local stack).</summary>
    public static async Task<AgentKeyValidationResult?> ValidateAgentKeyAsync(HttpClient http, string coreServiceUrl,
        string agentKey, string? agentId, string agentSecret, CancellationToken ct = default)
    {
        var url = coreServiceUrl.TrimEnd('/') + "/agent-keys/validate";
        var body = new { agentKey, agentId, agentSecret };
        var bodyStr = JsonSerializer.Serialize(body);
        var req = new HttpRequestMessage(HttpMethod.Post, url) { Content = new StringContent(bodyStr, Encoding.UTF8, "application/json") };
        var res = await http.SendAsync(req, ct);
        if (!res.IsSuccessStatusCode) return null;
        var responseText = await res.Content.ReadAsStringAsync(ct);
        var signature = res.Headers.TryGetValues(AgentVendHeaders.Signature, out var sig) ? string.Join("", sig) : null;
        var timestamp = res.Headers.TryGetValues(AgentVendHeaders.Timestamp, out var ts) ? string.Join("", ts) : null;
        if (string.IsNullOrEmpty(signature) || string.IsNullOrEmpty(timestamp)) return null;
        if (!Hmac.ValidateHmacWithTimestamp(signature, responseText, timestamp, agentSecret)) return null;
        var doc = JsonDocument.Parse(responseText);
        var root = doc.RootElement;
        if (root.TryGetProperty("valid", out var v) && !v.GetBoolean()) return null;
        var roles = root.TryGetProperty("roles", out var r) ? r.EnumerateArray().Select(x => x.GetString() ?? "").ToList() : new List<string>();
        return new AgentKeyValidationResult(
            root.TryGetProperty("userId", out var uid) ? uid.GetString() : null,
            root.TryGetProperty("agentId", out var aid) ? aid.GetString() : agentId,
            root.TryGetProperty("plan", out var p) ? p.GetString() : null,
            roles,
            root.TryGetProperty("quotaRemaining", out var q) && q.ValueKind == JsonValueKind.Number ? q.GetDecimal() : (decimal?)null,
            root.TryGetProperty("subscriptionActive", out var sa) && sa.GetBoolean(),
            root.TryGetProperty("billingModelType", out var bm) && bm.ValueKind == JsonValueKind.String ? bm.GetString() : null,
            root.TryGetProperty("measurementType", out var mt) && mt.ValueKind == JsonValueKind.String ? mt.GetString() : null,
            root.TryGetProperty("unitLabel", out var ul) && ul.ValueKind == JsonValueKind.String ? ul.GetString() : null
        );
    }

    /// <summary>Estimate usage using the SDK default Core root (same as <see cref="AgentVendClient"/>).</summary>
    public static Task<UsageEstimateResult?> EstimateUsageAsync(HttpClient http, string agentKey, decimal estimatedUnits,
        string? agentId, string agentSecret, CancellationToken ct = default) =>
        EstimateUsageAsync(http, DefaultCoreServiceRoot(), agentKey, estimatedUnits, agentId, agentSecret, ct);

    /// <summary>POST estimate-usage to an explicit Core base (including path prefix).</summary>
    public static async Task<UsageEstimateResult?> EstimateUsageAsync(HttpClient http, string coreServiceUrl,
        string agentKey, decimal estimatedUnits, string? agentId, string agentSecret, CancellationToken ct = default)
    {
        if (estimatedUnits <= 0) return null;
        if (string.IsNullOrWhiteSpace(agentKey)) return null;
        var url = coreServiceUrl.TrimEnd('/') + "/agent-keys/estimate-usage";
        var body = new { agentKey, agentId, agentSecret, estimatedUnits };
        var bodyStr = JsonSerializer.Serialize(body);
        var req = new HttpRequestMessage(HttpMethod.Post, url) { Content = new StringContent(bodyStr, Encoding.UTF8, "application/json") };
        var res = await http.SendAsync(req, ct);
        var code = (int)res.StatusCode;
        if (code != (int)HttpStatusCode.OK && code != (int)HttpStatusCode.Forbidden &&
            code != (int)HttpStatusCode.TooManyRequests) return null;
        var responseText = await res.Content.ReadAsStringAsync(ct);
        if (string.IsNullOrWhiteSpace(responseText)) return null;
        var signature = res.Headers.TryGetValues(AgentVendHeaders.Signature, out var sig) ? string.Join("", sig) : null;
        var timestamp = res.Headers.TryGetValues(AgentVendHeaders.Timestamp, out var ts) ? string.Join("", ts) : null;
        if (string.IsNullOrEmpty(signature) || string.IsNullOrEmpty(timestamp)) return null;
        if (!Hmac.ValidateHmacWithTimestamp(signature, responseText, timestamp, agentSecret)) return null;
        using var doc = JsonDocument.Parse(responseText);
        var root = doc.RootElement;
        JsonElement? breakdown = null;
        if (root.TryGetProperty("breakdown", out var br) && br.ValueKind == JsonValueKind.Object)
            breakdown = br.Clone();
        return new UsageEstimateResult(
            root.TryGetProperty("sufficientCredits", out var sc) && sc.GetBoolean(),
            root.TryGetProperty("wouldExceedCap", out var wec) && wec.GetBoolean(),
            root.TryGetProperty("wouldAllow", out var wa) && wa.GetBoolean(),
            ReadDecimal(root, "estimatedCost"),
            ReadDecimal(root, "remainingCredits"),
            ReadDecimal(root, "remainingSpendingCap"),
            ReadString(root, "billingModelType"),
            ReadString(root, "measurementType"),
            ReadString(root, "unitLabel"),
            breakdown,
            root.TryGetProperty("estimateSchemaVersion", out var esv) && esv.TryGetInt32(out var ev) ? ev : 0,
            root.TryGetProperty("timestamp", out var tsv) && tsv.TryGetInt64(out var tl) ? tl : 0L,
            code
        );
    }

    private static decimal? ReadDecimal(JsonElement root, string name)
    {
        if (!root.TryGetProperty(name, out var p)) return null;
        if (p.ValueKind == JsonValueKind.Null) return null;
        return p.ValueKind == JsonValueKind.Number && p.TryGetDecimal(out var d) ? d : null;
    }

    private static string? ReadString(JsonElement root, string name)
    {
        if (!root.TryGetProperty(name, out var p)) return null;
        if (p.ValueKind == JsonValueKind.Null) return null;
        return p.ValueKind == JsonValueKind.String ? p.GetString() : null;
    }

    private static string DefaultCoreServiceRoot() =>
        JoinBaseAndPrefix(AgentVendClient.DefaultApiUrl, AgentVendClient.DefaultCorePathPrefix);

    private static string JoinBaseAndPrefix(string baseUrl, string pathPrefix)
    {
        var b = baseUrl.Trim().TrimEnd('/');
        if (string.IsNullOrEmpty(pathPrefix)) return b;
        var p = pathPrefix.StartsWith('/') ? pathPrefix : "/" + pathPrefix;
        return b + p;
    }
}
