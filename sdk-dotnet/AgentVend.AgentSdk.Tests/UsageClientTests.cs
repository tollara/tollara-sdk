using System.Net;
using System.Text;
using Xunit;

namespace AgentVend.ServiceSdk.Tests;

public class UsageClientTests
{
    private sealed class CaptureUriHandler : HttpMessageHandler
    {
        public Uri? LastRequestUri { get; private set; }

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            LastRequestUri = request.RequestUri;
            var json = """{"status":"ok","isOverLimit":false,"remainingRequestsPerPeriod":1}""";
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(json, Encoding.UTF8, "application/json"),
            });
        }
    }

    [Fact]
    public void BuildUsageReportUrl_DefaultPrefix()
    {
        Assert.Equal(
            "https://usage.example.com/api/usage/report",
            UsageClient.BuildUsageReportUrl("https://usage.example.com/", null));
        Assert.Equal(
            "https://usage.example.com/api/usage/report",
            UsageClient.BuildUsageReportUrl("https://usage.example.com", ""));
    }

    [Fact]
    public void BuildUsageReportUrl_CustomPrefix()
    {
        Assert.Equal(
            "https://usage.example.com/usage/api/v1/report",
            UsageClient.BuildUsageReportUrl("https://usage.example.com", "/usage/api/v1"));
    }

    [Fact]
    public async Task ReportUsageAsync_UsesCustomUsagePathPrefix()
    {
        var handler = new CaptureUriHandler();
        using var http = new HttpClient(handler);
        await UsageClient.ReportUsageAsync(
            http,
            "https://usage.example.com",
            "u1",
            "a1",
            1m,
            "secret",
            null,
            "/custom/prefix",
            CancellationToken.None);

        Assert.Equal("https://usage.example.com/custom/prefix/report", handler.LastRequestUri?.ToString());
    }
}
