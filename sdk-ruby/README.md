# AgentVend SDK (Ruby)

**Gem:** `agentvend_service_sdk` (RubyGems). **Module:** `AgentVendSdk`.

HMAC helpers and inbound verification. Use Net::HTTP, Faraday, or similar for Core, Usage, and Gateway APIs.

## Configuration (base URLs)

The gem does not ship a unified HTTP client. Default production API origin is **`https://api.agentvend.api`** (aligned with other SDKs’ `AgentVendClient`). Configure Gateway, Core, and Usage bases per [**MAIN-SDK-API-SPEC.md**](../docs-sdk/MAIN-SDK-API-SPEC.md); set `AGENTVEND_API_URL` only when you need a non-production origin.

See [api-overview.md](../docs/api-overview.md).

## Environment variables (Java alignment)

The gem does **not** read the environment. Use the same names as the Java `AgentVendClient` in your deployment config:

- `AGENTVEND_API_URL` (optional override; production default is `https://api.agentvend.api`)
- `AGENTVEND_AGENT_ID` (optional depending on Core; variable name remains unchanged and maps to your service id)
- `AGENTVEND_AGENT_SECRET` (variable name remains unchanged and maps to your service secret)

There is no unified HTTP client here; use Net::HTTP, Faraday, etc., with the URLs in [**MAIN-SDK-API-SPEC.md**](../docs-sdk/MAIN-SDK-API-SPEC.md).

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

## HTTP examples

- **Validate service key:** `POST` to `{core_base}/service-keys/validate`; verify HMAC on response text + timestamp header (`HEADERS[:timestamp]`).
- **Report usage:** `POST` to `{usage_base}/api/usage/report` with signed headers. Per MAIN-SDK §3.1: JSON `timestamp` is **ISO-8601**; `X-AgentVend-Timestamp` is **Unix epoch seconds**; canonical = body string + that header value.
- **Progress / completion:** POST to full URLs from async response; sign JSON body with timestamp from query string.
- **Gateway:** `GET` `{gateway}{prefix}/requests/{request_id}/status` with `Authorization: Bearer #{service_key}`.

See [HMAC spec](../docs/hmac-spec.md) and [**MAIN-SDK-API-SPEC.md**](../docs-sdk/MAIN-SDK-API-SPEC.md).
