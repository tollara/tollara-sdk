using System.Net.Http;

namespace AgentVend;

/// <summary>Configuration for <see cref="AgentVendClient"/>. Explicit properties override environment variables.</summary>
public sealed class AgentVendClientOptions
{
    public string? ApiUrl { get; init; }
    public string? CoreApiUrl { get; init; }
    public string? GatewayApiUrl { get; init; }
    public string? UsageApiUrl { get; init; }
    public string? CorePathPrefix { get; init; }
    public string? GatewayPathPrefix { get; init; }
    public string? UsagePathPrefix { get; init; }
    public string? AgentId { get; init; }
    public string? AgentSecret { get; init; }
    public HttpClient? HttpClient { get; init; }
}

/// <summary>
/// Unified client for Core validate, Usage report/progress/complete, and Gateway polling (Java <c>AgentVendClient</c> parity).
/// </summary>
public sealed class AgentVendClient
{
    public const string EnvApiUrl = "AGENTVEND_API_URL";
    public const string EnvAgentId = "AGENTVEND_AGENT_ID";
    public const string EnvAgentSecret = "AGENTVEND_AGENT_SECRET";

    public const string DefaultCorePathPrefix = "/api/v1";
    public const string DefaultGatewayPathPrefix = "/api";
    public const string DefaultUsagePathPrefix = UsageClient.DefaultUsagePathPrefix;

    private readonly HttpClient _http;
    private readonly string _gatewayBaseUrl;
    private readonly string _gatewayPathPrefix;
    private readonly string _coreRoot;
    private readonly string _usageBase;
    private readonly string? _usagePathPrefix;
    private readonly string? _agentId;
    private readonly string _agentSecret;

    private AgentVendClient(
        HttpClient http,
        string gatewayBaseUrl,
        string gatewayPathPrefix,
        string coreRoot,
        string usageBase,
        string? usagePathPrefix,
        string? agentId,
        string agentSecret)
    {
        _http = http;
        _gatewayBaseUrl = gatewayBaseUrl;
        _gatewayPathPrefix = gatewayPathPrefix;
        _coreRoot = coreRoot;
        _usageBase = usageBase;
        _usagePathPrefix = usagePathPrefix;
        _agentId = agentId;
        _agentSecret = agentSecret;
    }

    /// <summary>Build from options and/or <c>AGENTVEND_*</c> environment variables.</summary>
    public static AgentVendClient Create(AgentVendClientOptions? options = null)
    {
        options ??= new AgentVendClientOptions();

        var resolved = TrimTrailingSlashes(FirstNonBlank(options.ApiUrl, Environment.GetEnvironmentVariable(EnvApiUrl)));
        if (string.IsNullOrEmpty(resolved))
            throw new InvalidOperationException(
                $"AgentVend API URL is required: set {nameof(AgentVendClientOptions.ApiUrl)} or environment variable {EnvApiUrl}");

        var coreBase = TrimTrailingSlashes(FirstNonBlank(options.CoreApiUrl, resolved));
        var gwBase = TrimTrailingSlashes(FirstNonBlank(options.GatewayApiUrl, resolved));
        var usageBase = TrimTrailingSlashes(FirstNonBlank(options.UsageApiUrl, resolved));

        var corePrefix = options.CorePathPrefix ?? DefaultCorePathPrefix;
        var gwPrefix = options.GatewayPathPrefix ?? DefaultGatewayPathPrefix;
        var usagePrefix = options.UsagePathPrefix;

        var secret = FirstNonBlank(options.AgentSecret, Environment.GetEnvironmentVariable(EnvAgentSecret));
        if (string.IsNullOrEmpty(secret))
            throw new InvalidOperationException(
                $"Agent secret is required: set {nameof(AgentVendClientOptions.AgentSecret)} or environment variable {EnvAgentSecret}");

        var agentIdRaw = FirstNonBlank(options.AgentId, Environment.GetEnvironmentVariable(EnvAgentId));
        var agentId = string.IsNullOrEmpty(agentIdRaw) ? null : agentIdRaw;

        var http = options.HttpClient ?? new HttpClient();

        var coreRoot = JoinUrl(coreBase, corePrefix);

        return new AgentVendClient(http, gwBase, gwPrefix, coreRoot, usageBase, usagePrefix, agentId, secret);
    }

