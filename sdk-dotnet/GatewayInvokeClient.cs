using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;

namespace Tollara;

/// <summary>Gateway service invoke (sync/async). See platform spec §1.1–1.2.</summary>
public record GatewayInvokeAsyncEnvelope(string RequestId, string CallbackUrl, string ProgressUrl);

public record GatewayInvokeResult(int StatusCode, string Body, GatewayInvokeAsyncEnvelope? AsyncEnvelope);

public static class GatewayInvokeClient
{
    public static async Task<GatewayInvokeResult?> InvokeAsync(
        HttpClient http,
        string gatewayBaseUrl,
        string gatewayPathPrefix,
        string method,
        string serviceId,
        string endpointId,
        string serviceKey,
        string? body,
        bool async,
        CancellationToken ct = default)
    {
        var suffix = $"/service/{serviceId}/endpoint/{endpointId}/invoke" + (async ? "/async" : "");
        var url = BuildUrl(gatewayBaseUrl, gatewayPathPrefix, suffix);
        var m = new HttpMethod((method ?? "GET").Trim().ToUpperInvariant());
        using var req = new HttpRequestMessage(m, url);
        req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", serviceKey);
        if (!string.IsNullOrEmpty(body) && (m == HttpMethod.Post || m == HttpMethod.Put))
        {
            req.Content = new StringContent(body, Encoding.UTF8, "application/json");
        }

        HttpResponseMessage res;
        try
        {
            res = await http.SendAsync(req, ct);
        }
        catch
        {
            return null;
        }

        var text = await res.Content.ReadAsStringAsync(ct);
        var code = (int)res.StatusCode;
        GatewayInvokeAsyncEnvelope? env = null;
        if (code == (int)HttpStatusCode.Accepted && !string.IsNullOrWhiteSpace(text))
        {
            try
            {
                using var doc = JsonDocument.Parse(text);
                var root = doc.RootElement;
                if (root.TryGetProperty("requestId", out var rid) && rid.ValueKind == JsonValueKind.String)
                {
                    env = new GatewayInvokeAsyncEnvelope(
                        rid.GetString() ?? "",
                        root.TryGetProperty("callbackUrl", out var cb) && cb.ValueKind == JsonValueKind.String
                            ? cb.GetString() ?? ""
                            : "",
                        root.TryGetProperty("progressUrl", out var pu) && pu.ValueKind == JsonValueKind.String
                            ? pu.GetString() ?? ""
                            : "");
                }
            }
            catch (JsonException)
            {
                // ignore
            }
        }

        return new GatewayInvokeResult(code, text, env);
    }

    private static string BuildUrl(string gatewayBaseUrl, string gatewayPathPrefix, string suffix)
    {
        var baseUrl = (gatewayBaseUrl ?? "").TrimEnd('/');
        var prefix = NormalizePrefix(gatewayPathPrefix);
        return baseUrl + prefix + suffix;
    }

    private static string NormalizePrefix(string? gatewayPathPrefix)
    {
        if (string.IsNullOrEmpty(gatewayPathPrefix)) return "";
        var p = gatewayPathPrefix.StartsWith('/') ? gatewayPathPrefix : "/" + gatewayPathPrefix;
        return p.TrimEnd('/');
    }
}
