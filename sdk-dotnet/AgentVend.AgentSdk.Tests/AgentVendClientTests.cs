using System.Net;
using System.Text;

namespace AgentVend.AgentSdk.Tests;

public class AgentVendClientTests
{
    private const string AgentId = "550e8400-e29b-41d4-a716-446655440000";
    private const string AgentSecret = "test-agent-secret";
    private const string AgentKey = "k";

    private sealed class ListUriHandler : HttpMessageHandler
    {
        public List<string> RequestUris { get; } = new();

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            RequestUris.Add(request.RequestUri!.ToString());
            var json = """{"status":"ok","isOverLimit":false,"remainingRequestsPerPeriod":1}""";
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(json, Encoding.UTF8, "application/json"),
            });
        }
    }

    private sealed class GatewayOkHandler : HttpMessageHandler
    {
        public string? LastUri { get; private set; }

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            LastUri = request.RequestUri?.ToString();
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"state":"OK"}""", Encoding.UTF8, "application/json"),
            });
        }
    }

    [Fact]
    public void Create_ThrowsWithoutApiUrl()
    {
        Assert.Throws<InvalidOperationException>(() =>
            AgentVendClient.Create(new AgentVendClientOptions { AgentSecret = AgentSecret }));
    }

    [Fact]
    public void Create_ThrowsWithoutSecret()
    {
        Assert.Throws<InvalidOperationException>(() =>
            AgentVendClient.Create(new AgentVendClientOptions { ApiUrl = "http://x" }));
    }

    [Fact]
    public async Task GetRequestStatusAsync_UsesDefaultGatewayPrefix()
    {
        var handler = new GatewayOkHandler();
        using var http = new HttpClient(handler);
        var client = AgentVendClient.Create(new AgentVendClientOptions
        {
            ApiUrl = "http://localhost:59901",
            HttpClient = http,
            AgentId = AgentId,
            AgentSecret = AgentSecret,
        });

        var (ok, code, body) = await client.GetRequestStatusAsync("job-1", AgentKey);

        Assert.True(ok);
        Assert.Equal(200, code);
        Assert.Contains("OK", body, StringComparison.Ordinal);
        Assert.Equal("http://localhost:59901/api/requests/job-1/status", handler.LastUri);
    }

    [Fact]
    public async Task ReportUsageAsync_UsesDefaultUsagePrefix()
    {
        var handler = new ListUriHandler();
        using var http = new HttpClient(handler);
        var client = AgentVendClient.Create(new AgentVendClientOptions
        {
            ApiUrl = "http://localhost:59902",
            HttpClient = http,
            AgentId = AgentId,
            AgentSecret = AgentSecret,
        });

        var report = await client.ReportUsageAsync("user-1", AgentId, 1m);

        Assert.Equal("ok", report.Status);
        Assert.Single(handler.RequestUris);
        Assert.Equal("http://localhost:59902/api/usage/report", handler.RequestUris[0]);
    }

    [Fact]
    public async Task ReportUsageAsync_CustomUsagePathPrefix()
    {
        var handler = new ListUriHandler();
        using var http = new HttpClient(handler);
        var client = AgentVendClient.Create(new AgentVendClientOptions
        {
            ApiUrl = "http://localhost:59903",
            HttpClient = http,
            AgentId = AgentId,
            AgentSecret = AgentSecret,
            UsagePathPrefix = "/usage/api/v1",
        });

        await client.ReportUsageAsync("user-1", AgentId, 1m);

        Assert.Equal("http://localhost:59903/usage/api/v1/report", handler.RequestUris[0]);
    }
}
