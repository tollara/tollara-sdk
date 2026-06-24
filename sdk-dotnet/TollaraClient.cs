using System.Net.Http;

namespace Tollara;

/// <summary>Configuration for <see cref="TollaraClient"/>. Explicit properties override environment variables.</summary>
public sealed class TollaraClientOptions
{
    public string? ApiUrl { get; init; }
    public string? CoreApiUrl { get; init; }
    public string? GatewayApiUrl { get; init; }
    public string? UsageApiUrl { get; init; }
    public string? CorePathPrefix { get; init; }
    public string? GatewayPathPrefix { get; init; }
    public string? UsagePathPrefix { get; init; }
    public string? ServiceId { get; init; }
    public string? ServiceSecret { get; init; }
    public HttpClient? HttpClient { get; init; }
}

/// <summary>
/// Tollara client for Core validate and estimates, Usage report/progress/complete, Gateway invoke and polling.
/// The API origin defaults to <see cref="DefaultApiUrl"/> when neither <see cref="TollaraClientOptions.ApiUrl"/> nor <c>TOLLARA_API_URL</c> is set.
/// </summary>
public sealed class TollaraClient
{
    /// <summary>Production API origin used when no URL is configured.</summary>
    public const string DefaultApiUrl = "https://api.tollara.ai";

    public const string EnvApiUrl = "TOLLARA_API_URL";
    /// <summary>Preferred environment variable for the service UUID (optional).</summary>
    public const string EnvServiceId = "TOLLARA_SERVICE_ID";
    /// <summary>Preferred environment variable for the service shared secret (required).</summary>
    public const string EnvServiceSecret = "TOLLARA_SERVICE_SECRET";
    public const string DefaultCorePathPrefix = "/api/v1";
    public const string DefaultGatewayPathPrefix = "/api";
    public const string DefaultUsagePathPrefix = UsageClient.DefaultUsagePathPrefix;

    private readonly HttpClient _http;
    private readonly string _gatewayBaseUrl;
    private readonly string _gatewayPathPrefix;
    private readonly string _coreRoot;
    private readonly string _usageBase;
    private readonly string? _usagePathPrefix;
    private readonly string? _serviceId;
    private readonly string _serviceSecret;

    private TollaraClient(
        HttpClient http,
        string gatewayBaseUrl,
        string gatewayPathPrefix,
        string coreRoot,
        string usageBase,
        string? usagePathPrefix,
        string? serviceId,
        string serviceSecret)
    {
        _http = http;
        _gatewayBaseUrl = gatewayBaseUrl;
        _gatewayPathPrefix = gatewayPathPrefix;
        _coreRoot = coreRoot;
        _usageBase = usageBase;
        _usagePathPrefix = usagePathPrefix;
        _serviceId = serviceId;
        _serviceSecret = serviceSecret;
    }

    /// <summary>Build from options and/or <c>TOLLARA_*</c> environment variables.</summary>
    public static TollaraClient Create(TollaraClientOptions? options = null)
    {
        options ??= new TollaraClientOptions();

        var resolved = TrimTrailingSlashes(FirstNonBlank(options.ApiUrl, Environment.GetEnvironmentVariable(EnvApiUrl)));
        if (string.IsNullOrEmpty(resolved))
            resolved = DefaultApiUrl;

        var coreBase = TrimTrailingSlashes(FirstNonBlank(options.CoreApiUrl, resolved));
        var gwBase = TrimTrailingSlashes(FirstNonBlank(options.GatewayApiUrl, resolved));
        var usageBase = TrimTrailingSlashes(FirstNonBlank(options.UsageApiUrl, resolved));

        var corePrefix = options.CorePathPrefix ?? DefaultCorePathPrefix;
        var gwPrefix = options.GatewayPathPrefix ?? DefaultGatewayPathPrefix;
        var usagePrefix = options.UsagePathPrefix;

        var secret = FirstNonBlank(options.ServiceSecret, Environment.GetEnvironmentVariable(EnvServiceSecret));
        if (string.IsNullOrEmpty(secret))
            throw new InvalidOperationException(
                $"Service secret is required: set {nameof(TollaraClientOptions.ServiceSecret)} or environment variable {EnvServiceSecret}");

        var serviceIdRaw = FirstNonBlank(options.ServiceId, Environment.GetEnvironmentVariable(EnvServiceId));
        var serviceId = string.IsNullOrEmpty(serviceIdRaw) ? null : serviceIdRaw;

        var http = options.HttpClient ?? new HttpClient();

        var coreRoot = JoinUrl(coreBase, corePrefix);

        return new TollaraClient(http, gwBase, gwPrefix, coreRoot, usageBase, usagePrefix, serviceId, secret);
    }

