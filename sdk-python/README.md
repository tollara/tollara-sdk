# Tollara Service SDK (Python)

**Package:** `tollara-service-sdk` (PyPI), version **3.0.0**. **Import:** `import tollara_service_sdk`.

Verify HMAC on incoming gateway requests, validate service keys, run usage pre-flight (service-key **and** JWT paths), **gateway invoke**, report usage, progress/completion, and poll async job status on the gateway.

This README covers the public SDK contract and usage examples.

## Configuration

### Recommended: `TollaraClient`

Use **`TollaraClient`** with one API **origin** (scheme + host, optional port).

| Setting | Default | Notes |
|--------|---------|--------|
| API origin | **`https://api.tollara.ai`** (`TollaraClient.DEFAULT_API_URL`) | Override with `api_url=...`, or env **`TOLLARA_API_URL`** for staging/tests — no trailing slash required |
| Service identity | From env **`TOLLARA_SERVICE_ID`**, or `service_id=...` | Optional if Core can infer the service from the key |
| Service secret | From env **`TOLLARA_SERVICE_SECRET`**, or `service_secret=...` | **Required** (Usage HMAC + Core response verification) |

**Progress / completion** still use the **full** `progress_url` / `callback_url` strings from the gateway (including query params).

**Usage report (§3):** JSON body includes an ISO-8601 **`timestamp`**; **`X-Tollara-Timestamp`** is **Unix epoch seconds** for signing. For `report_usage_at`, pass `timestamp` as epoch **seconds** (or omit for “now”); values above `1e11` are treated as milliseconds and converted.

**Progress / completion:** sign exactly the bytes you POST. The usage service verifies HMAC against the **raw HTTP request body** (spec §3). Callbacks return **`UsageCallbackResult`** (`success`, `http_status`, `http_status_text`, `request_url`, optional `response_body` / `network_error`).

Constructor arguments override environment variables when both are set.

### Environment variables

| Variable | Purpose |
|----------|---------|
| **`TOLLARA_API_URL`** | Optional. Overrides the default production API origin when set (staging, local stacks, tests). |
| **`TOLLARA_SERVICE_ID`** | Service UUID if you omit `service_id=...` (optional) |
| **`TOLLARA_SERVICE_SECRET`** | Service secret if you omit `service_secret=...` (**required** one way or the other) |

In code, names are also available as `TollaraClient.ENV_API_URL`, `ENV_SERVICE_ID`, and `ENV_SERVICE_SECRET`. The default base URL is `TollaraClient.DEFAULT_API_URL`.

## Requirements

Python 3.10+

## Install

```bash
pip install tollara-service-sdk
```

HTTP features (validate, usage, gateway, progress):

```bash
pip install tollara-service-sdk[http]
```

## Examples

### Verify inbound HMAC (agent backend)

Pass a **header map** (keys matched case-insensitively) and the **raw body** the gateway signed (same bytes as in the canonical string). Header names follow `TollaraHeaders` (`X-Tollara-*`). Verification uses HMAC user-context **v3** when `X-Tollara-Signing-Version` is `"3"` (`serviceProductId`, `subscriptionStatus`); **v2** when `"2"`; legacy v1 when the header is absent.

**Preferred:** verify and read user context in one step (`None` if the HMAC is invalid):

```python
from tollara_service_sdk import verify_inbound_context, grants_access

ctx = verify_inbound_context(service_secret, headers, raw_body)
if ctx is not None and grants_access(ctx.subscription_status):
    # ctx.user_id, ctx.service_product_id, ctx.subscription_status, ...
    ...
```

The former name `verify_signature_from_headers_and_get_user_context` remains available as an alias of `verify_inbound_context`.

**Or** verify and read separately:

```python
from tollara_service_sdk import verify_signature_from_headers, get_user_context

if verify_signature_from_headers(service_secret, headers, raw_body):
    ctx = get_user_context(headers)
```

For full control, build `InboundHmacRequest` with `SignedUserContext` and call `verify_inbound_hmac(service_secret, req)`.

### Caller / backend HTTP APIs (single client)

```python
from tollara_service_sdk import TollaraClient, CompletionStatus

# Default API origin is production; pass api_url=... or set TOLLARA_API_URL only to override.
# service_secret is required (here or via TOLLARA_SERVICE_SECRET).
client = TollaraClient(
    service_id=service_id,
    service_secret=service_secret,
)

validation = client.validate_service_key("bearer-token")
# validation.service_product_id, validation.subscription_status, validation.grants_access()

estimate = client.estimate_usage("bearer-token", 1.0)
if estimate is not None:
    allowed = estimate.would_allow
    # estimate.breakdown.remaining_credits / remaining_spending_cap when applicable
    status = estimate.http_status

# JWT usage estimate (unsigned): bearer JWT + internal Core user id + service id
# client.estimate_usage_with_jwt(jwt, core_user_id, service_id, 1.0)

# Gateway invoke: method, service_id, endpoint_id, service_key, optional body=..., async_=...
# client.invoke_service("POST", service_id, endpoint_id, service_key, body="{}", async_=False)

usage_resp = client.report_usage(user_id, service_id, 1.0)

progress = client.send_progress_update(progress_url, request_id, "some processing info", 50)
if not progress.success:
    print(progress.http_status, progress.response_body)

complete = client.send_completion(
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

Project metadata (name `tollara-service-sdk`, license, URLs) lives in `pyproject.toml`.

The SDK methods shown in this README cover invoke, validate, estimates, usage reporting, and gateway polling flows.
