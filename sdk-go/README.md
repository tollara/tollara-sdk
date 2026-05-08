# AgentVend SDK (Go)

**Module:** `github.com/agentvend/service-sdk-go`

HMAC helpers, inbound gateway verification, and an `AgentVendClient` for validate/estimate/invoke/usage/progress/completion/gateway polling.

## Configuration (base URLs)

Use the AgentVend API origin **`https://api.agentvend.api`** by default. Wire base URLs from your config, and set `AGENTVEND_API_URL` only when you need staging or local overrides. Path defaults follow the prefix table in [**MAIN-SDK-API-SPEC.md**](../docs-sdk/MAIN-SDK-API-SPEC.md).

See [api-overview.md](../docs/api-overview.md).

## Environment variables (Java alignment)

Use these environment variable names in your app config:

| Config key | Value |
|----------|--------|
| API URL | `AGENTVEND_API_URL` (optional override; default origin is `https://api.agentvend.api`) |
| Service ID | `AGENTVEND_SERVICE_ID` (maps to your **service id**) |
| Service secret | `AGENTVEND_SERVICE_SECRET` (maps to your **service secret**) |

`AgentVendClient` uses `net/http` and supports split bases/path-prefix overrides for Core/Gateway/Usage.

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

## AgentVend client example

```go
client, err := sdk.NewAgentVendClient(sdk.AgentVendClientOptions{
    ServiceID:     serviceID,
    ServiceSecret: serviceSecret,
})
if err != nil { panic(err) }

_, _ = client.ValidateServiceKey(serviceKey)
_, _ = client.EstimateUsage(serviceKey, 1)
_, _ = client.EstimateUsageWithJWT(bearerJwt, coreUserID, serviceID, 1)
_, _ = client.InvokeService("POST", serviceID, endpointID, serviceKey, "{}", false)
_, _ = client.ReportUsage(userID, serviceID, 1)
_, _ = client.SendProgressUpdate(progressURL, requestID, "processing", 50, nil)
_, _ = client.SendCompletion(callbackURL, requestID, "COMPLETED", 1, nil, nil, nil)
ok, code, body, _ := client.GetRequestStatus(requestID, serviceKey)
_ = []any{ok, code, body}
```

## Test

```bash
go test ./...
```

See [HMAC spec](../docs/hmac-spec.md) and [**MAIN-SDK-API-SPEC.md**](../docs-sdk/MAIN-SDK-API-SPEC.md).
