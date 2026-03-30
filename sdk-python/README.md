# AgentVend SDK (Python)

**Package:** `agentvend-sdk` (PyPI). **Import:** `import agentvend_sdk` (replaces the former `agentvend-agent-sdk` / `agentvend_agent_sdk` names).

Verify HMAC on incoming gateway requests, validate agent keys, report usage, progress/completion, and poll async job status on the gateway.

## Configuration

### Recommended: single `AgentVendClient`

Use **`AgentVendClient`** with one API **origin** (scheme + host, optional port). The client uses the default AgentVend API URL layout; for details see the [AgentVend documentation](https://agentvend.ai/docs).

| Setting | Default | Notes |
|--------|---------|--------|
| API origin | **`https://api.agentvend.api`** (`AgentVendClient.DEFAULT_API_URL`) | Override with `api_url=...`, or env **`AGENTVEND_API_URL`** for staging/tests — no trailing slash required |
| Agent ID | From env **`AGENTVEND_AGENT_ID`**, or `agent_id=...` | Optional if Core can infer the agent from the key |
| Agent secret | From env **`AGENTVEND_AGENT_SECRET`**, or `agent_secret=...` | **Required** (Usage HMAC + Core response verification) |

**Progress / completion** still use the **full** `progress_url` / `callback_url` strings from the gateway (including query params).

Constructor arguments override environment variables when both are set.

### Environment variables

| Variable | Purpose |
|----------|---------|
| **`AGENTVEND_API_URL`** | Optional. Overrides the default production API origin when set (staging, local stacks, tests). |
| **`AGENTVEND_AGENT_ID`** | Agent UUID if you omit `agent_id=...` (optional) |
| **`AGENTVEND_AGENT_SECRET`** | Agent secret if you omit `agent_secret=...` (**required** one way or the other) |

In code, names are also available as `AgentVendClient.ENV_API_URL`, `ENV_AGENT_ID`, and `ENV_AGENT_SECRET`. The default base URL is `AgentVendClient.DEFAULT_API_URL`.

### Low-level helpers

`validate_agent_key`, `report_usage`, `get_request_status`, and related functions remain available. They take a single `base_url` using the SDK’s default URL layout for AgentVend services.

More detail: [AgentVend documentation](https://agentvend.ai/docs).

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

### Verify inbound HMAC (agent backend)

Pass a **header map** (keys matched case-insensitively) and the **raw body** the gateway signed (same bytes as in the canonical string). Header names follow `AgentVendHeaders` (`X-AgentVend-*`).

**Preferred:** verify and read user context in one step (`None` if the HMAC is invalid):

```python
from agentvend_sdk import verify_inbound_context

ctx = verify_inbound_context(agent_secret, headers, raw_body)
if ctx is not None:
    # ctx.user_id, ctx.plan, ...
    ...
```

The former name `verify_signature_from_headers_and_get_user_context` remains available as an alias of `verify_inbound_context`.

**Or** verify and read separately:

```python
from agentvend_sdk import verify_signature_from_headers, get_user_context

if verify_signature_from_headers(agent_secret, headers, raw_body):
    ctx = get_user_context(headers)
```

For full control, build `InboundHmacRequest` with `SignedUserContext` and call `verify_inbound_hmac(agent_secret, req)`.

### Caller / backend HTTP APIs (single client)

```python
from agentvend_sdk import AgentVendClient, CompletionStatus

# Default API origin is production; pass api_url=... or set AGENTVEND_API_URL only to override.
# agent_secret is required (here or via AGENTVEND_AGENT_SECRET).
client = AgentVendClient(
    agent_id=agent_id,
    agent_secret=agent_secret,
)

validation = client.validate_agent_key("bearer-token")

usage_resp = client.report_usage(user_id, agent_id, 1.0)

client.send_progress_update(progress_url, request_id, "some processing info", 50)

client.send_completion(
    callback_url, request_id, CompletionStatus.COMPLETED, units=1.0, result="some result"
)

status = client.get_request_status(request_id, agent_key)
result = client.get_request_result(request_id, agent_key)
```

## Low-level functions (explicit URLs per call)

Use these when you are not using `AgentVendClient`, or when you pass an explicit `base_url` per call.

### Verify HMAC (low-level)

```python
from agentvend_sdk import (
    AgentVendHeaders,
    verify_signature_from_headers,
    get_user_context,
)

agent_secret = "your-agent-secret"
headers = {
    "x-agentvend-signature": sig,
    "x-agentvend-timestamp": ts,
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

result = validate_agent_key("https://api.agentvend.api", "bearer-token", "agent-secret", agent_id="agent-uuid")
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

report_usage("https://api.agentvend.api", user_id, agent_id, 1.0, agent_secret)
report_usage_at(
    "https://api.agentvend.api", user_id, agent_id, 1.0, agent_secret, timestamp=1700000000.0,
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
    "https://api.agentvend.api", request_id, agent_key
)
res = get_request_result(
    "https://api.agentvend.api", request_id, agent_key
)
```

## Tests (from source)

```bash
cd sdk-python
pip install -e ".[dev,http]"
pytest
```

For HMAC signing and HTTP API layout, see the [AgentVend documentation](https://agentvend.ai/docs).
