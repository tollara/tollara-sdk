# AgentVend SDK (.NET)

**Package:** `AgentVend.AgentSdk` (NuGet)

Verify HMAC, validate agent keys, report usage, progress, completion, and poll job status on the gateway.

## Configuration (base URLs)

Nothing is hardcoded. You pass:

- **Core:** `coreServiceUrl` (trimmed) + `/agent-keys/validate`.
- **Usage:** `usageServiceUrl` + `/api/usage/report` in `ReportUsageAsync`. Align base with [sdk-api-spec.md](../docs/sdk-api-spec.md) §3 for ECS.
- **Gateway:** `gatewayBaseUrl` + `gatewayPathPrefix` for `GatewayClient` helpers.
- **Progress / completion:** full URLs with required query parameters.

See [api-overview.md](../docs/api-overview.md).

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
var signed = new SignedUserContext("user1", "plan1", new[] { "r1" }, 10m);
var req = new InboundHmacRequest(sig, ts, payload, signed);
bool ok = Verifier.VerifyInboundHmac(agentSecret, req);
```

### Validate key

```csharp
var result = await ValidationClient.ValidateAgentKeyAsync(
    http, "https://core.example.com/api/v1", agentKey, agentId, agentSecret);
```

### Usage, progress, completion

```csharp
var report = await UsageClient.ReportUsageAsync(http, "https://usage.example.com",
    userId, agentId, 1m, agentSecret);
await UsageClient.ReportProgressAsync(http, progressUrl, requestId, "processing", 50, agentSecret);
await UsageClient.ReportCompletionAsync(http, callbackUrl, requestId, CompletionStatus.Completed, "ok", 1m, agentSecret);
```

### Gateway status / result

```csharp
var (ok, code, body) = await GatewayClient.GetRequestStatusAsync(
    http, "https://gateway.example.com", "/api", requestId, agentKey);
```

## Tests

```bash
cd AgentVend.AgentSdk.Tests
dotnet test
```

See [HMAC spec](../docs/hmac-spec.md) and [API spec](../docs/sdk-api-spec.md).
