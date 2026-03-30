# AgentVend SDK (Ruby)

**Gem:** `agentvend_sdk` (RubyGems). **Module:** `AgentVendSdk` (replaces the former `agentvend_agent_sdk` gem / `AgentVendAgentSdk` module).

HMAC helpers and inbound verification. Use Net::HTTP, Faraday, or similar for Core, Usage, and Gateway APIs.

## Configuration (base URLs)

The gem does not ship a unified HTTP client. Default production API origin is **`https://api.agentvend.api`** (aligned with other SDKs’ `AgentVendClient`). Configure Gateway, Core, and Usage bases per [sdk-api-spec.md](../docs/sdk-api-spec.md); set `AGENTVEND_API_URL` only when you need a non-production origin.

See [api-overview.md](../docs/api-overview.md).

## Environment variables (Java alignment)

The gem does **not** read the environment. Use the same names as the Java `AgentVendClient` in your deployment config:

- `AGENTVEND_API_URL` (optional override; production default is `https://api.agentvend.api`)
- `AGENTVEND_AGENT_ID` (optional depending on Core)
- `AGENTVEND_AGENT_SECRET`

There is no unified HTTP client here; use Net::HTTP, Faraday, etc., with the URLs in [sdk-api-spec.md](../docs/sdk-api-spec.md).

### Verify HMAC and trusted user context in one call

```ruby
ctx = AgentVendSdk.verify_signature_from_headers_and_user_context(agent_secret, headers_hash, raw_body)
# ctx is nil if invalid; otherwise same shape as user_context_from_headers
```

## Install

```bash
gem install agentvend_sdk
```

## Verify inbound HMAC

```ruby
require "agentvend_sdk"

AgentVendSdk.verify_signature_from_headers(agent_secret, headers_hash, raw_body)

AgentVendSdk.verify_inbound_hmac(agent_secret,
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

- **Validate:** `POST` to `{core_base}/agent-keys/validate`; verify HMAC on response text + timestamp header (`HEADERS[:timestamp]`).
- **Report usage:** `POST` to `{usage_base}/api/usage/report` with signed headers.
- **Progress / completion:** POST to full URLs from async response; sign JSON body with timestamp from query string.
- **Gateway:** `GET` `{gateway}{prefix}/requests/{request_id}/status` with `Authorization: Bearer #{agent_key}`.

See [HMAC spec](../docs/hmac-spec.md) and [API spec](../docs/sdk-api-spec.md).