    public Task<AgentKeyValidationResult?> ValidateAgentKeyAsync(string agentKey, CancellationToken ct = default) =>
        ValidationClient.ValidateAgentKeyAsync(_http, _coreRoot, agentKey, _agentId, _agentSecret, ct);

    public Task<UsageReportResponse> ReportUsageAsync(string userId, string agentId, decimal unitsUsed, CancellationToken ct = default) =>
        UsageClient.ReportUsageAsync(_http, _usageBase, userId, agentId, unitsUsed, _agentSecret, null, _usagePathPrefix, ct);

    public Task<UsageReportResponse> ReportUsageAsync(string userId, string agentId, decimal unitsUsed, DateTime? timestamp, CancellationToken ct = default) =>
        UsageClient.ReportUsageAsync(_http, _usageBase, userId, agentId, unitsUsed, _agentSecret, timestamp, _usagePathPrefix, ct);

    public Task<bool> SendProgressUpdateAsync(string progressUrl, string requestId, string stage, int percentageComplete, CancellationToken ct = default) =>
        UsageClient.ReportProgressAsync(_http, progressUrl, requestId, stage, percentageComplete, _agentSecret, ct);

    public Task<bool> SendProgressUpdateAsync(string progressUrl, string requestId, string stage, int percentageComplete, string? errorMessage, CancellationToken ct = default) =>
        UsageClient.ReportProgressAsync(_http, progressUrl, requestId, stage, percentageComplete, errorMessage, _agentSecret, ct);

    public Task<bool> SendCompletionAsync(string callbackUrl, string requestId, CompletionStatus status, decimal units, CancellationToken ct = default) =>
        UsageClient.ReportCompletionAsync(_http, callbackUrl, requestId, status, null, null, null, units, _agentSecret, ct);

    public Task<bool> SendCompletionAsync(string callbackUrl, string requestId, CompletionStatus status, string? result, decimal units, CancellationToken ct = default) =>
        UsageClient.ReportCompletionAsync(_http, callbackUrl, requestId, status, result, null, null, units, _agentSecret, ct);

    public Task<bool> SendCompletionAsync(string callbackUrl, string requestId, CompletionStatus status, string? result, string? resultUrl, string? contentType, decimal units, CancellationToken ct = default) =>
        UsageClient.ReportCompletionAsync(_http, callbackUrl, requestId, status, result, resultUrl, contentType, units, _agentSecret, ct);

    public Task<(bool Ok, int StatusCode, string Body)> GetRequestStatusAsync(string requestId, string agentKey, CancellationToken ct = default) =>
        GatewayClient.GetRequestStatusAsync(_http, _gatewayBaseUrl, _gatewayPathPrefix, requestId, agentKey, ct);

    public Task<(bool Ok, int StatusCode, string Body)> GetRequestResultAsync(string requestId, string agentKey, CancellationToken ct = default) =>
        GatewayClient.GetRequestResultAsync(_http, _gatewayBaseUrl, _gatewayPathPrefix, requestId, agentKey, ct);

    private static string FirstNonBlank(string? a, string? b)
    {
        if (!string.IsNullOrWhiteSpace(a)) return a.Trim();
        if (!string.IsNullOrWhiteSpace(b)) return b!.Trim();
        return "";
    }

    private static string TrimTrailingSlashes(string s)
    {
        var t = s.Trim();
        while (t.EndsWith('/')) t = t[..^1];
        return t;
    }

    private static string JoinUrl(string baseUrl, string path)
    {
        var b = TrimTrailingSlashes(baseUrl);
        if (string.IsNullOrEmpty(path)) return b;
        var p = path.StartsWith('/') ? path : "/" + path;
        return b + p;
    }
}
