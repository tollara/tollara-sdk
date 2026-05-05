# AgentVend SDK (Go)

**Module:** `github.com/agentvend/service-sdk-go`

HMAC helpers and inbound gateway verification. Use your own HTTP client (or `net/http`) for Core, Usage, and Gateway calls; see examples below.

## Configuration (base URLs)

Other language SDKs expose a unified `AgentVendClient` that defaults the API origin to **`https://api.agentvend.api`** (`sdk.DefaultAPIURL`). This Go module does **not** read the environment; wire bases from config—use `DefaultAPIURL` as the default production origin and `EnvAPIURL` / `AGENTVEND_API_URL` when you need staging or local overrides. Path defaults follow the prefix table in [**MAIN-SDK-API-SPEC.md**](../docs-sdk/MAIN-SDK-API-SPEC.md).

See [api-overview.md](../docs/api-overview.md).

## Environment variables (Java alignment)

This module does **not** read the environment for you. Use the same names as the Java `AgentVendClient` when wiring config (constants in package `sdk`):

| Constant | Value |
|----------|--------|
| `sdk.DefaultAPIURL` | `https://api.agentvend.api` (default origin for your config; not read automatically) |
| `sdk.EnvAPIURL` | `AGENTVEND_API_URL` (optional override) |
| `sdk.EnvAgentID` | `AGENTVEND_AGENT_ID` (name remains unchanged; maps to your **service id**) |
| `sdk.EnvAgentSecret` | `AGENTVEND_AGENT_SECRET` (name remains unchanged; maps to your **service secret**) |

There is no bundled HTTP client; combine these with your own `net/http` or other stack.

### Verify HMAC and trusted user context in one call

```go
ctx, ok := sdk.VerifyInboundHMACFromHeadersAndGetUserContext(serviceSecret, r.Header, string(bodyBytes))
if ok {
    _ = ctx.UserID // trusted only when ok is true
}
```

## Install

```bash
go get github.com/agentvend/service-sdk-go
```

## Verify inbound HMAC

```go
import "github.com/agentvend/service-sdk-go"

// From net/http (package name is sdk):
ok := sdk.VerifyInboundHMACFromHeaders(serviceSecret, r.Header, string(bodyBytes))

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
ok = sdk.VerifyInboundHMAC(serviceSecret, req)

ctx := sdk.UserContextFromHeaders(r.Header)
```

Constants live on `sdk` (e.g. `sdk.HeaderSignature`).

## Caller / usage flows (stdlib example)

There are no built-in HTTP clients in this module yet. Illustrative `net/http` calls:

**Validate service key (Core):** `POST {coreBase}/service-keys/validate` with JSON body; verify response HMAC over `body + X-AgentVend-Timestamp`.

**Report usage:** `POST {usageBase}/api/usage/report` (default layout; see spec for ECS prefixes) with JSON body and `X-AgentVend-Signature` / `X-AgentVend-Timestamp` using `sdk.CalculateHmacWithTimestamp`. Per MAIN-SDK §3.1: body `timestamp` is **ISO-8601**; header timestamp is **Unix epoch seconds**; HMAC canonical is **`bodyJson + headerTimestamp`**.

**Gateway job status:** `GET {gatewayBase}{prefix}/requests/{requestId}/status` with `Authorization: Bearer {serviceKey}`.

**Progress / completion:** `POST` to the full `progressUrl` / `callbackUrl` from the async response, signing the JSON body with `CalculateHmacWithTimestamp` and the timestamp from the URL query string (same pattern as other SDKs).

## Test

```bash
go test ./...
```

See [HMAC spec](../docs/hmac-spec.md) and [**MAIN-SDK-API-SPEC.md**](../docs-sdk/MAIN-SDK-API-SPEC.md).
