using System.Net;
using System.Text;
using Xunit;

namespace Tollara.ServiceSdk.Tests;

/// <summary>Low-level client overloads without explicit base URLs must match <see cref="TollaraClient"/> defaults.</summary>
public class DefaultUrlOverloadTests
{
    private sealed class CaptureUriHandler : HttpMessageHandler
    {
        public string? LastUri { get; private set; }

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            LastUri = request.RequestUri?.ToString();
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.BadRequest));
        }
    }

    [Fact]
    public async Task ValidationClient_DefaultOverload_PostsToDefaultCoreValidate()
    {
        var handler = new CaptureUriHandler();
        using var http = new HttpClient(handler);
        await ValidationClient.ValidateServiceKeyAsync(http, "key", null, "secret", CancellationToken.None);
        Assert.Equal("https://api.tollara.ai/core/api/v1/service-keys/validate", handler.LastUri);
    }

    private sealed class OkUsageHandler : HttpMessageHandler
    {
        public string? LastUri { get; private set; }

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            LastUri = request.RequestUri?.ToString();
            var json = """{"reportSchemaVersion":2,"status":"ok","userId":"u1","serviceId":"a1","billingModelType":"SUBSCRIPTION","breakdown":{"unitsRemaining":99,"isOverLimit":false}}""";
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(json, Encoding.UTF8, "application/json"),
            });
        }
    }

    [Fact]
    public async Task UsageClient_DefaultOverload_PostsToDefaultUsageReport()
    {
        var handler = new OkUsageHandler();
        using var http = new HttpClient(handler);
        await UsageClient.ReportUsageAsync(http, "u1", "a1", 1m, "secret", CancellationToken.None);
        Assert.Equal("https://api.tollara.ai/usage/api/v1/report", handler.LastUri);
    }

    [Fact]
    public async Task GatewayClient_DefaultOverload_GetsDefaultStatusUrl()
    {
        var handler = new CaptureUriHandler();
        using var http = new HttpClient(handler);
        await GatewayClient.GetRequestStatusAsync(http, "job-1", "service-key", CancellationToken.None);
        Assert.Equal("https://api.tollara.ai/gateway/api/v1/requests/job-1/status", handler.LastUri);
    }
}
