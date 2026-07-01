namespace Tollara;

/// <summary>ECS vs Docker path prefixes for hosted Tollara API origins (parity with sdk-js).</summary>
public static class PathPrefixes
{
    public const string EcsCorePathPrefix = "/core/api/v1";
    public const string EcsGatewayPathPrefix = "/gateway/api/v1";
    public const string EcsUsagePathPrefix = "/usage/api/v1";

    public static bool IsHostedTollaraApiOrigin(string origin)
    {
        if (string.IsNullOrWhiteSpace(origin)) return false;
        if (!Uri.TryCreate(origin.Trim(), UriKind.Absolute, out var uri) || uri.Host is null)
            return false;
        var host = uri.Host.ToLowerInvariant();
        if (host == "api.tollara.ai" || host.EndsWith(".api.tollara.ai", StringComparison.Ordinal))
            return true;
        return host == "api.ppe.tollara.ai" || host.EndsWith(".api.ppe.tollara.ai", StringComparison.Ordinal);
    }

    public static string ResolveGatewayPathPrefix(string? baseUrl, string? overridePrefix = null)
    {
        if (!string.IsNullOrWhiteSpace(overridePrefix)) return overridePrefix.Trim();
        var origin = ResolveOrigin(baseUrl);
        return IsHostedTollaraApiOrigin(origin) ? EcsGatewayPathPrefix : TollaraClient.DefaultGatewayPathPrefix;
    }

    public static string ResolveCorePathPrefix(string? baseUrl, string? overridePrefix = null)
    {
        if (!string.IsNullOrWhiteSpace(overridePrefix)) return overridePrefix.Trim();
        var origin = ResolveOrigin(baseUrl);
        return IsHostedTollaraApiOrigin(origin) ? EcsCorePathPrefix : TollaraClient.DefaultCorePathPrefix;
    }

    public static string ResolveUsagePathPrefix(string? baseUrl, string? overridePrefix = null)
    {
        if (!string.IsNullOrWhiteSpace(overridePrefix)) return overridePrefix.Trim();
        var origin = ResolveOrigin(baseUrl);
        return IsHostedTollaraApiOrigin(origin) ? EcsUsagePathPrefix : TollaraClient.DefaultUsagePathPrefix;
    }

    private static string ResolveOrigin(string? baseUrl)
    {
        if (!string.IsNullOrWhiteSpace(baseUrl))
        {
            var t = baseUrl.Trim();
            while (t.EndsWith('/')) t = t[..^1];
            return t;
        }
        return TollaraClient.DefaultApiUrl;
    }
}
