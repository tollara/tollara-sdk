using System.Text.Json;
using Xunit;

namespace AgentVend.AgentSdk.Tests;

public class CompletionStatusTests
{
    [Theory]
    [InlineData(CompletionStatus.Completed, "COMPLETED")]
    [InlineData(CompletionStatus.Failed, "FAILED")]
    public void ToApiString_IsUppercaseApiValues(CompletionStatus status, string expected) =>
        Assert.Equal(expected, status.ToApiString());

    [Fact]
    public void UsageStyleCompletionBody_ContainsUppercaseStatusToken()
    {
        var body = new Dictionary<string, object>
        {
            ["status"] = CompletionStatus.Completed.ToApiString(),
            ["timestamp"] = "2026-01-01T00:00:00.0000000Z",
            ["units"] = 1m,
        };
        var json = JsonSerializer.Serialize(body);
        Assert.Contains("\"COMPLETED\"", json, StringComparison.Ordinal);
        Assert.DoesNotContain("\"Completed\"", json, StringComparison.Ordinal);
    }
}
