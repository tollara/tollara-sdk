using System.Collections.Generic;
using Xunit;

namespace Tollara.ServiceSdk.Tests;

public class VerifierTests
{
    private const string Secret = "my-agent-secret";
    private const string SecretOwner = "test-agent-secret";

    [Fact]
    public void VerifySignatureFromHeaders_AcceptsGatewayHmacV3_WhenSigningVersionIs3()
    {
        var payload = "";
        var timestamp = "1700000000";
        var ucs = Verifier.BuildGatewayUserContextStringV3(
            "user1", "prod-1", new[] { "role1", "role2" }, "ACTIVE", "SUBSCRIPTION", "PER_REQUEST", "request");
        var signature = Hmac.CalculateHmac(payload + timestamp + ucs, Secret);
        var headers = new Dictionary<string, string?>
        {
            [TollaraHeaders.Signature] = signature,
            [TollaraHeaders.Timestamp] = timestamp,
            [TollaraHeaders.SigningVersion] = "3",
            [TollaraHeaders.UserId] = "user1",
            [TollaraHeaders.ServiceProductId] = "prod-1",
            [TollaraHeaders.Roles] = "role1,role2",
            [TollaraHeaders.SubscriptionStatus] = "ACTIVE",
            [TollaraHeaders.BillingModel] = "SUBSCRIPTION",
            [TollaraHeaders.MeasurementType] = "PER_REQUEST",
            [TollaraHeaders.UnitLabel] = "request",
        };
        Assert.True(Verifier.VerifySignatureFromHeaders(Secret, headers, payload));
        var ctx = Verifier.VerifyInboundHmacAndGetUserContext(Secret, headers, payload);
        Assert.NotNull(ctx);
        Assert.Equal("prod-1", ctx!.ServiceProductId);
        Assert.Equal("ACTIVE", ctx.SubscriptionStatus);
        Assert.True(Verifier.GrantsAccess(ctx.SubscriptionStatus));
    }

    [Fact]
    public void GrantsAccess_ReturnsFalseForNonEligibleStatus()
    {
        Assert.False(Verifier.GrantsAccess("EXPIRED"));
        Assert.False(Verifier.GrantsAccess(null));
        Assert.True(Verifier.GrantsAccess("CANCELLING_PENDING"));
    }

    [Fact]
    public void VerifySignatureFromHeaders_AcceptsGatewayHmacV2_WhenSigningVersionIs2()
    {
        var payload = "";
        var timestamp = "1700000000";
        var ucs = Verifier.BuildGatewayUserContextStringV2("user1", "plan1", new[] { "role1", "role2" }, false, null, null, null);
        var signature = Hmac.CalculateHmac(payload + timestamp + ucs, Secret);
        var headers = new Dictionary<string, string?>
        {
            [TollaraHeaders.Signature] = signature,
            [TollaraHeaders.Timestamp] = timestamp,
            [TollaraHeaders.SigningVersion] = "2",
            [TollaraHeaders.UserId] = "user1",
            [TollaraHeaders.Plan] = "plan1",
            [TollaraHeaders.Roles] = "role1,role2",
            [TollaraHeaders.SubscriptionActive] = "false",
        };
        Assert.True(Verifier.VerifySignatureFromHeaders(Secret, headers, payload));
    }

    [Fact]
    public void VerifySignatureFromHeaders_RejectsV2Signature_WithoutSigningVersionHeader()
    {
        var payload = "";
        var timestamp = "1700000000";
        var ucs = Verifier.BuildGatewayUserContextStringV2("user1", "plan1", new[] { "role1", "role2" }, false, null, null, null);
        var signature = Hmac.CalculateHmac(payload + timestamp + ucs, Secret);
        var headers = new Dictionary<string, string?>
        {
            [TollaraHeaders.Signature] = signature,
            [TollaraHeaders.Timestamp] = timestamp,
            [TollaraHeaders.UserId] = "user1",
            [TollaraHeaders.Plan] = "plan1",
            [TollaraHeaders.Roles] = "role1,role2",
            [TollaraHeaders.SubscriptionActive] = "false",
        };
        Assert.False(Verifier.VerifySignatureFromHeaders(Secret, headers, payload));
    }

    [Fact]
    public void VerifyInboundHmac_AcceptsExtendedVector()
    {
        var payload = "";
        var timestamp = "1700000000";
        var ucs = Verifier.BuildGatewayUserContextString("user1", "plan1", new[] { "role1", "role2" }, 10m, false, null, null, null);
        var signature = Hmac.CalculateHmac(payload + timestamp + ucs, Secret);
        var signed = new SignedUserContext("user1", null, new[] { "role1", "role2" }, null, Plan: "plan1", QuotaRemaining: 10m);
        var req = new InboundHmacRequest(signature, timestamp, payload, signed);
        Assert.True(Verifier.VerifyInboundHmac(Secret, req));
    }

