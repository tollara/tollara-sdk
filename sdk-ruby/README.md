# Tollara SDK (Ruby)

**Gem:** `tollara_service_sdk` (RubyGems), version **3.0.0**. **Module:** `TollaraSdk`.

HMAC helpers, inbound verification, and a **`TollaraClient`** for validate/estimate/invoke/usage/progress/completion/gateway polling.

## Configuration (base URLs)

Use the Tollara API origin **`https://api.tollara.ai`** by default; set `TOLLARA_API_URL` only when you need a non-production origin.

## Environment variables

- `TOLLARA_API_URL` (optional override)
- `TOLLARA_SERVICE_ID` (optional depending on Core)
- `TOLLARA_SERVICE_SECRET` (required for HMAC and Core response verification)

### Verify HMAC and trusted user context in one call

Verification uses HMAC user-context **v3** when `X-Tollara-Signing-Version` is `"3"` (`serviceProductId`, `subscriptionStatus`); **v2** when `"2"`; legacy v1 otherwise.

```ruby
ctx = TollaraSdk.verify_signature_from_headers_and_user_context(service_secret, headers_hash, raw_body)
# ctx is nil if invalid; otherwise includes :service_product_id, :subscription_status
TollaraSdk.grants_access(ctx[:subscription_status]) if ctx
```

## Install

```bash
gem install tollara_service_sdk
```

## Verify inbound HMAC (v3)

```ruby
require "tollara_service_sdk"

TollaraSdk.verify_signature_from_headers(service_secret, headers_hash, raw_body)

TollaraSdk.verify_inbound_hmac(service_secret,
  signature: sig,
  timestamp: ts,
  payload: body,
  user_id: uid,
  service_product_id: "prod-uuid-1",
  roles: %w[r1 r2],
  subscription_status: "ACTIVE",
  signing_version: "3"
)

ctx = TollaraSdk.user_context_from_headers(headers_hash)
```

## Tollara client example

```ruby
client = TollaraSdk::TollaraClient.new(
  service_id: service_id,
  service_secret: service_secret
)

validation = client.validate_service_key(service_key) # ServiceKeyValidationResult or nil
validation.grants_access if validation

estimate = client.estimate_usage(service_key, 1) # UsageEstimateResult or nil
report = client.report_usage(user_id, service_id, 1) # UsageReportResponse hash with breakdown
```

See this README for public SDK usage details.
