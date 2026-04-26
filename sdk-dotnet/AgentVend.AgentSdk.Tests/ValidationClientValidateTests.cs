using System.Net;
using System.Text;
using System.Text.Json;
using AgentVend;
using Xunit;

namespace AgentVend.AgentSdk.Tests;

public class ValidationClientValidateTests
{
    private const string AgentId = "550e8400-e29b-41d4-a716-446655440000";
    private const string AgentSecret = "test-agent-secret";
    private const string CoreRoot = "http://core.test/api/v1";
    private static readonly Guid AgentKeyId = Guid.Parse("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

    private sealed class ValidateOkHandler : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            var bodyObj = new
            {
                valid = true,
                agentKeyId = "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
                userId = "user-123",
                agentId = AgentId,
                plan = "basic",
                roles = new[] { "user" },
                quotaRemaining = 100,
                subscriptionActive = true,
                billingModelType = (string?)null,
                measurementType = (string?)null,
                unitLabel = (string?)null,
                timestamp = 1700000000L,
                error = (string?)null,
                validationSchemaVersion = 1,
            };
            var responseText = JsonSerializer.Serialize(bodyObj);
            const string timestamp = "1700000000";
            var signature = Hmac.CalculateHmac(responseText + timestamp, AgentSecret);
            var msg = new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(responseText, Encoding.UTF8, "application/json"),
            };
            msg.Headers.TryAddWithoutValidation(AgentVendHeaders.Signature, signature);
            msg.Headers.TryAddWithoutValidation(AgentVendHeaders.Timestamp, timestamp);
            return Task.FromResult(msg);
        }
    }

    [Fact]
    public async Task ValidateAgentKeyAsync_ReturnsResult_WithAgentKeyId_WhenSigned200()
    {
        using var http = new HttpClient(new ValidateOkHandler());
        var result = await ValidationClient.ValidateAgentKeyAsync(http, CoreRoot, "bearer-token", AgentId, AgentSecret);
        Assert.NotNull(result);
        Assert.Equal("user-123", result!.UserId);
        Assert.Equal(AgentId, result.AgentId);
        Assert.Equal(AgentKeyId, result.AgentKeyId);
        Assert.Equal("basic", result.Plan);
        Assert.Single(result.Roles);
        Assert.Equal("user", result.Roles[0]);
        Assert.Equal(100m, result.QuotaRemaining);
        Assert.True(result.SubscriptionActive);
    }
}
