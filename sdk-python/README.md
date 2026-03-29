# AgentVend SDK (Python)

**Package:** `agentvend-sdk` (PyPI). **Import:** `import agentvend_sdk` (replaces the former `agentvend-agent-sdk` / `agentvend_agent_sdk` names).

Verify HMAC, validate agent keys, report usage, progress, completion, and poll async job status.

## Recommended: unified client (single API origin)

Use **`AgentVendClient`** with one base URL (`api_url` or env `AGENTVEND_API_URL`). The client applies the usual path prefixes for Core (`/api/v1`), Gateway (`/api`), and Usage (`/api/usage` before `/report`)—same defaults as the Java SDK. Override with `core_path_prefix`, `gateway_path_prefix`, `usage_path_prefix`, or split hosts (`core_api_url`, `gateway_api_url`, `usage_api_url`) when your deployment differs (ECS, etc.); see [sdk-api-spec.md](../docs/sdk-api-spec.md) §3.

Required env (if not passed in the constructor): `AGENTVEND_API_URL`, `AGENTVEND_AGENT_SECRET`; optional `AGENTVEND_AGENT_ID`.

```python
from agentvend_sdk import AgentVendClient

client = AgentVendClient(
    api_url="https://api.example.com",
    agent_id="agent-uuid",
    agent_secret="secret",
)
# or: client = AgentVendClient.from_env()

client.validate_agent_key(agent_key)
client.report_usage(user_id, agent_id, 1.0)
client.get_request_status(request_id, agent_key)
```

Constructor arguments override environment variables. Optional `usage_path_prefix` on the client overrides the default Usage segment (same as Java `usagePathPrefix`).

See [api-overview.md](../docs/api-overview.md).

## Low-level functions (explicit URLs per call)

The module also exposes **stateless** helpers (`validate_agent_key`, `report_usage`, `get_request_status`, …) where **each call** takes the service base URL (and prefix for gateway) you want. Use these when you are not using `AgentVendClient`, or when services live on different origins and you prefer not to construct a client. **Progress / completion** always use full `progress_url` / `callback_url` strings from the platform.

### Verify signature and user context in one step

```python
from agentvend_sdk import verify_signature_from_headers_and_get_user_context

ctx = verify_signature_from_headers_and_get_user_context(agent_secret, headers, raw_body)
if ctx is not None:
    ...
```

## Requirements

Python 3.10+

## Install

```bash
pip install agentvend-sdk
```

HTTP features (validate, usage, gateway, progress):

```bash
pip install agentvend-sdk[http]
```

## Examples

### Verify HMAC (backend)

```python
from agentvend_sdk import (
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
from agentvend_sdk import verify_inbound_hmac, InboundHmacRequest, SignedUserContext

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

### Validate agent key (low-level)

```python
from agentvend_sdk import validate_agent_key

# core_service_url is the Core root including prefix, e.g. .../api/v1
result = validate_agent_key(
    "https://core.example.com/api/v1",
    "bearer-token",
    "agent-secret",
    agent_id="agent-uuid",
)
```

### Report usage, progress, completion (low-level)

```python
from agentvend_sdk import (
    CompletionStatus,
    report_usage,
    report_usage_at,
    report_progress,
    report_completion_with_result,
)

report_usage("https://usage.example.com", user_id, agent_id, 1.0, agent_secret)
report_usage_at(
    "https://usage.example.com", user_id, agent_id, 1.0, agent_secret, timestamp=1700000000.0,
    usage_path_prefix="/api/usage",  # optional; default /api/usage
)
report_progress(progress_url, request_id, "stage", 50, agent_secret)
report_completion_with_result(
    callback_url, request_id, CompletionStatus.COMPLETED, agent_secret, "ok", units=1.0
)
```

### Gateway job status / result (low-level)

```python
from agentvend_sdk import get_request_status, get_request_result

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
