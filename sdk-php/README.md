# Agent Hub SDK (PHP)

**Package:** `marketplace/agent-sdk` (Packagist)

Verify HMAC, sign outbound requests. Add Guzzle for validate/report.

## Install

```bash
composer require marketplace/agent-sdk
```

## Example

```php
use Marketplace\AgentSdk\Hmac;

$sig = Hmac::calculateHmac($data, $key);
$valid = Hmac::validateHmacSignature($signature, $payloadString, $key);
```

See [HMAC spec](../docs/hmac-spec.md) and [API overview](../docs/api-overview.md).
