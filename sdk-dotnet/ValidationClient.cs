using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json;

namespace Tollara;

public record ServiceKeyValidationResult(
    string? UserId,
    string? ServiceId,
    string? ServiceProductId,
    IReadOnlyList<string> Roles,
    string? SubscriptionStatus,
    int? ValidationSchemaVersion,
    string? BillingModelType,
    string? MeasurementType,
    string? UnitLabel,
    Guid? ServiceKeyId)
{
    public bool GrantAccess() => Verifier.GrantAccess(SubscriptionStatus);

    public static bool GrantAccess(string? subscriptionStatus) => Verifier.GrantAccess(subscriptionStatus);
}

/// <summary>Wire result for Core usage estimate endpoints (see docs-sdk/MAIN-SDK-API-SPEC.md §2.3).</summary>
public record UsageEstimateResult(
    bool SufficientCredits,
    bool WouldExceedCap,
    bool WouldAllow,
    decimal? EstimatedCost,
    string? BillingModelType,
    string? MeasurementType,
    string? UnitLabel,
    UsageBreakdown? Breakdown,
    int EstimateSchemaVersion,
    long Timestamp,
    int HttpStatus);

/// <summary>Canonical failure codes for validate outcome (§2.1.1).</summary>
public enum ValidationFailureCode
{
    MISSING_KEY,
    NETWORK,
    HTTP_ERROR,
    MISSING_SIGNATURE_HEADERS,
    HMAC_MISMATCH,
    INVALID_KEY,
    PARSE_ERROR,
}

public record ServiceKeyValidationFailure(
    ValidationFailureCode Code,
    string? Message = null,
    int? HttpStatus = null);

public abstract record ServiceKeyValidationOutcome
{
    public sealed record Success(ServiceKeyValidationResult Result) : ServiceKeyValidationOutcome;
    public sealed record Failure(ServiceKeyValidationFailure Error) : ServiceKeyValidationOutcome;

    public bool Ok => this is Success;
}

