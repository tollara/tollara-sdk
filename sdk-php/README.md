# AgentVend SDK (PHP)

**Package:** `agentvend/service-sdk` (Packagist)

HMAC signing, **inbound gateway verification** (`AgentVend\AgentSdk\Verifier`), and an `AgentVendClient` for validate/estimate/invoke/usage/progress/completion/gateway polling.

## Configuration (base URLs)

Use the AgentVend API origin **`https://api.agentvend.api`** by default. You may omit a configured base when your app only needs that origin; set **`AGENTVEND_API_URL`** (or your config equivalent) only to override—for example staging.

Use this README as the public usage reference.

## Environment variables (Java alignment)

Use these environment variable names in your app config:

- `AGENTVEND_API_URL` — Optional. API origin; defaults to `https://api.agentvend.api` in product terms if unset.
- `AGENTVEND_SERVICE_ID` — Service UUID (optional for some Core flows).
- `AGENTVEND_SERVICE_SECRET` — Service secret for outbound signing and inbound HMAC verification.

`AgentVendClient` uses HTTP requests directly and supports advanced configuration options when needed.

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

## AgentVend client example

```php
use AgentVend\AgentSdk\AgentVendClient;

$client = new AgentVendClient(
    serviceId: $serviceId,
    serviceSecret: $serviceSecret
);

$client->validateServiceKey($serviceKey);
$client->estimateUsage($serviceKey, 1.0);
$client->estimateUsageWithJwt($bearerJwt, $coreUserId, $serviceId, 1.0);
$client->invokeService('POST', $serviceId, $endpointId, $serviceKey, '{}', false);
$client->reportUsage($userId, $serviceId, 1.0);
$client->sendProgressUpdate($progressUrl, $requestId, 'processing', 50);
$client->sendCompletion($callbackUrl, $requestId, 'COMPLETED', 1.0);
$client->getRequestStatus($requestId, $serviceKey);
```

See this README for public SDK usage details.
