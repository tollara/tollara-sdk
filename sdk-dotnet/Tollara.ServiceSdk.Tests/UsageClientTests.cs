using System.Net;
using System.Text;
using Xunit;

namespace Tollara.ServiceSdk.Tests;

public class UsageClientTests
{
    private sealed class CaptureUriHandler : HttpMessageHandler
    {
        public Uri? LastRequestUri { get; private set; }

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            LastRequestUri = request.RequestUri;
            var json = """{"reportSchemaVersion":2,"status":"ok","warning":null,"userId":"u1","serviceId":"a1","billingModelType":"SUBSCRIPTION","measurementType":"PER_REQUEST","unitLabel":"request","breakdown":{"unitsUsed":1,"unitsRemaining":99,"remainingSpendingCap":20,"totalUnitsUsedThisCycle":1,"isOverLimit":false,"isOverage":false,"isOverageAllowed":true}}""";
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(json, Encoding.UTF8, "application/json"),
            });
        }
    }

    [Fact]
    public void BuildUsageReportUrl_HostedProdUsesEcsPrefix()
    {
        Assert.Equal(
            "https://api.tollara.ai/usage/api/v1/report",
            UsageClient.BuildUsageReportUrl("https://api.tollara.ai", null));
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
    public void ParseUsageReportResponse_ReadsReportV2Breakdown()
    {
        const string json = """
            {"reportSchemaVersion":2,"status":"ok","warning":null,"userId":"user-1","serviceId":"svc-1","billingModelType":"SUBSCRIPTION","measurementType":"PER_REQUEST","unitLabel":"request","breakdown":{"unitsUsed":1,"unitsRemaining":99,"remainingSpendingCap":20,"totalUnitsUsedThisCycle":1,"isOverLimit":false,"isOverage":false,"isOverageAllowed":true}}
            """;
        var response = UsageClient.ParseUsageReportResponse(json);
        Assert.Equal(2, response.ReportSchemaVersion);
        Assert.Equal("ok", response.Status);
        Assert.Equal("user-1", response.UserId);
        Assert.Equal("svc-1", response.ServiceId);
        Assert.NotNull(response.Breakdown);
        Assert.Equal(99m, response.Breakdown!.UnitsRemaining);
        Assert.False(response.Breakdown.OverLimit);
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

    [Fact]
    public async Task ReportProgressAsync_ReturnsUsageCallbackResult()
    {
        var progressPath = "/api/usage/progress/req-1";
        var timestamp = "1700000000";
        var progressUrl = $"https://usage.test{progressPath}?timestamp={timestamp}";
        var handler = new CaptureUriHandler();
        using var http = new HttpClient(handler);
        var result = await UsageClient.ReportProgressAsync(
            http, progressUrl, "req-1", "processing", 50, "secret", CancellationToken.None);

        Assert.True(result.Success);
        Assert.Equal(200, result.HttpStatus);
        Assert.Equal($"https://usage.test{progressPath}", result.RequestUrl);
    }

    [Fact]
    public async Task ReportProgressAsync_ReturnsFailureWhenTimestampMissing()
    {
        using var http = new HttpClient(new CaptureUriHandler());
        var result = await UsageClient.ReportProgressAsync(
            http,
            "https://usage.test/api/usage/progress/req-1",
            "req-1",
            "processing",
            25,
            "secret",
            CancellationToken.None);

        Assert.False(result.Success);
        Assert.Equal(0, result.HttpStatus);
    }

    [Fact]
    public async Task ReportProgressAsync_HandlesMissingUrlWithoutThrowing()
    {
        using var http = new HttpClient(new CaptureUriHandler());
        var result = await UsageClient.ReportProgressAsync(
            http,
            null!,
            "req-1",
            "processing",
            25,
            "secret",
            CancellationToken.None);

        Assert.False(result.Success);
        Assert.Equal(0, result.HttpStatus);
        Assert.Equal("Missing or invalid callback/progress URL", result.HttpStatusText);
    }

    [Fact]
    public async Task ReportProgressAsync_ReturnsHttpStatusAndBodyOnFailure()
    {
        var progressPath = "/api/usage/progress/req-1";
        var progressUrl = $"https://usage.test{progressPath}?timestamp=1700000000";
        var handler = new FixedResponseHandler(HttpStatusCode.NotFound, "Invalid requestId: req-1");
        using var http = new HttpClient(handler);
        var result = await UsageClient.ReportProgressAsync(
            http, progressUrl, "req-1", "processing", 25, "secret", CancellationToken.None);

        Assert.False(result.Success);
        Assert.Equal(404, result.HttpStatus);
        Assert.Equal("Invalid requestId: req-1", result.ResponseBody);
    }

    [Fact]
    public async Task ReportCompletionAsync_ReturnsFailureWhenTimestampMissing()
    {
        using var http = new HttpClient(new CaptureUriHandler());
        var result = await UsageClient.ReportCompletionAsync(
            http,
            "https://usage.test/api/usage/complete/req-1",
            "req-1",
            CompletionStatus.Completed,
            "secret",
            CancellationToken.None);

        Assert.False(result.Success);
        Assert.Equal(0, result.HttpStatus);
    }

    private sealed class FixedResponseHandler(HttpStatusCode statusCode, string body) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromResult(new HttpResponseMessage(statusCode)
            {
                Content = new StringContent(body, Encoding.UTF8, "text/plain"),
            });
    }
}
