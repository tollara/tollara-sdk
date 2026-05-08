# AgentVend SDK (Ruby)

**Gem:** `agentvend_service_sdk` (RubyGems). **Module:** `AgentVendSdk`.

HMAC helpers, inbound verification, and an `AgentVendClient` for validate/estimate/invoke/usage/progress/completion/gateway polling.

## Configuration (base URLs)

Use the AgentVend API origin **`https://api.agentvend.api`** by default. Configure Gateway, Core, and Usage bases per [**MAIN-SDK-API-SPEC.md**](../docs-sdk/MAIN-SDK-API-SPEC.md); set `AGENTVEND_API_URL` only when you need a non-production origin.

See [api-overview.md](../docs/api-overview.md).

## Environment variables (Java alignment)

Use these environment variable names in your deployment config:

- `AGENTVEND_API_URL` (optional override; production default is `https://api.agentvend.api`)
- `AGENTVEND_SERVICE_ID` (optional depending on Core; maps to your service id)
- `AGENTVEND_SERVICE_SECRET` (maps to your service secret)

`AgentVendClient` uses Net::HTTP and supports split bases/path-prefix overrides for Core/Gateway/Usage.

### Verify HMAC and trusted user context in one call

```ruby
ctx = AgentVendSdk.verify_signature_from_headers_and_user_context(service_secret, headers_hash, raw_body)
# ctx is nil if invalid; otherwise same shape as user_context_from_headers
```

## Install

```bash
gem install agentvend_service_sdk
```

## Verify inbound HMAC

```ruby
require "agentvend_service_sdk"

AgentVendSdk.verify_signature_from_headers(service_secret, headers_hash, raw_body)

AgentVendSdk.verify_inbound_hmac(service_secret,
  signature: sig,
  timestamp: ts,
  payload: body,
  user_id: uid,
  plan: plan,
  roles: %w[r1 r2],
  quota_remaining: 10
)

ctx = AgentVendSdk.user_context_from_headers(headers_hash)
```

Header name constants: `AgentVendSdk::HEADERS[:signature]`, etc.

## Outbound signing

```ruby
AgentVendSdk.calculate_hmac(data, key)
AgentVendSdk.calculate_hmac_with_timestamp(body_string, timestamp, key)
```

## AgentVend client example

```ruby
client = AgentVendSdk::AgentVendClient.new(
  service_id: service_id,
  service_secret: service_secret
)

client.validate_service_key(service_key)
client.estimate_usage(service_key, 1)
client.estimate_usage_with_jwt(bearer_jwt, core_user_id, service_id, 1)
client.invoke_service("POST", service_id, endpoint_id, service_key, body: "{}", async: false)
client.report_usage(user_id, service_id, 1)
client.send_progress_update(progress_url, request_id, "processing", 50)
client.send_completion(callback_url, request_id, "COMPLETED", 1)
client.get_request_status(request_id, service_key)
```

See [HMAC spec](../docs/hmac-spec.md) and [**MAIN-SDK-API-SPEC.md**](../docs-sdk/MAIN-SDK-API-SPEC.md).
