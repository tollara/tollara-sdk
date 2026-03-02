# Agent Hub SDK (Ruby)

**Gem:** `marketplace_agent_sdk` (RubyGems)

Verify HMAC and sign outbound requests. Add HTTP for validate/report.

## Install

```bash
gem install marketplace_agent_sdk
```

## Example

```ruby
require "marketplace_agent_sdk"

valid = MarketplaceAgentSdk.verify_signature(
  agent_secret, signature, timestamp, payload, user_id, plan, roles, quota_remaining
)
sig = MarketplaceAgentSdk.calculate_hmac_with_timestamp(body_str, timestamp, key)
```

See [HMAC spec](../docs/hmac-spec.md) and [API overview](../docs/api-overview.md).
