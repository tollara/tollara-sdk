using System.Net.Http;
using System.Text;
using System.Text.Json;

namespace Tollara;

public record UsageCallbackResult(
    bool Success,
    int HttpStatus,
    string HttpStatusText,
    string RequestUrl,
    string? ResponseBody = null,
    string? NetworkError = null);

public record UsageReportResponse(
    int ReportSchemaVersion,
    string? Status,
    string? Warning,
    string? UserId,
    string? ServiceId,
    string? BillingModelType,
    string? MeasurementType,
    string? UnitLabel,
    UsageBreakdown? Breakdown);

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

    public static Task<UsageCallbackResult> ReportProgressAsync(HttpClient http, string progressUrl, string requestId,
        string stage, int percentageComplete, string serviceSecret, CancellationToken ct = default) =>
        ReportProgressAsync(http, progressUrl, requestId, stage, percentageComplete, null, serviceSecret, ct);

    public static Task<UsageCallbackResult> ReportProgressAsync(HttpClient http, string progressUrl, string requestId,
        string stage, int percentageComplete, string? errorMessage, string serviceSecret, CancellationToken ct = default)
    {
        var body = new Dictionary<string, object>
        {
            ["stage"] = stage,
            ["percentageComplete"] = percentageComplete,
            ["timestamp"] = DateTime.UtcNow.ToString("o"),
        };
        if (errorMessage != null) body["errorMessage"] = errorMessage;
        var bodyStr = JsonSerializer.Serialize(body);
        return PostSignedUsageCallbackAsync(http, progressUrl, bodyStr, serviceSecret, ct);
    }

    public static Task<UsageCallbackResult> ReportCompletionAsync(HttpClient http, string callbackUrl, string requestId,
        CompletionStatus status, string serviceSecret, CancellationToken ct = default) =>
        ReportCompletionAsync(http, callbackUrl, requestId, status, null, null, null, 0, serviceSecret, ct);

    public static Task<UsageCallbackResult> ReportCompletionAsync(HttpClient http, string callbackUrl, string requestId,
        CompletionStatus status, string? result, decimal units, string serviceSecret, CancellationToken ct = default) =>
        ReportCompletionAsync(http, callbackUrl, requestId, status, result, null, null, units, serviceSecret, ct);

    public static Task<UsageCallbackResult> ReportCompletionAsync(HttpClient http, string callbackUrl, string requestId,
        CompletionStatus status, string? result, string? resultUrl, string? contentType, decimal units, string serviceSecret, CancellationToken ct = default)
    {
        var body = new Dictionary<string, object>
        {
            ["status"] = status.ToApiString(),
            ["timestamp"] = DateTime.UtcNow.ToString("o"),
            ["units"] = units,
        };
        if (result != null) body["result"] = result;
        if (resultUrl != null) body["resultUrl"] = resultUrl;
        if (contentType != null) body["contentType"] = contentType;
        var bodyStr = JsonSerializer.Serialize(body);
        return PostSignedUsageCallbackAsync(http, callbackUrl, bodyStr, serviceSecret, ct);
    }

    private static async Task<UsageCallbackResult> PostSignedUsageCallbackAsync(
        HttpClient http, string urlWithQuery, string bodyStr, string serviceSecret, CancellationToken ct)
    {
        var (baseUrl, timestamp) = ParseUrlParams(urlWithQuery);
        if (timestamp == null)
        {
            var statusText = string.IsNullOrEmpty(urlWithQuery)
                ? "Missing or invalid callback/progress URL"
                : "Missing timestamp query parameter in URL";
            return new UsageCallbackResult(false, 0, statusText, baseUrl);
        }

        try
        {
            var signature = Hmac.CalculateHmacWithTimestamp(bodyStr, timestamp, serviceSecret);
            var req = new HttpRequestMessage(HttpMethod.Post, baseUrl);
            req.Headers.TryAddWithoutValidation(TollaraHeaders.Signature, signature);
            req.Headers.TryAddWithoutValidation(TollaraHeaders.Timestamp, timestamp);
            req.Content = new StringContent(bodyStr, Encoding.UTF8, "application/json");
            var res = await http.SendAsync(req, ct);
            var responseBody = await res.Content.ReadAsStringAsync(ct);
            var statusText = res.IsSuccessStatusCode ? res.ReasonPhrase ?? "OK" : res.ReasonPhrase ?? $"HTTP {(int)res.StatusCode}";
            return new UsageCallbackResult(
                res.IsSuccessStatusCode,
                (int)res.StatusCode,
                statusText,
                baseUrl,
                string.IsNullOrEmpty(responseBody) ? null : responseBody);
        }
        catch (Exception ex)
        {
            return new UsageCallbackResult(false, 0, "Network error", baseUrl, NetworkError: ex.Message);
        }
    }

    /// <summary>Report usage using the SDK default API origin and usage path prefix (same as <see cref="TollaraClient"/>).</summary>
    public static Task<UsageReportResponse> ReportUsageAsync(HttpClient http, string userId, string serviceId, decimal unitsUsed,
        string serviceSecret, CancellationToken ct = default) =>
        ReportUsageAsync(http, TollaraClient.DefaultApiUrl, userId, serviceId, unitsUsed, serviceSecret, null, null, ct);

    /// <summary>Report usage against an explicit usage service origin (for custom or local stacks).</summary>
    public static Task<UsageReportResponse> ReportUsageAsync(HttpClient http, string usageServiceUrl,
        string userId, string serviceId, decimal unitsUsed, string serviceSecret, CancellationToken ct = default) =>
        ReportUsageAsync(http, usageServiceUrl, userId, serviceId, unitsUsed, serviceSecret, null, null, ct);

    public static Task<UsageReportResponse> ReportUsageAsync(HttpClient http, string usageServiceUrl,
        string userId, string serviceId, decimal unitsUsed, string serviceSecret, DateTime? timestamp, CancellationToken ct = default) =>
        ReportUsageAsync(http, usageServiceUrl, userId, serviceId, unitsUsed, serviceSecret, timestamp, null, ct);

    public static async Task<UsageReportResponse> ReportUsageAsync(HttpClient http, string usageServiceUrl,
        string userId, string serviceId, decimal unitsUsed, string serviceSecret, DateTime? timestamp, string? usagePathPrefix, CancellationToken ct = default)
    {
        var ts = (timestamp ?? DateTime.UtcNow).ToUniversalTime();
        var epochSec = new DateTimeOffset(ts).ToUnixTimeSeconds();
        var iso = ts.ToString("o");
        var bodyStr = JsonSerializer.Serialize(new { userId, serviceId, unitsUsed, timestamp = iso });
        var tsStr = epochSec.ToString();
        var signature = Hmac.CalculateHmacWithTimestamp(bodyStr, tsStr, serviceSecret);
        var reportUrl = BuildUsageReportUrl(usageServiceUrl, usagePathPrefix);
        var req = new HttpRequestMessage(HttpMethod.Post, reportUrl);
        req.Headers.TryAddWithoutValidation(TollaraHeaders.Signature, signature);
        req.Headers.TryAddWithoutValidation(TollaraHeaders.Timestamp, tsStr);
        req.Content = new StringContent(bodyStr, Encoding.UTF8, "application/json");
        var res = await http.SendAsync(req, ct);
        res.EnsureSuccessStatusCode();
        var json = await res.Content.ReadAsStringAsync(ct);
        return ParseUsageReportResponse(json);
    }

    internal static UsageReportResponse ParseUsageReportResponse(string json)
    {
        var doc = JsonDocument.Parse(json);
        var root = doc.RootElement;
        UsageBreakdown? breakdown = null;
        if (root.TryGetProperty("breakdown", out var br) && br.ValueKind == JsonValueKind.Object)
        {
            breakdown = JsonSerializer.Deserialize<UsageBreakdown>(br.GetRawText(), new JsonSerializerOptions
            {
                PropertyNameCaseInsensitive = true,
            });
        }
        return new UsageReportResponse(
            root.TryGetProperty("reportSchemaVersion", out var rsv) && rsv.TryGetInt32(out var rv) ? rv : 0,
            root.TryGetProperty("status", out var s) ? s.GetString() : null,
            root.TryGetProperty("warning", out var w) && w.ValueKind == JsonValueKind.String ? w.GetString() : null,
            ReadString(root, "userId"),
            ReadString(root, "serviceId"),
            ReadString(root, "billingModelType"),
            ReadString(root, "measurementType"),
            ReadString(root, "unitLabel"),
            breakdown
        );
    }

    private static string? ReadString(JsonElement root, string name)
    {
        if (!root.TryGetProperty(name, out var p)) return null;
        if (p.ValueKind == JsonValueKind.Null) return null;
        return p.ValueKind == JsonValueKind.String ? p.GetString() : null;
    }

    private static (string baseUrl, string? timestamp) ParseUrlParams(string? url)
    {
        if (url is null)
            return ("", null);
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
