# AgentVend SDK (Go)

**Module:** `github.com/agentvend/agent-sdk-go`

HMAC helpers and inbound gateway verification. Use your own HTTP client (or `net/http`) for Core, Usage, and Gateway calls; see examples below.

## Configuration (base URLs)

**No hardcoded production URLs.** Pass Gateway, Core, and Usage bases (and path prefixes) from configuration. Report usage typically posts to `{usageBase}/api/usage/report` per the reference spec; ECS layouts differ—see [sdk-api-spec.md](../docs/sdk-api-spec.md) §3.

See [api-overview.md](../docs/api-overview.md).

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
