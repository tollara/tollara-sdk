using System.Net;
using System.Text;
using System.Text.Json;
using AgentVend;
using Xunit;

namespace AgentVend.ServiceSdk.Tests;

public class ValidationClientEstimateTests
{
    private const string ServiceId = "550e8400-e29b-41d4-a716-446655440000";
    private const string ServiceSecret = "test-agent-secret";
    private const string CoreRoot = "http://core.test/api/v1";

    private sealed class EstimateOkHandler : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            var bodyObj = new
            {
                sufficientCredits = true,
                wouldExceedCap = false,
                wouldAllow = true,
                estimatedCost = 0.1m,
                remainingCredits = (decimal?)null,
                remainingSpendingCap = (decimal?)null,
                billingModelType = "SUBSCRIPTION",
                measurementType = "PER_REQUEST",
                unitLabel = "request",
                breakdown = (object?)null,
                estimateSchemaVersion = 1,
                timestamp = 1700000000L,
            };
            var responseText = JsonSerializer.Serialize(bodyObj);
            var timestamp = "1700000000";
            var signature = Hmac.CalculateHmac(responseText + timestamp, ServiceSecret);
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
    public async Task EstimateUsageAsync_ReturnsResult_WhenSigned200()
    {
        using var http = new HttpClient(new EstimateOkHandler());
        var result = await ValidationClient.EstimateUsageAsync(http, CoreRoot, "key-1", 1.5m, ServiceId, ServiceSecret);
        Assert.NotNull(result);
        Assert.Equal(200, result!.HttpStatus);
        Assert.True(result.WouldAllow);
        Assert.Equal(1, result.EstimateSchemaVersion);
        Assert.Equal("SUBSCRIPTION", result.BillingModelType);
    }

    [Fact]
    public async Task EstimateUsageAsync_ReturnsNull_WhenHmacInvalid()
    {
        var handler = new FuncHttpHandler(_ =>
        {
            const string responseText = """{"wouldAllow":false,"estimateSchemaVersion":1,"timestamp":1700000000}""";
            var msg = new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(responseText, Encoding.UTF8, "application/json"),
            };
            msg.Headers.TryAddWithoutValidation(AgentVendHeaders.Signature, "bad");
            msg.Headers.TryAddWithoutValidation(AgentVendHeaders.Timestamp, "1700000000");
            return Task.FromResult(msg);
        });
        using var http = new HttpClient(handler);
        var result = await ValidationClient.EstimateUsageAsync(http, CoreRoot, "k", 1m, ServiceId, ServiceSecret);
        Assert.Null(result);
    }

    [Fact]
    public async Task EstimateUsageAsync_ReturnsNull_WhenUnitsNotPositive()
    {
        using var http = new HttpClient(new FuncHttpHandler(_ => throw new InvalidOperationException("should not call")));
        Assert.Null(await ValidationClient.EstimateUsageAsync(http, CoreRoot, "k", 0m, ServiceId, ServiceSecret));
        Assert.Null(await ValidationClient.EstimateUsageAsync(http, CoreRoot, "k", -1m, ServiceId, ServiceSecret));
    }

    private sealed class FuncHttpHandler : HttpMessageHandler
    {
        private readonly Func<HttpRequestMessage, Task<HttpResponseMessage>> _fn;

        public FuncHttpHandler(Func<HttpRequestMessage, Task<HttpResponseMessage>> fn) => _fn = fn;

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            _fn(request);
    }
}
