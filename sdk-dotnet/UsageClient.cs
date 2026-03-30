using System.Net.Http;
using System.Text;
using System.Text.Json;

namespace AgentVend;

public record UsageReportResponse(string? Status, bool IsOverLimit, long RemainingRequestsPerPeriod);

public static class UsageClient
{
    public const string DefaultUsagePathPrefix = "/api/usage";

    public static string BuildUsageReportUrl(string usageServiceUrl, string? usagePathPrefix)
    {
        var baseUrl = usageServiceUrl.TrimEnd('/');
        var p = string.IsNullOrWhiteSpace(usagePathPrefix) ? DefaultUsagePathPrefix : usagePathPrefix.Trim();
        if (!p.StartsWith('/')) p = "/" + p;
        p = p.TrimEnd('/');
        return $"{baseUrl}{p}/report";
    }

    public static Task<bool> ReportProgressAsync(HttpClient http, string progressUrl, string requestId,
        string stage, int percentageComplete, string agentSecret, CancellationToken ct = default) =>
        ReportProgressAsync(http, progressUrl, requestId, stage, percentageComplete, null, agentSecret, ct);

    public static async Task<bool> ReportProgressAsync(HttpClient http, string progressUrl, string requestId,
        string stage, int percentageComplete, string? errorMessage, string agentSecret, CancellationToken ct = default)
    {
        var (baseUrl, timestamp) = ParseUrlParams(progressUrl);
        if (timestamp == null) return false;
        var body = new Dictionary<string, object> { ["stage"] = stage, ["percentageComplete"] = percentageComplete, ["timestamp"] = DateTime.UtcNow.ToString("o") };
        if (errorMessage != null) body["errorMessage"] = errorMessage;
        var bodyStr = JsonSerializer.Serialize(body);
        var signature = Hmac.CalculateHmacWithTimestamp(bodyStr, timestamp, agentSecret);
        var req = new HttpRequestMessage(HttpMethod.Post, baseUrl);
        req.Headers.TryAddWithoutValidation(AgentVendHeaders.Signature, signature);
        req.Headers.TryAddWithoutValidation(AgentVendHeaders.Timestamp, timestamp);
        req.Content = new StringContent(bodyStr, Encoding.UTF8, "application/json");
        var res = await http.SendAsync(req, ct);
        return res.IsSuccessStatusCode;
    }

    public static Task<bool> ReportCompletionAsync(HttpClient http, string callbackUrl, string requestId,
        CompletionStatus status, string agentSecret, CancellationToken ct = default) =>
        ReportCompletionAsync(http, callbackUrl, requestId, status, null, null, null, 0, agentSecret, ct);

    public static Task<bool> ReportCompletionAsync(HttpClient http, string callbackUrl, string requestId,
        CompletionStatus status, string result, decimal units, string agentSecret, CancellationToken ct = default) =>
        ReportCompletionAsync(http, callbackUrl, requestId, status, result, null, null, units, agentSecret, ct);

    public static async Task<bool> ReportCompletionAsync(HttpClient http, string callbackUrl, string requestId,
        CompletionStatus status, string? result, string? resultUrl, string? contentType, decimal units, string agentSecret, CancellationToken ct = default)
    {
        var (baseUrl, timestamp) = ParseUrlParams(callbackUrl);
        if (timestamp == null) return false;
        var body = new Dictionary<string, object> { ["status"] = status.ToApiString(), ["timestamp"] = DateTime.UtcNow.ToString("o"), ["units"] = units };
        if (result != null) body["result"] = result;
        if (resultUrl != null) body["resultUrl"] = resultUrl;
        if (contentType != null) body["contentType"] = contentType;
        var bodyStr = JsonSerializer.Serialize(body);
        var signature = Hmac.CalculateHmacWithTimestamp(bodyStr, timestamp, agentSecret);
        var req = new HttpRequestMessage(HttpMethod.Post, baseUrl);
        req.Headers.TryAddWithoutValidation(AgentVendHeaders.Signature, signature);
        req.Headers.TryAddWithoutValidation(AgentVendHeaders.Timestamp, timestamp);
        req.Content = new StringContent(bodyStr, Encoding.UTF8, "application/json");
        var res = await http.SendAsync(req, ct);
        return res.IsSuccessStatusCode;
    }

    public static Task<UsageReportResponse> ReportUsageAsync(HttpClient http, string usageServiceUrl,
        string userId, string agentId, decimal unitsUsed, string agentSecret, CancellationToken ct = default) =>
        ReportUsageAsync(http, usageServiceUrl, userId, agentId, unitsUsed, agentSecret, null, null, ct);

    public static Task<UsageReportResponse> ReportUsageAsync(HttpClient http, string usageServiceUrl,
        string userId, string agentId, decimal unitsUsed, string agentSecret, DateTime? timestamp, CancellationToken ct = default) =>
        ReportUsageAsync(http, usageServiceUrl, userId, agentId, unitsUsed, agentSecret, timestamp, null, ct);

    public static async Task<UsageReportResponse> ReportUsageAsync(HttpClient http, string usageServiceUrl,
        string userId, string agentId, decimal unitsUsed, string agentSecret, DateTime? timestamp, string? usagePathPrefix, CancellationToken ct = default)
    {
        var ts = timestamp ?? DateTime.UtcNow;
        var tsMs = (long)(ts - DateTime.UnixEpoch).TotalMilliseconds;
        var body = new { userId, agentId, unitsUsed, timestamp = tsMs };
        var bodyStr = JsonSerializer.Serialize(body);
        var tsStr = tsMs.ToString();
        var signature = Hmac.CalculateHmacWithTimestamp(bodyStr, tsStr, agentSecret);
        var reportUrl = BuildUsageReportUrl(usageServiceUrl, usagePathPrefix);
        var req = new HttpRequestMessage(HttpMethod.Post, reportUrl);
        req.Headers.TryAddWithoutValidation(AgentVendHeaders.Signature, signature);
        req.Headers.TryAddWithoutValidation(AgentVendHeaders.Timestamp, tsStr);
        req.Content = new StringContent(bodyStr, Encoding.UTF8, "application/json");
        var res = await http.SendAsync(req, ct);
        res.EnsureSuccessStatusCode();
        var json = await res.Content.ReadAsStringAsync(ct);
        var doc = JsonDocument.Parse(json);
        var root = doc.RootElement;
        return new UsageReportResponse(
            root.TryGetProperty("status", out var s) ? s.GetString() : null,
            root.TryGetProperty("isOverLimit", out var o) && o.GetBoolean(),
            root.TryGetProperty("remainingRequestsPerPeriod", out var r) ? r.GetInt64() : 0
        );
    }

    private static (string baseUrl, string? timestamp) ParseUrlParams(string url)
    {
        var idx = url.IndexOf('?');
        if (idx < 0) return (url, null);
        var baseUrl = url[..idx];
        var query = url[(idx + 1)..];
        string? timestamp = null;
        foreach (var part in query.Split('&'))
        {
            var eq = part.IndexOf('=');
            if (eq <= 0) continue;
            var key = Uri.UnescapeDataString(part[..eq]);
            if (key == "timestamp") timestamp = part[(eq + 1)..];
        }
        return (baseUrl, timestamp);
    }
}
