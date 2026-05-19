using Xunit;

namespace Tollara.ServiceSdk.Tests;

/// <summary>Vectors from docs/hmac-spec.md (outbound bodyString + timestamp).</summary>
public class HmacTests
{
    private const string OutboundVectorCanonical = "1234567890";
    private const string OutboundVectorKey = "secret";
    private const string OutboundVectorSignature = "Bgs+chJF8gBA3xW2542Tm7B7l571zTPfLMBiCBwOp2c=";

    [Fact]
    public void CalculateHmac_MatchesHmacSpec_OutboundVector()
    {
        Assert.Equal(OutboundVectorSignature, Hmac.CalculateHmac(OutboundVectorCanonical, OutboundVectorKey));
    }

    [Fact]
    public void CalculateHmacWithTimestamp_EmptyBodyPlusTimestamp_MatchesOutboundVector()
    {
        Assert.Equal(OutboundVectorSignature, Hmac.CalculateHmacWithTimestamp("", "1234567890", OutboundVectorKey));
    }

    [Fact]
    public void CalculateHmacWithTimestamp_AgreesWithConcatenation()
    {
        const string body = """{"a":1}""";
        const string ts = "1700000000123";
        Assert.Equal(
            Hmac.CalculateHmac(body + ts, "k"),
            Hmac.CalculateHmacWithTimestamp(body, ts, "k"));
    }

    [Fact]
    public void ValidateHmacWithTimestamp_AcceptsMatchingSignature()
    {
        const string body = """{"x":"y"}""";
        const string ts = "99";
        const string key = "agent-secret";
        var sig = Hmac.CalculateHmacWithTimestamp(body, ts, key);
        Assert.True(Hmac.ValidateHmacWithTimestamp(sig, body, ts, key));
        Assert.False(Hmac.ValidateHmacWithTimestamp(sig, body, "100", key));
    }

    [Fact]
    public void ValidateHmacCanonical_MatchesCalculateHmac()
    {
        var sig = Hmac.CalculateHmac("payload1700ctx", "k");
        Assert.True(Hmac.ValidateHmacCanonical(sig, "payload1700ctx", "k"));
    }

    [Fact]
    public void ValidateHmacWithTimestamp_CoreResponseStyle_MatchesLegacyConcatenation()
    {
        const string responseBody = """{"valid":true}""";
        const string timestamp = "1700000000";
        const string key = "sec";
        var sig = Hmac.CalculateHmac(responseBody + timestamp, key);
        Assert.True(Hmac.ValidateHmacWithTimestamp(sig, responseBody, timestamp, key));
    }
}
