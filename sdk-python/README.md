# AgentVend SDK (Python)

**Package:** `agentvend-agent-sdk` (PyPI)

Verify HMAC, validate agent keys, report usage, progress, completion, and poll async job status.

## Configuration (base URLs)

SDK **does not embed** production hosts. You pass:

- **Core:** base URL including path prefix (e.g. `https://core.example.com/api/v1`).
- **Usage:** `usage_service_url` for `report_usage` (uses `{url}/api/usage/report`). Adjust for ECS per [sdk-api-spec.md](../docs/sdk-api-spec.md) §3.
- **Gateway:** `gateway_base_url` + `gateway_path_prefix` for `get_request_status` / `get_request_result`.
- **Progress / completion:** full URLs from async responses.

See [api-overview.md](../docs/api-overview.md).

## Requirements

Python 3.10+

## Install

```bash
pip install agentvend-agent-sdk
```

HTTP features (validate, usage, gateway, progress):

```bash
pip install agentvend-agent-sdk[http]
```

## Examples

### Verify HMAC (backend)

```python
from agentvend_agent_sdk import (
    AgentVendHeaders,
    verify_signature_from_headers,
    get_user_context,
)

agent_secret = "your-agent-secret"
headers = {  # keys matched case-insensitively
    "x-agentvend-signature": sig,
    "x-agentvend-timestamp": ts,
    # ...
}
valid = verify_signature_from_headers(agent_secret, headers, raw_body)
if valid:
    ctx = get_user_context(headers)
```

### Typed inbound request

```python
from agentvend_agent_sdk import verify_inbound_hmac, InboundHmacRequest, SignedUserContext

req = InboundHmacRequest(
    signature=sig,
    timestamp=ts,
    payload="",
    signed_user_context=SignedUserContext(
        user_id="u1",
        plan="p1",
        roles=["r1"],
        quota_remaining=10.0,
        subscription_active=False,
    ),
)
assert verify_inbound_hmac(agent_secret, req)
```

### Validate agent key

```python
from agentvend_agent_sdk import validate_agent_key

result = validate_agent_key(
    "https://core.example.com/api/v1",
    "bearer-token",
    "agent-secret",
    agent_id="agent-uuid",
)
```

### Report usage, progress, completion

```python
from agentvend_agent_sdk import (
    CompletionStatus,
    report_usage,
    report_usage_at,
    report_progress,
    report_completion_with_result,
)

report_usage("https://usage.example.com", user_id, agent_id, 1.0, agent_secret)
report_usage_at("https://usage.example.com", user_id, agent_id, 1.0, agent_secret, timestamp=1700000000.0)
report_progress(progress_url, request_id, "stage", 50, agent_secret)
report_completion_with_result(
    callback_url, request_id, CompletionStatus.COMPLETED, agent_secret, "ok", units=1.0
)
```

### Gateway job status / result

```python
from agentvend_agent_sdk import get_request_status, get_request_result

st = get_request_status(
    "https://gateway.example.com", "/api", request_id, agent_key
)
res = get_request_result(
    "https://gateway.example.com", "/gateway/api/v1", request_id, agent_key
)
```

## Tests (from source)

```bash
cd sdk-python
pip install -e ".[dev,http]"
pytest
```

See [HMAC spec](../docs/hmac-spec.md) and [API spec](../docs/sdk-api-spec.md).