    [Fact]
    public void VerifySignatureFromHeaders_IgnoresHeaderKeyCase()
    {
        var payload = "";
        var timestamp = "1700000000";
        var ucs = Verifier.BuildGatewayUserContextString("user1", "plan1", new[] { "role1", "role2" }, 10m, false, null, null, null);
        var signature = Hmac.CalculateHmac(payload + timestamp + ucs, Secret);
        var headers = new Dictionary<string, string?>
        {
            ["x-tollara-signature"] = signature,
            ["x-tollara-timestamp"] = timestamp,
            ["x-tollara-user-id"] = "user1",
            ["x-tollara-plan"] = "plan1",
            ["x-tollara-roles"] = "role1,role2",
            ["x-tollara-quota-remaining"] = "10",
            ["x-tollara-subscription-active"] = "false",
        };
        Assert.True(Verifier.VerifySignatureFromHeaders(Secret, headers, payload));
    }

    [Fact]
    public void VerifyInboundHmacAndGetUserContext_ReturnsContextWhenValid()
    {
        var payload = "";
        var timestamp = "1700000000";
        var ucs = Verifier.BuildGatewayUserContextString("user1", "plan1", new[] { "role1", "role2" }, 10m, false, null, null, null);
        var signature = Hmac.CalculateHmac(payload + timestamp + ucs, Secret);
        var headers = new Dictionary<string, string?>
        {
            [TollaraHeaders.Signature] = signature,
            [TollaraHeaders.Timestamp] = timestamp,
            [TollaraHeaders.UserId] = "user1",
            [TollaraHeaders.Plan] = "plan1",
            [TollaraHeaders.Roles] = "role1,role2",
            [TollaraHeaders.QuotaRemaining] = "10",
            [TollaraHeaders.SubscriptionActive] = "false",
        };
        var ctx = Verifier.VerifyInboundHmacAndGetUserContext(Secret, headers, payload);
        Assert.NotNull(ctx);
        Assert.Equal("user1", ctx!.UserId);
    }

    [Fact]
    public void VerifyInboundHmacAndGetUserContext_ReturnsNullWhenInvalid()
    {
        var headers = new Dictionary<string, string?>
        {
            [TollaraHeaders.Signature] = "bad",
            [TollaraHeaders.Timestamp] = "1700000000",
        };
        Assert.Null(Verifier.VerifyInboundHmacAndGetUserContext(Secret, headers, ""));
    }

    [Fact]
    public void OwnerLikeContext_MatchesGateway()
    {
        var payload = "{\"hello\":1}";
        var ts = "1700000000";
        var quota = 9223372036854775807m;
        var ucs = Verifier.BuildGatewayUserContextString("user-1", "owner", Array.Empty<string>(), quota, true, null, null, null);
        var sig = Hmac.CalculateHmac(payload + ts + ucs, SecretOwner);
        Assert.True(Verifier.VerifySignature(SecretOwner, sig, ts, payload, "user-1", "owner", Array.Empty<string>(), quota, true));
    }

    [Fact]
    public void SubscriberWithBilling_MatchesGateway()
    {
        var payload = "";
        var ts = "1710000000";
        var ucs = Verifier.BuildGatewayUserContextString(
            "sub-user", "basic", new[] { "roleA", "roleB" }, 50m, true, "SUBSCRIPTION", "PER_REQUEST", "request");
        var sig = Hmac.CalculateHmac(payload + ts + ucs, SecretOwner);
        Assert.True(Verifier.VerifySignature(SecretOwner, sig, ts, payload, "sub-user", "basic",
            new[] { "roleA", "roleB" }, 50m, true, "SUBSCRIPTION", "PER_REQUEST", "request"));
    }

    [Fact]
    public void GetUserContext_ReadsV3Headers()
    {
        var headers = new Dictionary<string, string?>
        {
            ["x-tollara-user-id"] = "u1",
            ["x-tollara-service-product-id"] = "prod-1",
            ["x-tollara-subscription-status"] = "ACTIVE",
            ["x-tollara-billing-model"] = "USAGE_POSTPAID",
            ["x-tollara-measurement-type"] = "PER_TOKEN",
            ["x-tollara-unit-label"] = "token",
        };
        var ctx = Verifier.GetUserContext(headers);
        Assert.Equal("u1", ctx.UserId);
        Assert.Equal("prod-1", ctx.ServiceProductId);
        Assert.Equal("ACTIVE", ctx.SubscriptionStatus);
        Assert.Equal("USAGE_POSTPAID", ctx.BillingModelType);
        Assert.Equal("PER_TOKEN", ctx.MeasurementType);
        Assert.Equal("token", ctx.UnitLabel);
    }
}
