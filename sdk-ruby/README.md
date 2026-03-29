# AgentVend SDK (Ruby)

**Gem:** `agentvend_agent_sdk` (RubyGems)

HMAC helpers and inbound verification. Use Net::HTTP, Faraday, or similar for Core, Usage, and Gateway APIs.

## Configuration (base URLs)

**No embedded production URLs.** Configure Gateway, Core, and Usage bases per [sdk-api-spec.md](../docs/sdk-api-spec.md).

See [api-overview.md](../docs/api-overview.md).

## Install

```bash
gem install agentvend_agent_sdk
```

## Verify inbound HMAC

```ruby
require "agentvend_agent_sdk"

AgentVendAgentSdk.verify_signature_from_headers(agent_secret, headers_hash, raw_body)

AgentVendAgentSdk.verify_inbound_hmac(agent_secret,
  signature: sig,
  timestamp: ts,
  payload: body,
  user_id: uid,
  plan: plan,
  roles: %w[r1 r2],
  quota_remaining: 10
)

ctx = AgentVendAgentSdk.user_context_from_headers(headers_hash)
```

Header name constants: `AgentVendAgentSdk::HEADERS[:signature]`, etc.

## Outbound signing

```ruby
AgentVendAgentSdk.calculate_hmac(data, key)
AgentVendAgentSdk.calculate_hmac_with_timestamp(body_string, timestamp, key)
```

## HTTP examples

- **Validate:** `POST` to `{core_base}/agent-keys/validate`; verify HMAC on response text + timestamp header (`HEADERS[:timestamp]`).
- **Report usage:** `POST` to `{usage_base}/api/usage/report` with signed headers.
- **Progress / completion:** POST to full URLs from async response; sign JSON body with timestamp from query string.
- **Gateway:** `GET` `{gateway}{prefix}/requests/{request_id}/status` with `Authorization: Bearer #{agent_key}`.

See [HMAC spec](../docs/hmac-spec.md) and [API spec](../docs/sdk-api-spec.md).
