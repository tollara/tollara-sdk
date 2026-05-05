# AgentVend SDK (Python)

**Package:** `agentvend-sdk` (PyPI). **Import:** `import agentvend_sdk` (replaces the former `agentvend-agent-sdk` / `agentvend_agent_sdk` names).

Verify HMAC on incoming gateway requests, validate agent keys, run usage pre-flight (agent-key **and** JWT paths), **gateway invoke**, report usage, progress/completion, and poll async job status on the gateway.

HTTP contracts: [**MAIN-SDK-API-SPEC.md**](../docs-sdk/MAIN-SDK-API-SPEC.md). HMAC details: [hmac-spec.md](../docs/hmac-spec.md).

## Configuration

### Recommended: single `AgentVendClient`

Use **`AgentVendClient`** with one API **origin** (scheme + host, optional port). Path prefixes match **MAIN-SDK-API-SPEC** defaults unless overridden.

| Setting | Default | Notes |
|--------|---------|--------|
| API origin | **`https://api.agentvend.api`** (`AgentVendClient.DEFAULT_API_URL`) | Override with `api_url=...`, or env **`AGENTVEND_API_URL`** for staging/tests — no trailing slash required |
| Agent ID | From env **`AGENTVEND_AGENT_ID`**, or `agent_id=...` | Optional if Core can infer the agent from the key |
| Agent secret | From env **`AGENTVEND_AGENT_SECRET`**, or `agent_secret=...` | **Required** (Usage HMAC + Core response verification) |
| Core path prefix | **`/api/v1`** (`DEFAULT_CORE_PATH_PREFIX`) | ECS-style: `core_path_prefix="/core/api/v1"` on `AgentVendClient(...)` or low-level helpers |
| Gateway path prefix | **`/api`** (`DEFAULT_GATEWAY_PATH_PREFIX`) | Override with `gateway_path_prefix=...` on `AgentVendClient` or gateway helpers |
| Usage path prefix | **`/api/usage`** (`DEFAULT_USAGE_PATH_PREFIX`) | ECS: `usage_path_prefix="/usage/api/v1"` on `AgentVendClient` or `report_usage` helpers |

**Progress / completion** still use the **full** `progress_url` / `callback_url` strings from the gateway (including query params).

**Usage report (§3):** JSON body includes an ISO-8601 **`timestamp`**; **`X-AgentVend-Timestamp`** is **Unix epoch seconds** for signing. For `report_usage_at`, pass `timestamp` as epoch **seconds** (or omit for “now”); values above `1e11` are treated as milliseconds and converted.

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

Pass a **header map** (keys matched case-insensitively) and the **raw body** the gateway signed (same bytes as in the canonical string). Header names follow `AgentVendHeaders` (`X-AgentVend-*`). When the gateway sends **`X-AgentVend-Signing-Version: 2`**, verification uses the newer user-context suffix (no quota segment in the signed material).

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
# validation.agent_key_id — Core key id when present

estimate = client.estimate_usage("bearer-token", 1.0)
if estimate is not None:
    allowed = estimate.would_allow
    status = estimate.http_status

# JWT usage estimate (unsigned): bearer JWT + internal Core user id + agent id
# client.estimate_usage_with_jwt(jwt, core_user_id, agent_id, 1.0)

# Gateway invoke: method, agent_id, endpoint_id, agent_key, optional body=..., async_=...
# client.invoke_agent("POST", agent_id, endpoint_id, agent_key, body="{}", async_=False)

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

### Validate agent key and usage estimate (low-level)

```python
from agentvend_sdk import validate_agent_key, estimate_usage, estimate_usage_with_jwt, invoke_agent

# Pass your Core service base URL (same layout as the unified client’s Core target).
result = validate_agent_key(core_base_url, "bearer-token", "agent-secret", agent_id="agent-uuid")
est = estimate_usage(core_base_url, "bearer-token", "agent-secret", 1.0, agent_id="agent-uuid")
jwt_est = estimate_usage_with_jwt(
    core_base_url, "jwt", core_user_id, agent_id, 1.0, core_path_prefix="/api/v1"
)
invoke_agent(
    gateway_base_url, "POST", agent_id, endpoint_id, agent_key, body="{}", async_=False
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

report_usage(usage_service_base_url, user_id, agent_id, 1.0, agent_secret)
report_usage_at(
    usage_service_base_url,
    user_id,
    agent_id,
    1.0,
    agent_secret,
    timestamp=1700000000.0,  # epoch seconds (or ms if > 1e11)
)
report_progress(progress_url, request_id, "stage", 50, agent_secret)
report_completion_with_result(
    callback_url, request_id, CompletionStatus.COMPLETED, agent_secret, "ok", units=1.0
)
```

### Gateway job status / result (low-level)

```python
from agentvend_sdk import get_request_status, get_request_result

st = get_request_status(gateway_base_url, request_id, agent_key)
res = get_request_result(gateway_base_url, request_id, agent_key)
```

## Tests (from source)

```bash
cd sdk-python
pip install -e ".[dev,http]"
pytest
```

## Release (PyPI)

1. **Version** — Bump `version` in [`pyproject.toml`](pyproject.toml) under `[project]` (PEP 440 / SemVer). Each upload must use a **new** version; PyPI will reject duplicates.
2. **Verify** — Run tests (see above). Optionally run from a clean tree.
3. **Build distributions** — Install tooling if needed (`pip install build twine`), then from `sdk-python`:

   ```bash
   python -m build
   ```

   This creates `dist/*.whl` and `dist/*.tar.gz`.
4. **Check** — `twine check dist/*`
5. **Upload** — Use [PyPI](https://pypi.org/) (or [Test PyPI](https://test.pypi.org/) for a dry run):

   ```bash
   twine upload dist/*
   ```

   Configure credentials via `~/.pypirc`, environment variables, or a **trusted publisher** / API token as described in [PyPI’s publishing docs](https://packaging.python.org/en/latest/guides/distributing-packages-using-setuptools/#upload-your-distributions).
6. **Tag** — Tag the Git commit that matches the released version.

Project metadata (name `agentvend-sdk`, license, URLs) lives in `pyproject.toml`.

For the full HTTP matrix (invoke, validate, estimates, usage, gateway polling), see [**MAIN-SDK-API-SPEC.md**](../docs-sdk/MAIN-SDK-API-SPEC.md).
