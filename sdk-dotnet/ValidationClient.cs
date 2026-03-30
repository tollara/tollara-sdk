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

public static class ValidationClient
{
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
        if (!Hmac.ValidateHmacSignature(signature, responseText + timestamp, agentSecret)) return null;
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
}
