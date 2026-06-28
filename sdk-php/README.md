# Tollara SDK (PHP)

**Package:** `tollara/service-sdk` (Packagist)

HMAC signing, **inbound gateway verification** (`Tollara\AgentSdk\Verifier`), and an `TollaraClient` for validate/estimate/invoke/usage/progress/completion/gateway polling.

## Configuration (base URLs)

Use the Tollara API origin **`https://api.tollara.ai`** by default. You may omit a configured base when your app only needs that origin; set **`TOLLARA_API_URL`** (or your config equivalent) only to override—for example staging.

Use this README as the public usage reference.

## Environment variables (Java alignment)

Use these environment variable names in your app config:

- `TOLLARA_API_URL` — Optional. API origin; defaults to `https://api.tollara.ai` in product terms if unset.
- `TOLLARA_SERVICE_ID` — Service UUID (optional for some Core flows).
- `TOLLARA_SERVICE_SECRET` — Service secret for outbound signing and inbound HMAC verification.

`TollaraClient` uses HTTP requests directly and supports advanced configuration options when needed.

### Verify HMAC and trusted user context in one call

Verification uses HMAC user-context **v3** when `X-Tollara-Signing-Version` is `"3"`; **v2** when `"2"`; legacy v1 otherwise.

```php
use Tollara\AgentSdk\Verifier;

$ctx = Verifier::verifyInboundHmacAndGetUserContext($serviceSecret, $headersArray, $rawBody);
if ($ctx !== null && Verifier::grantsAccess($ctx->subscriptionStatus)) {
    // invoke-eligible subscription
}
```

## Install

```bash
composer require tollara/service-sdk
```

## Inbound verification

```php
use Tollara\AgentSdk\TollaraHeaders;
use Tollara\AgentSdk\InboundHmacRequest;
use Tollara\AgentSdk\Verifier;

$valid = Verifier::verifySignatureFromHeaders($serviceSecret, $headersArray, $rawBody);

$req = new InboundHmacRequest($sig, $ts, $payload, $userId, $plan, $roles, $quotaRemaining, subscriptionActive: false);
$valid = Verifier::verifyInboundHmac($serviceSecret, $req);

$ctx = Verifier::parseUserContext($headersArray);
```

## Outbound signing

```php
use Tollara\AgentSdk\Hmac;

$sig = Hmac::calculateHmac($data, $key);
$sig = Hmac::calculateHmacWithTimestamp($bodyJson, $timestamp, $serviceSecret);
```

## Tollara client example

```php
use Tollara\AgentSdk\TollaraClient;

$client = new TollaraClient(
    serviceId: $serviceId,
    serviceSecret: $serviceSecret
);

$client->validateServiceKey($serviceKey); // validationSchemaVersion 3
$client->estimateUsage($serviceKey, 1.0); // estimateSchemaVersion 3 breakdown
$client->reportUsage($userId, $serviceId, 1.0); // reportSchemaVersion 2 breakdown
$result = $client->sendProgressUpdate($progressUrl, $requestId, 'processing', 50);
if (!$result->success) { /* handle callback failure */ }
$complete = $client->sendCompletion($callbackUrl, $requestId, 'COMPLETED', 1.0);
if (!$complete->success) { /* handle callback failure */ }
$client->getRequestStatus($requestId, $serviceKey);
```

See this README for public SDK usage details.
