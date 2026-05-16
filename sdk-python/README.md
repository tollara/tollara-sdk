# AgentVend Service SDK (Python)

**Package:** `agentvend-service-sdk` (PyPI). **Import:** `import agentvend_service_sdk`.

Verify HMAC on incoming gateway requests, validate service keys, run usage pre-flight (service-key **and** JWT paths), **gateway invoke**, report usage, progress/completion, and poll async job status on the gateway.

This README covers the public SDK contract and usage examples.

## Configuration

### Recommended: `AgentVendClient`

Use **`AgentVendClient`** with one API **origin** (scheme + host, optional port).

| Setting | Default | Notes |
|--------|---------|--------|
| API origin | **`https://api.agentvend.api`** (`AgentVendClient.DEFAULT_API_URL`) | Override with `api_url=...`, or env **`AGENTVEND_API_URL`** for staging/tests — no trailing slash required |
| Service identity | From env **`AGENTVEND_SERVICE_ID`**, or `service_id=...` | Optional if Core can infer the service from the key |
| Service secret | From env **`AGENTVEND_SERVICE_SECRET`**, or `service_secret=...` | **Required** (Usage HMAC + Core response verification) |

**Progress / completion** still use the **full** `progress_url` / `callback_url` strings from the gateway (including query params).

**Usage report (§3):** JSON body includes an ISO-8601 **`timestamp`**; **`X-AgentVend-Timestamp`** is **Unix epoch seconds** for signing. For `report_usage_at`, pass `timestamp` as epoch **seconds** (or omit for “now”); values above `1e11` are treated as milliseconds and converted.

Constructor arguments override environment variables when both are set.

### Environment variables

| Variable | Purpose |
|----------|---------|
| **`AGENTVEND_API_URL`** | Optional. Overrides the default production API origin when set (staging, local stacks, tests). |
| **`AGENTVEND_SERVICE_ID`** | Service UUID if you omit `service_id=...` (optional) |
| **`AGENTVEND_SERVICE_SECRET`** | Service secret if you omit `service_secret=...` (**required** one way or the other) |

In code, names are also available as `AgentVendClient.ENV_API_URL`, `ENV_SERVICE_ID`, and `ENV_SERVICE_SECRET`. The default base URL is `AgentVendClient.DEFAULT_API_URL`.

## Requirements

Python 3.10+

## Install

```bash
pip install agentvend-service-sdk
```

HTTP features (validate, usage, gateway, progress):

```bash
pip install agentvend-service-sdk[http]
```

## Examples

### Verify inbound HMAC (agent backend)

Pass a **header map** (keys matched case-insensitively) and the **raw body** the gateway signed (same bytes as in the canonical string). Header names follow `AgentVendHeaders` (`X-AgentVend-*`). Verification defaults to signing version **v2** (newer user-context suffix, no quota segment in the signed material).

**Preferred:** verify and read user context in one step (`None` if the HMAC is invalid):

```python
from agentvend_service_sdk import verify_inbound_context

ctx = verify_inbound_context(service_secret, headers, raw_body)
if ctx is not None:
    # ctx.user_id, ctx.plan, ...
    ...
```

The former name `verify_signature_from_headers_and_get_user_context` remains available as an alias of `verify_inbound_context`.

**Or** verify and read separately:

```python
from agentvend_service_sdk import verify_signature_from_headers, get_user_context

if verify_signature_from_headers(service_secret, headers, raw_body):
    ctx = get_user_context(headers)
```

For full control, build `InboundHmacRequest` with `SignedUserContext` and call `verify_inbound_hmac(service_secret, req)`.

### Caller / backend HTTP APIs (single client)

```python
from agentvend_service_sdk import AgentVendClient, CompletionStatus

# Default API origin is production; pass api_url=... or set AGENTVEND_API_URL only to override.
# service_secret is required (here or via AGENTVEND_SERVICE_SECRET).
client = AgentVendClient(
    service_id=service_id,
    service_secret=service_secret,
)

validation = client.validate_service_key("bearer-token")
# validation.service_key_id — Core key id when present

estimate = client.estimate_usage("bearer-token", 1.0)
if estimate is not None:
    allowed = estimate.would_allow
    status = estimate.http_status

# JWT usage estimate (unsigned): bearer JWT + internal Core user id + service id
# client.estimate_usage_with_jwt(jwt, core_user_id, service_id, 1.0)

# Gateway invoke: method, service_id, endpoint_id, service_key, optional body=..., async_=...
# client.invoke_service("POST", service_id, endpoint_id, service_key, body="{}", async_=False)

usage_resp = client.report_usage(user_id, service_id, 1.0)

client.send_progress_update(progress_url, request_id, "some processing info", 50)

client.send_completion(
    callback_url, request_id, CompletionStatus.COMPLETED, units=1.0, result="some result"
)

status = client.get_request_status(request_id, service_key)
result = client.get_request_result(request_id, service_key)
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

Project metadata (name `agentvend-service-sdk`, license, URLs) lives in `pyproject.toml`.

The SDK methods shown in this README cover invoke, validate, estimates, usage reporting, and gateway polling flows.
