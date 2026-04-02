using System.Net;
using System.Text;
using Xunit;

namespace AgentVend.AgentSdk.Tests;

/// <summary>Low-level client overloads without explicit base URLs must match <see cref="AgentVendClient"/> defaults.</summary>
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
        await ValidationClient.ValidateAgentKeyAsync(http, "key", null, "secret", CancellationToken.None);
        Assert.Equal("https://api.agentvend.api/api/v1/agent-keys/validate", handler.LastUri);
    }

    private sealed class OkUsageHandler : HttpMessageHandler
    {
        public string? LastUri { get; private set; }

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            LastUri = request.RequestUri?.ToString();
            var json = """{"status":"ok","isOverLimit":false,"remainingRequestsPerPeriod":1}""";
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
        Assert.Equal("https://api.agentvend.api/api/usage/report", handler.LastUri);
    }

    [Fact]
    public async Task GatewayClient_DefaultOverload_GetsDefaultStatusUrl()
    {
        var handler = new CaptureUriHandler();
        using var http = new HttpClient(handler);
        await GatewayClient.GetRequestStatusAsync(http, "job-1", "agent-key", CancellationToken.None);
        Assert.Equal("https://api.agentvend.api/api/requests/job-1/status", handler.LastUri);
    }
}
