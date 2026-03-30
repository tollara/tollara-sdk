# AgentVend SDK (.NET)

**Package:** `AgentVend.AgentSdk` (NuGet)

Verify HMAC, validate agent keys, report usage, progress, completion, and poll job status on the gateway.

## Configuration (base URLs)

**Unified `AgentVendClient`:** the API origin defaults to **`https://api.agentvend.api`** (`AgentVendClient.DefaultApiUrl`). Set `ApiUrl` or **`AGENTVEND_API_URL`** only to override (staging, local). Default path prefixes match [sdk-api-spec.md](../docs/sdk-api-spec.md); use `CorePathPrefix`, `GatewayPathPrefix`, or `UsagePathPrefix` only for non-standard layouts.

**Low-level clients** take explicit URLs: Core + `/agent-keys/validate`, Usage base + `/api/usage/report`, Gateway base + prefix. **Progress / completion** use full URLs from the platform.

See [api-overview.md](../docs/api-overview.md).

### Unified client

`AgentVendClient.Create` matches Java: optional `AGENTVEND_API_URL`, optional `AGENTVEND_AGENT_ID`, required `AGENTVEND_AGENT_SECRET`, default path prefixes, optional split bases and `UsagePathPrefix`.

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

### Validate key

```csharp
var result = await ValidationClient.ValidateAgentKeyAsync(
    http, "https://api.agentvend.api/core/api/v1", agentKey, agentId, agentSecret);
```

### Usage, progress, completion

```csharp
var report = await UsageClient.ReportUsageAsync(http, "https://api.agentvend.api",
    userId, agentId, 1m, agentSecret);
await UsageClient.ReportProgressAsync(http, progressUrl, requestId, "processing", 50, agentSecret);
await UsageClient.ReportCompletionAsync(http, callbackUrl, requestId, CompletionStatus.Completed, "ok", 1m, agentSecret);
```

### Gateway status / result

```csharp
var (ok, code, body) = await GatewayClient.GetRequestStatusAsync(
    http, "https://api.agentvend.api", "/api", requestId, agentKey);
```

## Tests

```bash
cd AgentVend.AgentSdk.Tests
dotnet test
```

See [HMAC spec](../docs/hmac-spec.md) and [API spec](../docs/sdk-api-spec.md).
