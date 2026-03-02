using System.Security.Cryptography;
using System.Text;

namespace Marketplace.AgentSdk;

public static class Hmac
{
    public static string CalculateHmac(string data, string key)
    {
        var keyBytes = Encoding.UTF8.GetBytes(key);
        var dataBytes = Encoding.UTF8.GetBytes(data);
        using var hmac = new HMACSHA256(keyBytes);
        var hash = hmac.ComputeHash(dataBytes);
        return Convert.ToBase64String(hash);
    }

    public static string CalculateHmacWithTimestamp(string bodyString, string timestamp, string key) =>
        CalculateHmac(bodyString + timestamp, key);

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

    public static bool ValidateHmacSignature(string signature, string payloadString, string key)
    {
        if (string.IsNullOrEmpty(signature) || string.IsNullOrEmpty(key)) return false;
        var expected = CalculateHmac(payloadString, key);
        return ConstantTimeEquals(expected, signature);
    }
}
