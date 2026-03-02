# Agent Hub SDK (Go)

**Module:** `github.com/your-org/agent-sdk-go`

Verify HMAC, validate agent keys, report usage, progress, and completion.

## Install

```bash
go get github.com/your-org/agent-sdk-go
```

## Example

```go
import "github.com/your-org/agent-sdk-go/sdk"

valid := sdk.VerifySignature(agentSecret, signature, timestamp, payload, userID, plan, roles, quotaRemaining)
ctx := sdk.GetUserContext(headers["X-Marketplace-User-ID"], headers["X-Marketplace-Plan"], ...)
```

See [HMAC spec](../docs/hmac-spec.md) and [API overview](../docs/api-overview.md).
