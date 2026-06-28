# Tollara SDK (PHP)

**Package:** `tollara/service-sdk` (Packagist), version **3.0.0**

HMAC signing, **inbound gateway verification**, and a **`TollaraClient`** for validate/estimate/invoke/usage/progress/completion/gateway polling.

## Configuration (base URLs)

Use the Tollara API origin **`https://api.tollara.ai`** by default. Set **`TOLLARA_API_URL`** only when you need a non-production override.

## Environment variables

- `TOLLARA_API_URL` — Optional API origin override
- `TOLLARA_SERVICE_ID` — Service UUID (optional for some Core flows)
- `TOLLARA_SERVICE_SECRET` — Service secret for outbound signing and inbound HMAC verification

### Verify HMAC and trusted user context in one call

Verification uses HMAC user-context **v3** when `X-Tollara-Signing-Version` is `"3"` (`serviceProductId`, `subscriptionStatus`); **v2** when `"2"`; legacy v1 otherwise.

```php
use Tollara\ServiceSdk\Verifier;

$ctx = Verifier::verifyInboundHmacAndGetUserContext($serviceSecret, $headersArray, $rawBody);
if ($ctx !== null && Verifier::grantsAccess($ctx->subscriptionStatus)) {
    // invoke-eligible subscription
}
```

## Install

```bash
composer require tollara/service-sdk
```

## Inbound verification (v3)

```php
use Tollara\ServiceSdk\InboundHmacRequest;
use Tollara\ServiceSdk\Verifier;

$valid = Verifier::verifySignatureFromHeaders($serviceSecret, $headersArray, $rawBody);

$req = new InboundHmacRequest(
    $sig,
    $ts,
    $payload,
    userId: 'user1',
    serviceProductId: 'prod-uuid-1',
    roles: ['r1', 'r2'],
    subscriptionStatus: 'ACTIVE',
    signingVersion: '3',
);
$valid = Verifier::verifyInboundHmac($serviceSecret, $req);

$ctx = Verifier::parseUserContext($headersArray);
```

## Tollara client example

```php
use Tollara\ServiceSdk\TollaraClient;

$client = new TollaraClient(
    serviceId: $serviceId,
    serviceSecret: $serviceSecret
);

$validation = $client->validateServiceKey($serviceKey); // validationSchemaVersion 3
if ($validation !== null && $validation->grantsAccess()) { /* ... */ }

$estimate = $client->estimateUsage($serviceKey, 1.0); // estimateSchemaVersion 3; caps/credits on breakdown
$report = $client->reportUsage($userId, $serviceId, 1.0); // reportSchemaVersion 2 + breakdown
```

See this README for public SDK usage details.