    public Task<ServiceKeyValidationResult?> ValidateServiceKeyAsync(string serviceKey, CancellationToken ct = default) =>
        ValidationClient.ValidateServiceKeyAsync(_http, _coreRoot, serviceKey, _serviceId, _serviceSecret, ct);

    public Task<UsageEstimateResult?> EstimateUsageAsync(string serviceKey, decimal estimatedUnits, CancellationToken ct = default) =>
        ValidationClient.EstimateUsageAsync(_http, _coreRoot, serviceKey, estimatedUnits, _serviceId, _serviceSecret, ct);

    public Task<UsageEstimateResult?> EstimateUsageWithJwtAsync(string bearerToken, string userId, string serviceId,
        decimal estimatedUnits, CancellationToken ct = default) =>
        ValidationClient.EstimateUsageWithJwtAsync(_http, _coreRoot, bearerToken, userId, serviceId, estimatedUnits, ct);

    public Task<GatewayInvokeResult?> InvokeServiceAsync(string method, string serviceId, string endpointId, string serviceKey,
        string? body, bool async, CancellationToken ct = default) =>
        GatewayInvokeClient.InvokeAsync(_http, _gatewayBaseUrl, _gatewayPathPrefix, method, serviceId, endpointId, serviceKey, body, async, ct);

    public Task<UsageReportResponse> ReportUsageAsync(string userId, string serviceId, decimal unitsUsed, CancellationToken ct = default) =>
        UsageClient.ReportUsageAsync(_http, _usageBase, userId, serviceId, unitsUsed, _serviceSecret, null, _usagePathPrefix, ct);

    public Task<UsageReportResponse> ReportUsageAsync(string userId, string serviceId, decimal unitsUsed, DateTime? timestamp, CancellationToken ct = default) =>
        UsageClient.ReportUsageAsync(_http, _usageBase, userId, serviceId, unitsUsed, _serviceSecret, timestamp, _usagePathPrefix, ct);

    public Task<UsageCallbackResult> SendProgressUpdateAsync(string progressUrl, string requestId, string stage, int percentageComplete, CancellationToken ct = default) =>
        UsageClient.ReportProgressAsync(_http, progressUrl, requestId, stage, percentageComplete, _serviceSecret, ct);

    public Task<UsageCallbackResult> SendProgressUpdateAsync(string progressUrl, string requestId, string stage, int percentageComplete, string? errorMessage, CancellationToken ct = default) =>
        UsageClient.ReportProgressAsync(_http, progressUrl, requestId, stage, percentageComplete, errorMessage, _serviceSecret, ct);

    public Task<UsageCallbackResult> SendCompletionAsync(string callbackUrl, string requestId, CompletionStatus status, decimal units, CancellationToken ct = default) =>
        UsageClient.ReportCompletionAsync(_http, callbackUrl, requestId, status, null, null, null, units, _serviceSecret, ct);

    public Task<UsageCallbackResult> SendCompletionAsync(string callbackUrl, string requestId, CompletionStatus status, string? result, decimal units, CancellationToken ct = default) =>
        UsageClient.ReportCompletionAsync(_http, callbackUrl, requestId, status, result, null, null, units, _serviceSecret, ct);

    public Task<UsageCallbackResult> SendCompletionAsync(string callbackUrl, string requestId, CompletionStatus status, string? result, string? resultUrl, string? contentType, decimal units, CancellationToken ct = default) =>
        UsageClient.ReportCompletionAsync(_http, callbackUrl, requestId, status, result, resultUrl, contentType, units, _serviceSecret, ct);

    public Task<(bool Ok, int StatusCode, string Body)> GetRequestStatusAsync(string requestId, string serviceKey, CancellationToken ct = default) =>
        GatewayClient.GetRequestStatusAsync(_http, _gatewayBaseUrl, _gatewayPathPrefix, requestId, serviceKey, ct);

    public Task<(bool Ok, int StatusCode, string Body)> GetRequestResultAsync(string requestId, string serviceKey, CancellationToken ct = default) =>
        GatewayClient.GetRequestResultAsync(_http, _gatewayBaseUrl, _gatewayPathPrefix, requestId, serviceKey, ct);

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
