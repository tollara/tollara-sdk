# AgentVend SDK (PHP)

**Package:** `agentvend/service-sdk` (Packagist)

HMAC signing and **inbound gateway verification** (`AgentVend\AgentSdk\Verifier`). Use Guzzle or another HTTP client for Core, Usage, and Gateway requests.

## Configuration (base URLs)

This package does not assemble HTTP clients for you. Default production origin is **`https://api.agentvend.api`** (same as unified clients in Java/Python/JS/.NET/Rust). You may omit a configured base when your app only needs that origin; set **`AGENTVEND_API_URL`** (or your config equivalent) only to override—for example staging. Path defaults match [**MAIN-SDK-API-SPEC.md**](../docs-sdk/MAIN-SDK-API-SPEC.md) for default vs ECS layouts.

See [api-overview.md](../docs/api-overview.md).

## Environment variables (Java alignment)

This package does **not** load configuration from the environment. Use the same variable names as the Java `AgentVendClient` in your app config:

- `AGENTVEND_API_URL` — Optional. API origin; defaults to `https://api.agentvend.api` in product terms if unset.
- `AGENTVEND_AGENT_ID` — Service UUID (optional for some Core flows). Variable name remains unchanged.
- `AGENTVEND_AGENT_SECRET` — Service secret for outbound signing and inbound HMAC verification. Variable name remains unchanged.

There is no unified HTTP client in this SDK; use Guzzle or similar and the paths in [**MAIN-SDK-API-SPEC.md**](../docs-sdk/MAIN-SDK-API-SPEC.md).

### Verify HMAC and trusted user context in one call

```php
use AgentVend\AgentSdk\Verifier;

$ctx = Verifier::verifyInboundHmacAndGetUserContext($serviceSecret, $headersArray, $rawBody);
if ($ctx !== null) {
    // $ctx is trusted user context (same shape as parseUserContext)
}
```

## Install

```bash
composer require agentvend/service-sdk
```

## Inbound verification

```php
use AgentVend\AgentSdk\AgentVendHeaders;
use AgentVend\AgentSdk\InboundHmacRequest;
use AgentVend\AgentSdk\Verifier;

$valid = Verifier::verifySignatureFromHeaders($serviceSecret, $headersArray, $rawBody);

$req = new InboundHmacRequest($sig, $ts, $payload, $userId, $plan, $roles, $quotaRemaining, subscriptionActive: false);
$valid = Verifier::verifyInboundHmac($serviceSecret, $req);

$ctx = Verifier::parseUserContext($headersArray);
```

## Outbound signing

```php
use AgentVend\AgentSdk\Hmac;

$sig = Hmac::calculateHmac($data, $key);
$sig = Hmac::calculateHmacWithTimestamp($bodyJson, $timestamp, $serviceSecret);
```

## HTTP examples (Guzzle)

**Validate service key:** `POST {coreBase}/service-keys/validate`, then verify HMAC on response body + `AgentVendHeaders::TIMESTAMP` header.

**Report usage:** `POST {usageBase}/api/usage/report` with JSON body; set `AgentVendHeaders::SIGNATURE` and `TIMESTAMP` using `calculateHmacWithTimestamp`. Per MAIN-SDK §3.1: JSON `timestamp` is **ISO-8601**; header value is **Unix epoch seconds**; canonical = **`bodyJson + headerTimestamp`**.

**Progress / completion:** POST to full URLs from async invoke; sign body + timestamp from query string.

**Gateway polling:** `GET {gatewayBase}{prefix}/requests/{id}/status` with `Authorization: Bearer {serviceKey}`.

See [HMAC spec](../docs/hmac-spec.md) and [**MAIN-SDK-API-SPEC.md**](../docs-sdk/MAIN-SDK-API-SPEC.md).
