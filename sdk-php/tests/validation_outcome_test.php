<?php

declare(strict_types=1);

require_once __DIR__ . '/../src/UsageBreakdown.php';
require_once __DIR__ . '/../src/ServiceKeyValidationResult.php';
require_once __DIR__ . '/../src/ValidationFailureCode.php';
require_once __DIR__ . '/../src/ServiceKeyValidationFailure.php';
require_once __DIR__ . '/../src/ServiceKeyValidationOutcome.php';
require_once __DIR__ . '/../src/Hmac.php';
require_once __DIR__ . '/../src/TollaraHeaders.php';
require_once __DIR__ . '/../src/ValidationClient.php';

use Tollara\ServiceSdk\Hmac;
use Tollara\ServiceSdk\TollaraHeaders;
use Tollara\ServiceSdk\ValidationClient;
use Tollara\ServiceSdk\ValidationFailureCode;

function assertTrue(bool $condition, string $message): void
{
    if (!$condition) {
        throw new RuntimeException($message);
    }
}

function assertSame(mixed $expected, mixed $actual, string $message): void
{
    if ($expected !== $actual) {
        throw new RuntimeException(
            $message . ': expected ' . var_export($expected, true) . ', got ' . var_export($actual, true)
        );
    }
}

$secret = 'secret';

$missing = ValidationClient::outcomeFromValidateResponse(401, 'unauthorized', [], $secret, null);
assertTrue(!$missing->ok, '401 without json is failure');
assertSame(ValidationFailureCode::HTTP_ERROR, $missing->failure?->code, '401 without json is HTTP_ERROR');
assertSame(401, $missing->failure?->httpStatus, '401 without json http status');

$invalid = ValidationClient::outcomeFromValidateResponse(
    401,
    json_encode(['valid' => false, 'error' => 'Invalid service key']),
    [],
    $secret,
    null,
);
assertTrue(!$invalid->ok, 'unsigned 401 invalid key is failure');
assertSame(ValidationFailureCode::INVALID_KEY, $invalid->failure?->code, 'unsigned 401 code');
assertSame('Invalid service key', $invalid->failure?->message, 'unsigned 401 message');
assertSame(401, $invalid->failure?->httpStatus, 'unsigned 401 status');

$body = json_encode([
    'valid' => false,
    'error' => 'Internal server error',
]);
$serverError = ValidationClient::outcomeFromValidateResponse(500, $body, [], $secret, null);
assertSame(ValidationFailureCode::HTTP_ERROR, $serverError->failure?->code, '500 valid:false is HTTP_ERROR');

$signedBody = json_encode([
    'valid' => false,
    'error' => 'Key expired',
], JSON_UNESCAPED_SLASHES);
$ts = '1700000000';
$sig = Hmac::calculateHmac($signedBody . $ts, $secret);
$signedInvalid = ValidationClient::outcomeFromValidateResponse(
    200,
    $signedBody,
    [
        TollaraHeaders::SIGNATURE => $sig,
        TollaraHeaders::TIMESTAMP => $ts,
    ],
    $secret,
    null,
);
assertSame(ValidationFailureCode::INVALID_KEY, $signedInvalid->failure?->code, 'signed invalid key');
assertSame('Key expired', $signedInvalid->failure?->message, 'signed invalid message');

$successBody = json_encode([
    'valid' => true,
    'userId' => 'user-123',
    'serviceId' => 'svc-1',
    'serviceProductId' => 'prod-1',
    'roles' => ['user'],
    'subscriptionStatus' => 'ACTIVE',
    'validationSchemaVersion' => 3,
], JSON_UNESCAPED_SLASHES);
$successSig = Hmac::calculateHmac($successBody . $ts, $secret);
$success = ValidationClient::outcomeFromValidateResponse(
    200,
    $successBody,
    [
        TollaraHeaders::SIGNATURE => $successSig,
        TollaraHeaders::TIMESTAMP => $ts,
    ],
    $secret,
    'svc-fallback',
);
assertTrue($success->ok, 'signed success');
assertSame('user-123', $success->result?->userId, 'success userId');
assertTrue($success->result?->grantAccess(), 'success grantAccess');

echo "Validation outcome tests passed\n";
