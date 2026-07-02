using Xunit;

namespace Tollara.ServiceSdk.Tests;

public class GatewayHmacUserContextTests
{
    [Fact]
    public void BuildV3_AllFieldsPresent_GoldenString()
    {
        var ctx = Verifier.BuildGatewayUserContextStringV3(
            "sub-ext-id",
            "prod-uuid-1",
            new[] { "roleA", "roleB" },
            "ACTIVE",
            "SUBSCRIPTION",
            "PER_REQUEST",
            "request");
        Assert.Equal("3sub-ext-idprod-uuid-1roleA,roleBACTIVESUBSCRIPTIONPER_REQUESTrequest", ctx);
    }

    [Fact]
    public void BuildV3_EmptyRoles_GoldenString()
    {
        var ctx = Verifier.BuildGatewayUserContextStringV3(
            "user-1",
            "prod-1",
            Array.Empty<string>(),
            "TRIAL",
            null,
            null,
            null);
        Assert.Equal("3user-1prod-1TRIAL", ctx);
    }

    [Fact]
    public void BuildV3_BillingFieldsAbsent_GoldenString()
    {
        var ctx = Verifier.BuildGatewayUserContextStringV3(
            "owner-id",
            "",
            null,
            "ACTIVE",
            null,
            null,
            null);
        Assert.Equal("3owner-idACTIVE", ctx);
    }

    [Fact]
    public void BuildV3_NonAccessStatus_GoldenString()
    {
        var ctx = Verifier.BuildGatewayUserContextStringV3(
            "user-x",
            "prod-x",
            new[] { "r1" },
            "EXPIRED",
            "PREPAID",
            "PER_REQUEST",
            "request");
        Assert.Equal("3user-xprod-xr1EXPIREDPREPAIDPER_REQUESTrequest", ctx);
    }

    [Fact]
    public void BuildV2_OwnerNoRoles_GoldenString()
    {
        var ctx = Verifier.BuildGatewayUserContextStringV2(
            "user-1",
            "owner",
            Array.Empty<string>(),
            true,
            null,
            null,
            null);
        Assert.Equal("2user-1ownertrue", ctx);
    }

    [Fact]
    public void BuildV2_SubscriberWithRolesAndBillingFields_GoldenString()
    {
        var ctx = Verifier.BuildGatewayUserContextStringV2(
            "sub-ext-id",
            "pro",
            new[] { "roleA", "roleB" },
            false,
            "SUBSCRIPTION",
            "PER_REQUEST",
            "request");
        Assert.Equal("2sub-ext-idproroleA,roleBfalseSUBSCRIPTIONPER_REQUESTrequest", ctx);
    }

    [Fact]
    public void BuildLegacyV1_WithQuota_GoldenString()
    {
        var ctx = Verifier.BuildGatewayUserContextString(
            "a",
            "b",
            new[] { "x" },
            5m,
            false,
            "S",
            "M",
            "U");
        Assert.Equal("abx5falseSMU", ctx);
    }
}
