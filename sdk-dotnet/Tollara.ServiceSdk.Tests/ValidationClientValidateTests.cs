using System.Net;
using System.Text;
using System.Text.Json;
using Tollara;
using Xunit;

namespace Tollara.ServiceSdk.Tests;

public class ValidationClientValidateTests
{
    private const string ServiceId = "550e8400-e29b-41d4-a716-446655440000";
    private const string ServiceSecret = "test-agent-secret";
    private const string CoreRoot = "http://core.test/api/v1";
    private static readonly Guid ServiceKeyId = Guid.Parse("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
    private const string ServiceProductId = "7c9e6679-7425-40de-944b-e07fc1f90ae7";

    private sealed class ValidateOkHandler : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            var bodyObj = new
            {
                valid = true,
                serviceKeyId = "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
                userId = "user-123",
                serviceId = ServiceId,
                serviceProductId = ServiceProductId,
                roles = new[] { "user" },
                subscriptionStatus = "ACTIVE",
                billingModelType = "SUBSCRIPTION",
                measurementType = "PER_REQUEST",
                unitLabel = "request",
                timestamp = 1700000000L,
                error = (string?)null,
                validationSchemaVersion = 3,
            };
            var responseText = JsonSerializer.Serialize(bodyObj);
            const string timestamp = "1700000000";
            var signature = Hmac.CalculateHmac(responseText + timestamp, ServiceSecret);
            var msg = new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(responseText, Encoding.UTF8, "application/json"),
            };
            msg.Headers.TryAddWithoutValidation(TollaraHeaders.Signature, signature);
            msg.Headers.TryAddWithoutValidation(TollaraHeaders.Timestamp, timestamp);
            return Task.FromResult(msg);
        }
    }

    private sealed class ValidateExpiredHandler : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            var bodyObj = new
            {
                valid = true,
                serviceKeyId = "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
                userId = "user-123",
                serviceId = ServiceId,
                serviceProductId = ServiceProductId,
                roles = Array.Empty<string>(),
                subscriptionStatus = "EXPIRED",
                billingModelType = (string?)null,
                measurementType = (string?)null,
                unitLabel = (string?)null,
                timestamp = 1700000000L,
                error = (string?)null,
                validationSchemaVersion = 3,
            };
            var responseText = JsonSerializer.Serialize(bodyObj);
            const string timestamp = "1700000000";
            var signature = Hmac.CalculateHmac(responseText + timestamp, ServiceSecret);
            var msg = new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(responseText, Encoding.UTF8, "application/json"),
            };
            msg.Headers.TryAddWithoutValidation(TollaraHeaders.Signature, signature);
            msg.Headers.TryAddWithoutValidation(TollaraHeaders.Timestamp, timestamp);
            return Task.FromResult(msg);
        }
    }

    [Fact]
    public async Task ValidateServiceKeyAsync_ReturnsResult_WithV3Fields_WhenSigned200()
    {
        using var http = new HttpClient(new ValidateOkHandler());
        var result = await ValidationClient.ValidateServiceKeyAsync(http, CoreRoot, "bearer-token", ServiceId, ServiceSecret);
        Assert.NotNull(result);
        Assert.Equal("user-123", result!.UserId);
        Assert.Equal(ServiceId, result.ServiceId);
        Assert.Equal(ServiceKeyId, result.ServiceKeyId);
        Assert.Equal(ServiceProductId, result.ServiceProductId);
        Assert.Equal("ACTIVE", result.SubscriptionStatus);
        Assert.Equal(3, result.ValidationSchemaVersion);
        Assert.Single(result.Roles);
        Assert.Equal("user", result.Roles[0]);
        Assert.True(result.GrantAccess());
        Assert.Equal("SUBSCRIPTION", result.BillingModelType);
    }

    [Fact]
    public async Task ValidateServiceKeyAsync_GrantAccessFalseForExpiredStatus()
    {
        using var http = new HttpClient(new ValidateExpiredHandler());
        var result = await ValidationClient.ValidateServiceKeyAsync(http, CoreRoot, "expired-key", ServiceId, ServiceSecret);
        Assert.NotNull(result);
        Assert.False(result!.GrantAccess());
        Assert.False(ServiceKeyValidationResult.GrantAccess("EXPIRED"));
    }
}
