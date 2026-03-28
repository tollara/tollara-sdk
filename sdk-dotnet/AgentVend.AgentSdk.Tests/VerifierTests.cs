using Xunit;

namespace AgentVend.AgentSdk.Tests;

public class VerifierTests
{
    private const string Secret = "my-agent-secret";

    [Fact]
    public void VerifyInboundHmac_AcceptsHmacSpecVector()
    {
        var payload = "";
        var timestamp = "1700000000";
        var userContextString = "user1plan1role1,role210";
        var dataToSign = payload + timestamp + userContextString;
        var signature = Hmac.CalculateHmac(dataToSign, Secret);
        var signed = new SignedUserContext("user1", "plan1", new[] { "role1", "role2" }, 10m);
        var req = new InboundHmacRequest(signature, timestamp, payload, signed);
        Assert.True(Verifier.VerifyInboundHmac(Secret, req));
    }

    [Fact]
    public void VerifySignatureFromHeaders_IgnoresHeaderKeyCase()
    {
        var payload = "";
        var timestamp = "1700000000";
        var dataToSign = payload + timestamp + "user1plan1role1,role210";
        var signature = Hmac.CalculateHmac(dataToSign, Secret);
        var headers = new Dictionary<string, string?>
        {
            ["x-agentvend-signature"] = signature,
            ["x-agentvend-timestamp"] = timestamp,
            ["x-agentvend-user-id"] = "user1",
            ["x-agentvend-plan"] = "plan1",
            ["x-agentvend-roles"] = "role1,role2",
            ["x-agentvend-quota-remaining"] = "10",
        };
        Assert.True(Verifier.VerifySignatureFromHeaders(Secret, headers, payload));
    }

    [Fact]
    public void GetUserContext_ReadsLowercaseKeys()
    {
        var headers = new Dictionary<string, string?>
        {
            ["x-agentvend-user-id"] = "u1",
            ["x-agentvend-subscription-active"] = "true",
        };
        var ctx = Verifier.GetUserContext(headers);
        Assert.Equal("u1", ctx.UserId);
        Assert.True(ctx.SubscriptionActive);
    }
}
