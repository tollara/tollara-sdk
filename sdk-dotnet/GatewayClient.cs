using System.Net.Http;
using System.Net.Http.Headers;

namespace AgentVend;

/// <summary>Caller-side gateway polling (sdk-api-spec §1.3–1.4).</summary>
public static class GatewayClient
{
    /// <summary>Poll status using the SDK default API origin and gateway path prefix (same as <see cref="AgentVendClient"/>).</summary>
    public static Task<(bool Ok, int StatusCode, string Body)> GetRequestStatusAsync(HttpClient http, string requestId,
        string agentKey, CancellationToken ct = default) =>
        GetRequestStatusAsync(http, AgentVendClient.DefaultApiUrl, AgentVendClient.DefaultGatewayPathPrefix, requestId,
            agentKey, ct);

    /// <summary>Poll status against explicit gateway base and path prefix (for custom or local stacks).</summary>
    public static async Task<(bool Ok, int StatusCode, string Body)> GetRequestStatusAsync(
        HttpClient http,
        string gatewayBaseUrl,
        string gatewayPathPrefix,
        string requestId,
        string agentKey,
        CancellationToken ct = default)
    {
        var url = BuildUrl(gatewayBaseUrl, gatewayPathPrefix, $"/requests/{requestId}/status");
        return await GetAsync(http, url, agentKey, ct);
    }

    /// <summary>Fetch result using the SDK default API origin and gateway path prefix (same as <see cref="AgentVendClient"/>).</summary>
    public static Task<(bool Ok, int StatusCode, string Body)> GetRequestResultAsync(HttpClient http, string requestId,
        string agentKey, CancellationToken ct = default) =>
        GetRequestResultAsync(http, AgentVendClient.DefaultApiUrl, AgentVendClient.DefaultGatewayPathPrefix, requestId,
            agentKey, ct);

    /// <summary>Fetch result against explicit gateway base and path prefix (for custom or local stacks).</summary>
    public static async Task<(bool Ok, int StatusCode, string Body)> GetRequestResultAsync(
        HttpClient http,
        string gatewayBaseUrl,
        string gatewayPathPrefix,
        string requestId,
        string agentKey,
        CancellationToken ct = default)
    {
        var url = BuildUrl(gatewayBaseUrl, gatewayPathPrefix, $"/requests/{requestId}/result");
        return await GetAsync(http, url, agentKey, ct);
    }

    private static async Task<(bool Ok, int StatusCode, string Body)> GetAsync(
        HttpClient http, string url, string agentKey, CancellationToken ct)
    {
        using var req = new HttpRequestMessage(HttpMethod.Get, url);
        req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", agentKey);
        var res = await http.SendAsync(req, ct);
        var body = await res.Content.ReadAsStringAsync(ct);
        return (res.IsSuccessStatusCode, (int)res.StatusCode, body);
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
