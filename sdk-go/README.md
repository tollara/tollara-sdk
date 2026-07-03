# Tollara SDK (Go)

**Module:** `github.com/tollara/service-sdk-go`

HMAC helpers, inbound gateway verification, and an `TollaraClient` for validate/estimate/invoke/usage/progress/completion/gateway polling.

## Configuration

**`TollaraClient`** uses built-in production defaults. Set **`TOLLARA_SERVICE_ID`** and **`TOLLARA_SERVICE_SECRET`** for your service.

### Verify HMAC and trusted user context in one call

Verification uses HMAC user-context **v3** when `X-Tollara-Signing-Version` is `"3"` (`serviceProductId`, `subscriptionStatus`); **v2** when `"2"`; legacy v1 otherwise.

```go
ctx, ok := sdk.VerifyInboundHMACFromHeadersAndGetUserContext(serviceSecret, r.Header, string(bodyBytes))
if ok {
    _ = ctx.UserID // trusted only when ok is true
    if sdk.GrantAccess(ctx.SubscriptionStatus) {
        // invoke-eligible subscription
    }
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

// Or explicit struct (v3):
req := &sdk.InboundHmacRequest{
    Signature:          sig,
    Timestamp:          ts,
    Payload:            string(body),
    UserID:             "user1",
    ServiceProductID:   "prod-uuid-1",
    Roles:              []string{"role1", "role2"},
    SubscriptionStatus: "ACTIVE",
    SigningVersion:     "3",
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

_, _ = client.ValidateServiceKey(serviceKey) // validationSchemaVersion 3: serviceProductId, subscriptionStatus
est, _ := client.EstimateUsage(serviceKey, 1) // estimateSchemaVersion 3: breakdown.remainingCredits / remainingSpendingCap
rep, _ := client.ReportUsage(userID, serviceID, 1) // reportSchemaVersion 2 + breakdown
result := client.SendProgressUpdate(progressURL, requestID, "processing", 50, nil)
if !result.Success { /* handle callback failure */ }
complete := client.SendCompletion(callbackURL, requestID, "COMPLETED", 1, nil, nil, nil)
if !complete.Success { /* handle callback failure */ }
ok, code, body, _ := client.GetRequestStatus(requestID, serviceKey)
_ = []any{ok, code, body}
```

## Test

```bash
go test ./...
```

See this README for public SDK usage details.
