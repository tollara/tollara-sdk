# Tollara SDK (Go)

**Module:** `github.com/tollara/service-sdk-go`

HMAC helpers, inbound gateway verification, and an `TollaraClient` for validate/estimate/invoke/usage/progress/completion/gateway polling.

## Configuration (base URLs)

Use the Tollara API origin **`https://api.tollara.ai`** by default. Set `TOLLARA_API_URL` only when you need a non-production override.

Use this README as the public usage reference.

## Environment variables (Java alignment)

Use these environment variable names in your app config:

| Config key | Value |
|----------|--------|
| API URL | `TOLLARA_API_URL` (optional override; default origin is `https://api.tollara.ai`) |
| Service ID | `TOLLARA_SERVICE_ID` (maps to your **service id**) |
| Service secret | `TOLLARA_SERVICE_SECRET` (maps to your **service secret**) |

`TollaraClient` uses `net/http` and supports advanced configuration options when needed.

### Verify HMAC and trusted user context in one call

```go
ctx, ok := sdk.VerifyInboundHMACFromHeadersAndGetUserContext(serviceSecret, r.Header, string(bodyBytes))
if ok {
    _ = ctx.UserID // trusted only when ok is true
}
```

## Install

```bash
go get github.com/tollara/service-sdk-go
```

## Verify inbound HMAC

```go
import "github.com/tollara/service-sdk-go"

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

## Tollara client example

```go
client, err := sdk.NewTollaraClient(sdk.TollaraClientOptions{
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

See this README for public SDK usage details.
