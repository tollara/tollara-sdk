# Tollara SDK (.NET)

**Package:** `Tollara.ServiceSdk` (NuGet), **version** `2.0.0`.

Verify HMAC, validate service keys, run usage pre-flight (service-key **and** JWT), **gateway invoke**, report usage, progress, completion, and poll job status on the gateway.

This README covers the public SDK contract and usage examples.

On [nuget.org](https://www.nuget.org/), relative doc links below may not resolve; use the [sdk-dotnet folder](https://github.com/tollara/tollara-sdk/tree/master/sdk-dotnet) in the repository for the same files with working links.

## Configuration

**`TollaraClient`** uses built-in defaults for production. Override the API origin for non-production or private deployments via `ApiUrl` on `TollaraClientOptions` and/or **`TOLLARA_API_URL`**. Additional constructor options exist for advanced layouts when your environment differs from the default.

**Progress / completion** always use the full `progressUrl` / `callbackUrl` strings from the platform.

## HMAC (aligned with other SDKs)

- **Usage service** (report / progress / completion) and **signed Core JSON responses** (validate, service-key usage estimate): canonical string = **`bodyJsonString + timestamp`** (concatenation, no separator; **`timestamp`** in **`X-Tollara-Timestamp`** is **Unix epoch seconds**). For **report**, the JSON body’s **`timestamp`** field is an **ISO-8601** instant. Then **`Base64(HMAC-SHA256(canonical, serviceSecret))`**. Use `Hmac.CalculateHmacWithTimestamp` / `Hmac.ValidateHmacWithTimestamp`.
- **JWT usage estimate**: **not** HMAC-signed; do not expect signature headers.
- **Gateway → service inbound:** canonical = `payload + timestamp + userContextString`. Verification defaults to v2 via **`BuildGatewayUserContextStringV2`** (leading `2`, no quota segment). `Verifier.BuildGatewayUserContextString` remains the legacy suffix.

## Completion status (async completion payloads)

JSON `status` for async completion must be uppercase **`COMPLETED`** or **`FAILED`**. Use `CompletionStatus.Completed` / `.Failed` with **`ToApiString()`** when building bodies (the clients do this). Do not rely on default `System.Text.Json` enum serialization for API payloads.

### Tollara client

`TollaraClient.Create` honors optional **`TOLLARA_SERVICE_ID`**, required **`TOLLARA_SERVICE_SECRET`** (or options), and optional **`TOLLARA_API_URL`**.

```csharp
var client = TollaraClient.Create(new TollaraClientOptions
{
    ServiceId = serviceId,
    ServiceSecret = serviceSecret,
    HttpClient = http,
});
var report = await client.ReportUsageAsync(userId, serviceId, 1m);
var estimate = await client.EstimateUsageAsync(serviceKey, 1m);
var jwtEst = await client.EstimateUsageWithJwtAsync(bearerJwt, coreUserId, serviceId, 1m);
var invoke = await client.InvokeServiceAsync("POST", serviceId, endpointId, serviceKey, body: "{}", async: false);
var (ok, code, body) = await client.GetRequestStatusAsync(requestId, serviceKey);
```

### Verify inbound HMAC and user context

```csharp
var ctx = Verifier.VerifyInboundHmacAndGetUserContext(serviceSecret, headers, payload);
if (ctx is not null) { /* trusted */ }
```

## Install

```bash
dotnet add package Tollara.ServiceSdk
```

## Examples

### Verify HMAC

```csharp
using Tollara;

var headers = new Dictionary<string, string?>(StringComparer.OrdinalIgnoreCase)
{
    ["x-tollara-signature"] = sig,
    ["x-tollara-timestamp"] = ts,
    // ...
};
bool valid = Verifier.VerifySignatureFromHeaders(serviceSecret, headers, payload);
var ctx = Verifier.GetUserContext(headers);
```

### Inbound DTO

```csharp
var signed = new SignedUserContext("user1", "plan1", new[] { "r1" }, 10m, subscriptionActive: false);
var req = new InboundHmacRequest(sig, ts, payload, signed, SigningVersion: "2"); // v2 default/recommended
bool ok = Verifier.VerifyInboundHmac(serviceSecret, req);
```

## Tests

From the `sdk-dotnet` directory:

```bash
dotnet test Tollara.ServiceSdk.Tests/Tollara.ServiceSdk.Tests.csproj
```

## Changelog (high level)

### 0.0.6 (current)

- **Environment variables:** `TollaraClient.Create` uses `TOLLARA_SERVICE_ID` / `TOLLARA_SERVICE_SECRET` when options are omitted.

### 0.0.5

- **Gateway invoke** and **JWT usage estimate** on `TollaraClient`; usage report uses an ISO-8601 `timestamp` in the JSON body and Unix epoch seconds in `X-Tollara-Timestamp` for HMAC.
- **Usage report response** model includes optional cap/time/overage fields.

### 0.0.4

- **Usage estimate:** `TollaraClient.EstimateUsageAsync` calls Core with the same trust model as validate; response HMAC is verified when signature headers are present.
- **Gateway HMAC v2:** `TollaraHeaders.SigningVersion`, `Verifier.BuildGatewayUserContextStringV2`, and `SigningVersion: "2"` by default/recommendation on `InboundHmacRequest` when verifying inbound requests.

### 0.0.3

- **HMAC API:** `Hmac.ValidateHmacWithTimestamp` / `Hmac.ValidateHmacCanonical` for timestamped vs full-canonical flows. `Hmac.ValidateHmacSignature` is **obsolete** (still works).
- **Completion status:** API JSON uses uppercase `COMPLETED` / `FAILED`; use `ToApiString()` for payloads.

## Release (NuGet.org)

1. **Version** — Set `<Version>` in `Tollara.ServiceSdk.csproj` to a new **SemVer** value (e.g. `0.0.6`). NuGet does not allow republishing the same version. Keep the version line at the top of this README in sync if you maintain it there.
2. **Verify** — Run tests (command above).
3. **Pack** — From `sdk-dotnet`:

   ```bash
   dotnet pack Tollara.ServiceSdk.csproj -c Release
   ```

   This writes `bin/Release/Tollara.ServiceSdk.<version>.nupkg` and a matching **`.snupkg`** (symbols).
4. **Publish** — Create an [API key](https://www.nuget.org/account/apikeys) on nuget.org (scope: push for `Tollara.ServiceSdk`). Push **both** packages:

   ```bash
   dotnet nuget push bin/Release/Tollara.ServiceSdk.<version>.nupkg   --api-key <KEY> --source https://api.nuget.org/v3/index.json
   dotnet nuget push bin/Release/Tollara.ServiceSdk.<version>.snupkg --api-key <KEY> --source https://api.nuget.org/v3/index.json
   ```

5. **After upload** — Wait for indexing, then confirm on [nuget.org/packages/Tollara.ServiceSdk](https://www.nuget.org/packages/Tollara.ServiceSdk). Tag the Git commit that matches the release.

Package metadata (license, repo URL, readme embedded in the package) is defined in the `.csproj`.

Further detail is available in this README and the package API surface.
