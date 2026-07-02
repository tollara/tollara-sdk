using Xunit;

namespace Tollara.ServiceSdk.Tests;

public class PathPrefixesTests
{
    [Theory]
    [InlineData("https://api.tollara.ai", true)]
    [InlineData("https://acme.api.tollara.ai", true)]
    [InlineData("https://api.ppe.tollara.ai", true)]
    [InlineData("http://host.docker.internal:8083", false)]
    public void IsHostedTollaraApiOrigin_DetectsHostedOrigins(string origin, bool expected)
    {
        Assert.Equal(expected, PathPrefixes.IsHostedTollaraApiOrigin(origin));
    }

    [Fact]
    public void ResolveGatewayPathPrefix_UsesEcsForHostedProd()
    {
        Assert.Equal(PathPrefixes.EcsGatewayPathPrefix,
            PathPrefixes.ResolveGatewayPathPrefix("https://api.tollara.ai", null));
        Assert.Equal(TollaraClient.DefaultGatewayPathPrefix,
            PathPrefixes.ResolveGatewayPathPrefix("http://host.docker.internal:8083", null));
    }

    [Fact]
    public void ResolveGatewayPathPrefix_ExplicitOverrideWins()
    {
        Assert.Equal("/api", PathPrefixes.ResolveGatewayPathPrefix("https://api.tollara.ai", "/api"));
    }

    [Fact]
    public void ResolveCoreAndUsagePrefixes_UsesEcsForHostedProd()
    {
        Assert.Equal(PathPrefixes.EcsCorePathPrefix,
            PathPrefixes.ResolveCorePathPrefix("https://api.tollara.ai", null));
        Assert.Equal(PathPrefixes.EcsUsagePathPrefix,
            PathPrefixes.ResolveUsagePathPrefix("https://api.tollara.ai", null));
    }
}