public static class ValidationClient
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNameCaseInsensitive = true,
    };

    /// <summary>Validate using the SDK default API origin and Core path prefix (same as <see cref="TollaraClient"/>).</summary>
    public static Task<ServiceKeyValidationResult?> ValidateServiceKeyAsync(HttpClient http, string serviceKey, string? serviceId,
        string serviceSecret, CancellationToken ct = default) =>
        ValidateServiceKeyAsync(http, DefaultCoreServiceRoot(), serviceKey, serviceId, serviceSecret, ct);

    /// <summary>Validate using the SDK default API origin and Core path prefix (same as <see cref="TollaraClient"/>).</summary>
    public static Task<ServiceKeyValidationResult?> ValidateServiceKeyAsync(HttpClient http, string serviceKey, string? serviceId,
        string serviceSecret, CancellationToken ct = default) =>
        ValidateServiceKeyAsync(http, DefaultCoreServiceRoot(), serviceKey, serviceId, serviceSecret, ct);

    /// <summary>Validate with structured outcome (§2.1.1).</summary>
    public static Task<ServiceKeyValidationOutcome> ValidateServiceKeyWithOutcomeAsync(HttpClient http, string serviceKey,
        string? serviceId, string serviceSecret, CancellationToken ct = default) =>
        ValidateServiceKeyWithOutcomeAsync(http, DefaultCoreServiceRoot(), serviceKey, serviceId, serviceSecret, ct);

    /// <summary>Validate against an explicit Core service base (include path prefix, e.g. custom or local stack).</summary>
    public static async Task<ServiceKeyValidationOutcome> ValidateServiceKeyWithOutcomeAsync(HttpClient http, string coreServiceUrl,
        string serviceKey, string? serviceId, string serviceSecret, CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(serviceKey))
            return new ServiceKeyValidationOutcome.Failure(new ServiceKeyValidationFailure(ValidationFailureCode.MISSING_KEY));

        var url = coreServiceUrl.TrimEnd('/') + "/service-keys/validate";
        var body = new { serviceKey, serviceId, serviceSecret };
        var bodyStr = JsonSerializer.Serialize(body);
        var req = new HttpRequestMessage(HttpMethod.Post, url) { Content = new StringContent(bodyStr, Encoding.UTF8, "application/json") };
        HttpResponseMessage res;
        try
        {
            res = await http.SendAsync(req, ct);
        }
        catch (HttpRequestException)
        {
            return new ServiceKeyValidationOutcome.Failure(new ServiceKeyValidationFailure(ValidationFailureCode.NETWORK));
        }

        var httpStatus = (int)res.StatusCode;
        if (!res.IsSuccessStatusCode)
            return new ServiceKeyValidationOutcome.Failure(
                new ServiceKeyValidationFailure(ValidationFailureCode.HTTP_ERROR, HttpStatus: httpStatus));

        var responseText = await res.Content.ReadAsStringAsync(ct);
        var signature = res.Headers.TryGetValues(TollaraHeaders.Signature, out var sig) ? string.Join("", sig) : null;
        var timestamp = res.Headers.TryGetValues(TollaraHeaders.Timestamp, out var ts) ? string.Join("", ts) : null;
        if (string.IsNullOrEmpty(signature) || string.IsNullOrEmpty(timestamp))
            return new ServiceKeyValidationOutcome.Failure(
                new ServiceKeyValidationFailure(ValidationFailureCode.MISSING_SIGNATURE_HEADERS, HttpStatus: httpStatus));

        if (!Hmac.ValidateHmacWithTimestamp(signature, responseText, timestamp, serviceSecret))
            return new ServiceKeyValidationOutcome.Failure(
                new ServiceKeyValidationFailure(ValidationFailureCode.HMAC_MISMATCH, HttpStatus: httpStatus));

        JsonDocument doc;
        try
        {
            doc = JsonDocument.Parse(responseText);
        }
        catch (JsonException)
        {
            return new ServiceKeyValidationOutcome.Failure(
                new ServiceKeyValidationFailure(ValidationFailureCode.PARSE_ERROR, HttpStatus: httpStatus));
        }

        var root = doc.RootElement;
        if (root.TryGetProperty("valid", out var v) && !v.GetBoolean())
        {
            var message = ReadString(root, "error");
            return new ServiceKeyValidationOutcome.Failure(
                new ServiceKeyValidationFailure(ValidationFailureCode.INVALID_KEY, message, httpStatus));
        }

        var roles = root.TryGetProperty("roles", out var r) ? r.EnumerateArray().Select(x => x.GetString() ?? "").ToList() : new List<string>();
        Guid? serviceKeyId = null;
        if (root.TryGetProperty("serviceKeyId", out var sk) && sk.ValueKind == JsonValueKind.String &&
            Guid.TryParse(sk.GetString(), out var skGuid))
            serviceKeyId = skGuid;

        var result = new ServiceKeyValidationResult(
            root.TryGetProperty("userId", out var uid) ? uid.GetString() : null,
            root.TryGetProperty("serviceId", out var sid) ? sid.GetString() : serviceId,
            ReadString(root, "serviceProductId"),
            roles,
            ReadString(root, "subscriptionStatus"),
            root.TryGetProperty("validationSchemaVersion", out var vsv) && vsv.TryGetInt32(out var vv) ? vv : null,
            ReadString(root, "billingModelType"),
            ReadString(root, "measurementType"),
            ReadString(root, "unitLabel"),
            serviceKeyId
        );
        return new ServiceKeyValidationOutcome.Success(result);
    }

    /// <summary>Validate against an explicit Core service base (include path prefix, e.g. custom or local stack).</summary>
    public static async Task<ServiceKeyValidationResult?> ValidateServiceKeyAsync(HttpClient http, string coreServiceUrl,
        string serviceKey, string? serviceId, string serviceSecret, CancellationToken ct = default)
    {
        var outcome = await ValidateServiceKeyWithOutcomeAsync(http, coreServiceUrl, serviceKey, serviceId, serviceSecret, ct);
        return outcome is ServiceKeyValidationOutcome.Success s ? s.Result : null;
    }

    /// <summary>Estimate usage using the SDK default Core root (same as <see cref="TollaraClient"/>).</summary>
    public static Task<UsageEstimateResult?> EstimateUsageAsync(HttpClient http, string serviceKey, decimal estimatedUnits,
        string? serviceId, string serviceSecret, CancellationToken ct = default) =>
        EstimateUsageAsync(http, DefaultCoreServiceRoot(), serviceKey, estimatedUnits, serviceId, serviceSecret, ct);

    /// <summary>POST estimate-usage to an explicit Core base (including path prefix).</summary>
    public static async Task<UsageEstimateResult?> EstimateUsageAsync(HttpClient http, string coreServiceUrl,
        string serviceKey, decimal estimatedUnits, string? serviceId, string serviceSecret, CancellationToken ct = default)
    {
        if (estimatedUnits <= 0) return null;
        if (string.IsNullOrWhiteSpace(serviceKey)) return null;
        var url = coreServiceUrl.TrimEnd('/') + "/service-keys/estimate-usage";
        var body = new { serviceKey, serviceId, serviceSecret, estimatedUnits };
        var bodyStr = JsonSerializer.Serialize(body);
        var req = new HttpRequestMessage(HttpMethod.Post, url) { Content = new StringContent(bodyStr, Encoding.UTF8, "application/json") };
        var res = await http.SendAsync(req, ct);
        var code = (int)res.StatusCode;
        if (code != (int)HttpStatusCode.OK && code != (int)HttpStatusCode.Forbidden &&
            code != (int)HttpStatusCode.TooManyRequests) return null;
        var responseText = await res.Content.ReadAsStringAsync(ct);
        if (string.IsNullOrWhiteSpace(responseText)) return null;
        var signature = res.Headers.TryGetValues(TollaraHeaders.Signature, out var sig) ? string.Join("", sig) : null;
        var timestamp = res.Headers.TryGetValues(TollaraHeaders.Timestamp, out var ts) ? string.Join("", ts) : null;
        if (string.IsNullOrEmpty(signature) || string.IsNullOrEmpty(timestamp)) return null;
        if (!Hmac.ValidateHmacWithTimestamp(signature, responseText, timestamp, serviceSecret)) return null;
        return ParseEstimateResult(responseText, code);
    }

    /// <summary>Core JWT usage estimate (§2.2). Response is not HMAC-signed.</summary>
    public static Task<UsageEstimateResult?> EstimateUsageWithJwtAsync(HttpClient http, string bearerToken, string userId,
        string serviceId, decimal estimatedUnits, CancellationToken ct = default) =>
        EstimateUsageWithJwtAsync(http, DefaultCoreServiceRoot(), bearerToken, userId, serviceId, estimatedUnits, ct);

    /// <summary>POST <c>…/billing/usage/estimate</c> with explicit Core root (including path prefix).</summary>
    public static async Task<UsageEstimateResult?> EstimateUsageWithJwtAsync(HttpClient http, string coreServiceUrl,
        string bearerToken, string userId, string serviceId, decimal estimatedUnits, CancellationToken ct = default)
    {
        if (estimatedUnits <= 0) return null;
        if (string.IsNullOrWhiteSpace(bearerToken) || string.IsNullOrWhiteSpace(userId) || string.IsNullOrWhiteSpace(serviceId))
            return null;
        var url = coreServiceUrl.TrimEnd('/') + "/billing/usage/estimate";
        var body = new { userId, serviceId, estimatedUnits };
        var bodyStr = JsonSerializer.Serialize(body);
        var req = new HttpRequestMessage(HttpMethod.Post, url) { Content = new StringContent(bodyStr, Encoding.UTF8, "application/json") };
        req.Headers.TryAddWithoutValidation("Authorization", "Bearer " + bearerToken.Trim());
        var res = await http.SendAsync(req, ct);
        var code = (int)res.StatusCode;
        if (code != (int)HttpStatusCode.OK && code != (int)HttpStatusCode.Forbidden &&
            code != (int)HttpStatusCode.TooManyRequests) return null;
        var responseText = await res.Content.ReadAsStringAsync(ct);
        if (string.IsNullOrWhiteSpace(responseText)) return null;
        return ParseEstimateResult(responseText, code);
    }

    private static UsageEstimateResult? ParseEstimateResult(string responseText, int httpStatus)
    {
        using var doc = JsonDocument.Parse(responseText);
        var root = doc.RootElement;
        UsageBreakdown? breakdown = null;
        if (root.TryGetProperty("breakdown", out var br) && br.ValueKind == JsonValueKind.Object)
            breakdown = JsonSerializer.Deserialize<UsageBreakdown>(br.GetRawText(), JsonOptions);
        return new UsageEstimateResult(
            root.TryGetProperty("sufficientCredits", out var sc) && sc.GetBoolean(),
            root.TryGetProperty("wouldExceedCap", out var wec) && wec.GetBoolean(),
            root.TryGetProperty("wouldAllow", out var wa) && wa.GetBoolean(),
            ReadDecimal(root, "estimatedCost"),
            ReadString(root, "billingModelType"),
            ReadString(root, "measurementType"),
            ReadString(root, "unitLabel"),
            breakdown,
            root.TryGetProperty("estimateSchemaVersion", out var esv) && esv.TryGetInt32(out var ev) ? ev : 0,
            root.TryGetProperty("timestamp", out var tsv) && tsv.TryGetInt64(out var tl) ? tl : 0L,
            httpStatus
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
        JoinBaseAndPrefix(TollaraClient.DefaultApiUrl, TollaraClient.DefaultCorePathPrefix);

    private static string JoinBaseAndPrefix(string baseUrl, string pathPrefix)
    {
        var b = baseUrl.Trim().TrimEnd('/');
        if (string.IsNullOrEmpty(pathPrefix)) return b;
        var p = pathPrefix.StartsWith('/') ? pathPrefix : "/" + pathPrefix;
        return b + p;
    }
}
