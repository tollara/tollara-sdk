# Agent Hub SDK (.NET)

**Package:** `Marketplace.AgentSdk` (NuGet)

Verify HMAC, validate agent keys, report usage, progress, and completion.

## Install

```bash
dotnet add package Marketplace.AgentSdk
```

## Example

```csharp
using Marketplace.AgentSdk;

// Verify signature (backend)
var valid = Verifier.VerifySignature(agentSecret, signature, timestamp, payload, userId, plan, roles, quotaRemaining);
var ctx = Verifier.GetUserContext(headers);

// Validate key (caller)
var result = await ValidationClient.ValidateAgentKeyAsync(http, coreServiceUrl, agentKey, agentId, agentSecret);

// Report usage (backend)
var resp = await UsageClient.ReportUsageAsync(http, usageServiceUrl, userId, agentId, 1m, agentSecret);
```

See [HMAC spec](../docs/hmac-spec.md) and [API overview](../docs/api-overview.md).
