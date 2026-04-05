# AgentVend SDK (.NET)

**Package:** `AgentVend.AgentSdk` (NuGet), **version** `0.0.4`.

Verify HMAC, validate agent keys, run usage pre-flight checks, report usage, progress, completion, and poll job status on the gateway.

On [nuget.org](https://www.nuget.org/), relative doc links below may not resolve; use the [sdk-dotnet folder](https://github.com/maffers001/agentvend-sdk/tree/master/sdk-dotnet) in the repository for the same files with working links.

## Configuration

**Unified `AgentVendClient`** uses built-in defaults for production. Override the API origin for non-production or private deployments via `ApiUrl` on `AgentVendClientOptions` and/or **`AGENTVEND_API_URL`**. Additional constructor options exist for advanced layouts when your environment differs from the default.

**Low-level clients** (`ValidationClient`, `UsageClient`, `GatewayClient`) mirror the same defaults; overloads with explicit bases are available for custom integrations.

**Progress / completion** always use the full `progressUrl` / `callbackUrl` strings from the platform.

## HMAC (aligned with other SDKs)

- **Usage service** (report / progress / completion) and **signed Core JSON responses** (validate, usage estimate): canonical string = **`bodyJsonString + timestamp`** (concatenation, no separator; timestamp matches `X-AgentVend-Timestamp`), then **`Base64(HMAC-SHA256(canonical, agentSecret))`**. Use `Hmac.CalculateHmacWithTimestamp` / `Hmac.ValidateHmacWithTimestamp`.
- **Gateway → agent inbound:** canonical = `payload + timestamp + userContextString`. `Verifier.BuildGatewayUserContextString` is the legacy suffix; when the gateway sends **`X-AgentVend-Signing-Version: 2`**, `Verifier` uses **`BuildGatewayUserContextStringV2`** (leading `2`, no quota segment).

## Completion status (usage API)

JSON `status` for async completion must be uppercase **`COMPLETED`** or **`FAILED`**. Use `CompletionStatus.Completed` / `.Failed` with **`ToApiString()`** when building bodies (the clients do this). Do not rely on default `System.Text.Json` enum serialization for API payloads.

### Unified client

`AgentVendClient.Create` honors optional **`AGENTVEND_AGENT_ID`**, required **`AGENTVEND_AGENT_SECRET`** (or options), and optional **`AGENTVEND_API_URL`**.

```csharp
var client = AgentVendClient.Create(new AgentVendClientOptions
{
    AgentId = agentId,
    AgentSecret = agentSecret,
    HttpClient = http,
});
var report = await client.ReportUsageAsync(userId, agentId, 1m);
var estimate = await client.EstimateUsageAsync(agentKey, 1m);
var (ok, code, body) = await client.GetRequestStatusAsync(requestId, agentKey);
```

### Verify inbound HMAC and user context

```csharp
var ctx = Verifier.VerifyInboundHmacAndGetUserContext(agentSecret, headers, payload);
if (ctx is not null) { /* trusted */ }
```

## Install

```bash
dotnet add package AgentVend.AgentSdk
```

## Examples

### Verify HMAC

```csharp
using AgentVend;

var headers = new Dictionary<string, string?>(StringComparer.OrdinalIgnoreCase)
{
    ["x-agentvend-signature"] = sig,
    ["x-agentvend-timestamp"] = ts,
    // ...
};
bool valid = Verifier.VerifySignatureFromHeaders(agentSecret, headers, payload);
var ctx = Verifier.GetUserContext(headers);
```

### Inbound DTO

```csharp
var signed = new SignedUserContext("user1", "plan1", new[] { "r1" }, 10m, subscriptionActive: false);
var req = new InboundHmacRequest(sig, ts, payload, signed, SigningVersion: "2"); // optional; omit for v1
bool ok = Verifier.VerifyInboundHmac(agentSecret, req);
```

### Validate key and usage estimate (low-level, defaults)

```csharp
var result = await ValidationClient.ValidateAgentKeyAsync(http, agentKey, agentId, agentSecret);
var est = await ValidationClient.EstimateUsageAsync(http, agentKey, 1m, agentId, agentSecret);
```

Use overloads that accept an explicit Core service root when not using defaults.

### Usage, progress, completion

```csharp
var report = await UsageClient.ReportUsageAsync(http, userId, agentId, 1m, agentSecret);
await UsageClient.ReportProgressAsync(http, progressUrl, requestId, "processing", 50, agentSecret);
await UsageClient.ReportCompletionAsync(http, callbackUrl, requestId, CompletionStatus.Completed, "ok", 1m, agentSecret);
```

### Gateway status / result (low-level, defaults)

```csharp
var (ok, code, body) = await GatewayClient.GetRequestStatusAsync(http, requestId, agentKey);
```

## Tests

From the `sdk-dotnet` directory:

```bash
dotnet test AgentVend.AgentSdk.Tests/AgentVend.AgentSdk.Tests.csproj
```

## Changelog (high level)

### 0.0.4 (current)

- **Usage estimate:** `ValidationClient.EstimateUsageAsync` and `AgentVendClient.EstimateUsageAsync` call Core with the same trust model as validate; response HMAC is verified when signature headers are present.
- **Gateway HMAC v2:** `AgentVendHeaders.SigningVersion`, `Verifier.BuildGatewayUserContextStringV2`, and optional `SigningVersion` on `InboundHmacRequest` when verifying inbound requests.

### 0.0.3

- **HMAC API:** `Hmac.ValidateHmacWithTimestamp` / `Hmac.ValidateHmacCanonical` for timestamped vs full-canonical flows. `Hmac.ValidateHmacSignature` is **obsolete** (still works).
- **Completion status:** API JSON uses uppercase `COMPLETED` / `FAILED`; use `ToApiString()` for payloads.

## Release (NuGet.org)

1. **Version** — Set `<Version>` in `AgentVend.AgentSdk.csproj` to a new **SemVer** value (e.g. `0.0.5`). NuGet does not allow republishing the same version. Keep the version line at the top of this README in sync if you maintain it there.
2. **Verify** — Run tests (command above).
3. **Pack** — From `sdk-dotnet`:

   ```bash
   dotnet pack AgentVend.AgentSdk.csproj -c Release
   ```

   This writes `bin/Release/AgentVend.AgentSdk.<version>.nupkg` and a matching **`.snupkg`** (symbols).
4. **Publish** — Create an [API key](https://www.nuget.org/account/apikeys) on nuget.org (scope: push for `AgentVend.AgentSdk`). Push **both** packages:

   ```bash
   dotnet nuget push bin/Release/AgentVend.AgentSdk.<version>.nupkg   --api-key <KEY> --source https://api.nuget.org/v3/index.json
   dotnet nuget push bin/Release/AgentVend.AgentSdk.<version>.snupkg --api-key <KEY> --source https://api.nuget.org/v3/index.json
   ```

5. **After upload** — Wait for indexing, then confirm on [nuget.org/packages/AgentVend.AgentSdk](https://www.nuget.org/packages/AgentVend.AgentSdk). Tag the Git commit that matches the release.

Package metadata (license, repo URL, readme embedded in the package) is defined in the `.csproj`.

Further detail: [AgentVend documentation](https://agentvend.ai/docs).
