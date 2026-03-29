# AgentVend SDK (Go)

**Module:** `github.com/agentvend/agent-sdk-go`

HMAC helpers and inbound gateway verification. Use your own HTTP client (or `net/http`) for Core, Usage, and Gateway calls; see examples below.

## Configuration (base URLs)

**No hardcoded production URLs.** Pass Gateway, Core, and Usage bases (and path prefixes) from configuration. Report usage typically posts to `{usageBase}/api/usage/report` per the reference spec; ECS layouts differ—see [sdk-api-spec.md](../docs/sdk-api-spec.md) §3.

See [api-overview.md](../docs/api-overview.md).

## Environment variables (Java alignment)

This module does **not** read the environment for you. Use the same names as the Java `AgentVendClient` when wiring config (constants in package `sdk`):

| Constant        | Value                 |
|-----------------|-----------------------|
| `sdk.EnvAPIURL` | `AGENTVEND_API_URL`   |
| `sdk.EnvAgentID` | `AGENTVEND_AGENT_ID` |
| `sdk.EnvAgentSecret` | `AGENTVEND_AGENT_SECRET` |

There is no bundled HTTP client; combine these with your own `net/http` or other stack.

### Verify HMAC and trusted user context in one call

```go
ctx, ok := sdk.VerifyInboundHMACFromHeadersAndGetUserContext(agentSecret, r.Header, string(bodyBytes))
if ok {
    _ = ctx.UserID // trusted only when ok is true
}
```

## Install

```bash
go get github.com/agentvend/agent-sdk-go
```

## Verify inbound HMAC

```go
import "github.com/agentvend/agent-sdk-go"

// From net/http (package name is sdk):
ok := sdk.VerifyInboundHMACFromHeaders(agentSecret, r.Header, string(bodyBytes))

// Or explicit struct:
req := &sdk.InboundHmacRequest{
    Signature: sig,
    Timestamp: ts,
    Payload:   string(body),
    UserID:    "user1",
    Plan:      "plan1",
    Roles:     []string{"role1", "role2"},
    QuotaRemaining: "10",
}
ok = sdk.VerifyInboundHMAC(agentSecret, req)

ctx := sdk.UserContextFromHeaders(r.Header)
```

Constants live on `sdk` (e.g. `sdk.HeaderSignature`).

## Caller / usage flows (stdlib example)

There are no built-in HTTP clients in this module yet. Illustrative `net/http` calls:

**Validate key (Core):** `POST {coreBase}/agent-keys/validate` with JSON body; verify response HMAC over `body + X-AgentVend-Timestamp`.

**Report usage:** `POST {usageBase}/api/usage/report` with JSON body and `X-AgentVend-Signature` / `X-AgentVend-Timestamp` using `sdk.CalculateHmacWithTimestamp`.

**Gateway job status:** `GET {gatewayBase}{prefix}/requests/{requestId}/status` with `Authorization: Bearer {agentKey}`.

**Progress / completion:** `POST` to the full `progressUrl` / `callbackUrl` from the async response, signing the JSON body with `CalculateHmacWithTimestamp` and the timestamp from the URL query string (same pattern as other SDKs).

## Test

```bash
go test ./...
```

See [HMAC spec](../docs/hmac-spec.md) and [API spec](../docs/sdk-api-spec.md).
