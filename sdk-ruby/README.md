# Tollara SDK (Ruby)

**Gem:** `tollara_service_sdk` (RubyGems). **Module:** `TollaraSdk`.

HMAC helpers, inbound verification, and an `TollaraClient` for validate/estimate/invoke/usage/progress/completion/gateway polling.

## Configuration (base URLs)

Use the Tollara API origin **`https://api.tollara.ai`** by default; set `TOLLARA_API_URL` only when you need a non-production origin.

Use this README as the public usage reference.

## Environment variables (Java alignment)

Use these environment variable names in your deployment config:

- `TOLLARA_API_URL` (optional override; production default is `https://api.tollara.ai`)
- `TOLLARA_SERVICE_ID` (optional depending on Core; maps to your service id)
- `TOLLARA_SERVICE_SECRET` (maps to your service secret)

`TollaraClient` uses Net::HTTP and supports advanced configuration options when needed.

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

## Verify inbound HMAC

```ruby
require "tollara_service_sdk"

TollaraSdk.verify_signature_from_headers(service_secret, headers_hash, raw_body)

TollaraSdk.verify_inbound_hmac(service_secret,
  signature: sig,
  timestamp: ts,
  payload: body,
  user_id: uid,
  plan: plan,
  roles: %w[r1 r2],
  quota_remaining: 10
)

ctx = TollaraSdk.user_context_from_headers(headers_hash)
```

Header name constants: `TollaraSdk::HEADERS[:signature]`, etc.

## Outbound signing

```ruby
TollaraSdk.calculate_hmac(data, key)
TollaraSdk.calculate_hmac_with_timestamp(body_string, timestamp, key)
```

## Tollara client example

```ruby
client = TollaraSdk::TollaraClient.new(
  service_id: service_id,
  service_secret: service_secret
)

client.validate_service_key(service_key) # validationSchemaVersion 3
client.estimate_usage(service_key, 1) # estimateSchemaVersion 3 breakdown
client.report_usage(user_id, service_id, 1) # reportSchemaVersion 2 breakdown
result = client.send_progress_update(progress_url, request_id, "processing", 50)
raise "progress failed" unless result.success
client.send_completion(callback_url, request_id, "COMPLETED", 1)
client.get_request_status(request_id, service_key)
```

See this README for public SDK usage details.
