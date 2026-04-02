# AgentVend SDK (.NET)

**Package:** `AgentVend.AgentSdk` (NuGet), **version** `0.0.2`.

Verify HMAC, validate agent keys, report usage, progress, completion, and poll job status on the gateway.

On [nuget.org](https://www.nuget.org/), relative doc links below may not resolve; use the [sdk-dotnet folder](https://github.com/maffers001/agentvend-sdk/tree/master/sdk-dotnet) in the repository for the same files with working links.

## Configuration

**Unified `AgentVendClient`:** production endpoints are determined by built-in defaults. Override only for non-production or local testing: set `ApiUrl` on `AgentVendClientOptions` and/or environment variable **`AGENTVEND_API_URL`**. Use `CorePathPrefix`, `GatewayPathPrefix`, or `UsagePathPrefix` only when your deployment uses a different URL layout than the default.

**Low-level clients** (`ValidationClient`, `UsageClient`, `GatewayClient`) include overloads **without** base URL arguments; those use the same defaults as `AgentVendClient`. Use the overloads **with** explicit base URLs (and gateway path prefix where required) only for custom or self-hosted stacks.

**Progress / completion** always use the full `progressUrl` / `callbackUrl` strings from the platform.

See [api-overview.md](../docs/api-overview.md) and [sdk-api-spec.md](../docs/sdk-api-spec.md) in the repository for path layout details.

### Unified client

`AgentVendClient.Create` honors optional **`AGENTVEND_AGENT_ID`**, required **`AGENTVEND_AGENT_SECRET`** (or options), optional **`AGENTVEND_API_URL`** for overrides, and default path prefixes unless you set the prefix options.

```csharp
var client = AgentVendClient.Create(new AgentVendClientOptions
{
    AgentId = agentId,
    AgentSecret = agentSecret,
    HttpClient = http,
});
var report = await client.ReportUsageAsync(userId, agentId, 1m);
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
var req = new InboundHmacRequest(sig, ts, payload, signed);
bool ok = Verifier.VerifyInboundHmac(agentSecret, req);
```

### Validate key (low-level, defaults)

```csharp
var result = await ValidationClient.ValidateAgentKeyAsync(http, agentKey, agentId, agentSecret);
```

For a custom Core base URL (including path prefix), use the overload that accepts `coreServiceUrl` first after `HttpClient`.

### Usage, progress, completion

```csharp
var report = await UsageClient.ReportUsageAsync(http, userId, agentId, 1m, agentSecret);
await UsageClient.ReportProgressAsync(http, progressUrl, requestId, "processing", 50, agentSecret);
await UsageClient.ReportCompletionAsync(http, callbackUrl, requestId, CompletionStatus.Completed, "ok", 1m, agentSecret);
```

Use the `ReportUsageAsync` overload that takes an explicit usage service origin when not using defaults.

### Gateway status / result (low-level, defaults)

```csharp
var (ok, code, body) = await GatewayClient.GetRequestStatusAsync(http, requestId, agentKey);
```

Use the overloads that take `gatewayBaseUrl` and `gatewayPathPrefix` when not using defaults.

## Tests

From the `sdk-dotnet` directory:

```bash
dotnet test AgentVend.AgentSdk.Tests/AgentVend.AgentSdk.Tests.csproj
```

## Release (NuGet.org)

1. **Version** — Set `<Version>` in `AgentVend.AgentSdk.csproj` to a new **SemVer** value (e.g. `0.0.3`). NuGet does not allow republishing the same version. Keep the version line at the top of this README in sync if you maintain it there.
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

See [HMAC spec](../docs/hmac-spec.md) and [API spec](../docs/sdk-api-spec.md).
