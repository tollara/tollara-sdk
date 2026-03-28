# AgentVend SDK (PHP)

**Package:** `agentvend/agent-sdk` (Packagist)

HMAC signing and **inbound gateway verification** (`AgentVend\AgentSdk\Verifier`). Use Guzzle or another HTTP client for Core, Usage, and Gateway requests.

## Configuration (base URLs)

Hosts and path prefixes are **your responsibility**—nothing is hardcoded in the library. Match [sdk-api-spec.md](../docs/sdk-api-spec.md) for default vs ECS paths.

See [api-overview.md](../docs/api-overview.md).

## Install

```bash
composer require agentvend/agent-sdk
```

## Inbound verification

```php
use AgentVend\AgentSdk\AgentVendHeaders;
use AgentVend\AgentSdk\InboundHmacRequest;
use AgentVend\AgentSdk\Verifier;

$valid = Verifier::verifySignatureFromHeaders($agentSecret, $headersArray, $rawBody);

$req = new InboundHmacRequest($sig, $ts, $payload, $userId, $plan, $roles, $quotaRemaining, subscriptionActive: false);
$valid = Verifier::verifyInboundHmac($agentSecret, $req);

$ctx = Verifier::parseUserContext($headersArray);
```

## Outbound signing

```php
use AgentVend\AgentSdk\Hmac;

$sig = Hmac::calculateHmac($data, $key);
$sig = Hmac::calculateHmacWithTimestamp($bodyJson, $timestamp, $agentSecret);
```

## HTTP examples (Guzzle)

**Validate:** `POST {coreBase}/agent-keys/validate`, then verify HMAC on response body + `AgentVendHeaders::TIMESTAMP` header.

**Report usage:** `POST {usageBase}/api/usage/report` with JSON body; set `AgentVendHeaders::SIGNATURE` and `TIMESTAMP` using `calculateHmacWithTimestamp`.

**Progress / completion:** POST to full URLs from async invoke; sign body + timestamp from query string.

**Gateway polling:** `GET {gatewayBase}{prefix}/requests/{id}/status` with `Authorization: Bearer {agentKey}`.

See [HMAC spec](../docs/hmac-spec.md) and [API spec](../docs/sdk-api-spec.md).
