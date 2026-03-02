# Agent Hub SDK (Python)

**Package:** `marketplace-agent-sdk` (PyPI)

Verify HMAC, validate agent keys, report usage, progress, and completion.

## Install

```bash
pip install marketplace-agent-sdk
```

For validate/report (HTTP): `pip install marketplace-agent-sdk requests`

## Example

```python
from marketplace_agent_sdk import verify_signature, get_user_context, validate_agent_key, report_usage

# Verify signature (backend)
valid = verify_signature(agent_secret, signature, timestamp, payload, user_id, plan, roles, quota_remaining)
ctx = get_user_context(headers)

# Validate key (caller) – requires requests
result = validate_agent_key(core_service_url, agent_key, agent_secret, agent_id=agent_id)

# Report usage (backend) – requires requests
resp = report_usage(usage_service_url, user_id, agent_id, 1.0, agent_secret)
```

See [HMAC spec](../docs/hmac-spec.md) and [API overview](../docs/api-overview.md).
