using System.Security.Cryptography;
using System.Text;

namespace AgentVend;

/// <summary>
/// HMAC-SHA256 helpers aligned with <c>docs/hmac-spec.md</c> and other AgentVend SDKs (Java, JS, Python).
/// Key and message are UTF-8; signature is Base64.
/// </summary>
public static class Hmac
{
    /// <summary>
    /// Base64(HMAC-SHA256(canonicalString, key)). Use when the signing input is already fully concatenated
    /// (e.g. gateway inbound: payload + timestamp + userContextString, or a test canonical string).
    /// </summary>
    public static string CalculateHmac(string canonicalString, string key)
    {
        var keyBytes = Encoding.UTF8.GetBytes(key);
        var dataBytes = Encoding.UTF8.GetBytes(canonicalString);
        using var hmac = new HMACSHA256(keyBytes);
        var hash = hmac.ComputeHash(dataBytes);
        return Convert.ToBase64String(hash);
    }

    /// <summary>
    /// Outbound usage-style signing (report / progress / completion and the same concatenation rule for core validate responses):
    /// canonical = <c>bodyJsonString + timestamp</c> (no separator), matching sdk-api-spec §3 and §6.
    /// </summary>
    public static string CalculateHmacWithTimestamp(string bodyJsonString, string timestamp, string key) =>
        CalculateHmac(bodyJsonString + timestamp, key);

    public static bool ConstantTimeEquals(string a, string b)
    {
        if (a == null || b == null) return a == b;
        if (a.Length != b.Length) return false;
        var aa = Encoding.UTF8.GetBytes(a);
        var bb = Encoding.UTF8.GetBytes(b);
        if (aa.Length != bb.Length) return false;
        uint diff = 0;
        for (int i = 0; i < aa.Length; i++) diff |= (uint)(aa[i] ^ bb[i]);
        return diff == 0;
    }

    /// <summary>
    /// Verifies a signature where the canonical input was <c>bodyJsonString + timestamp</c> (outbound to usage, or core response body + header timestamp).
    /// Equivalent to comparing <see cref="CalculateHmacWithTimestamp"/> with <paramref name="signature"/>.
    /// </summary>
    public static bool ValidateHmacWithTimestamp(string signature, string bodyJsonString, string timestamp, string key)
    {
        if (string.IsNullOrEmpty(signature) || string.IsNullOrEmpty(key) || timestamp == null) return false;
        var expected = CalculateHmacWithTimestamp(bodyJsonString, timestamp, key);
        return ConstantTimeEquals(expected, signature);
    }

    /// <summary>
    /// Verifies Base64(HMAC-SHA256(canonicalUtf8String, key)) against <paramref name="signature"/> using constant-time compare.
    /// Use for any fully built canonical string (inbound gateway verification builds the string explicitly in <see cref="Verifier"/>).
    /// </summary>
    public static bool ValidateHmacCanonical(string signature, string canonicalUtf8String, string key)
    {
        if (string.IsNullOrEmpty(signature) || string.IsNullOrEmpty(key)) return false;
        var expected = CalculateHmac(canonicalUtf8String, key);
        return ConstantTimeEquals(expected, signature);
    }

    /// <summary>
    /// Prefer <see cref="ValidateHmacWithTimestamp"/> (body + timestamp) or <see cref="ValidateHmacCanonical"/> (full canonical).
    /// This method treats <paramref name="payloadString"/> as the entire HMAC input (same as <see cref="CalculateHmac"/>).
    /// </summary>
    [Obsolete("Use ValidateHmacWithTimestamp(signature, responseBody, timestamp, key) for core/usage-style body+timestamp, or ValidateHmacCanonical(signature, canonicalString, key) for a pre-concatenated canonical string.")]
    public static bool ValidateHmacSignature(string signature, string payloadString, string key) =>
        ValidateHmacCanonical(signature, payloadString, key);
}
