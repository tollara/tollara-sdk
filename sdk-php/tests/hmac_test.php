<?php

declare(strict_types=1);

require_once __DIR__ . '/../src/Hmac.php';
require_once __DIR__ . '/../src/TollaraHeaders.php';
require_once __DIR__ . '/../src/Verifier.php';

use Tollara\ServiceSdk\Verifier;

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

$ctx = Verifier::buildGatewayUserContextStringV3(
    'sub-ext-id',
    'prod-uuid-1',
    ['roleA', 'roleB'],
    'ACTIVE',
    'SUBSCRIPTION',
    'PER_REQUEST',
    'request'
);
assertSame(
    '3sub-ext-idprod-uuid-1roleA,roleBACTIVESUBSCRIPTIONPER_REQUESTrequest',
    $ctx,
    'buildV3 all fields'
);

$ctx = Verifier::buildGatewayUserContextStringV3('user-1', 'prod-1', [], 'TRIAL', '', '', '');
assertSame('3user-1prod-1TRIAL', $ctx, 'buildV3 empty roles');

$ctx = Verifier::buildGatewayUserContextStringV3('owner-id', '', [], 'ACTIVE', '', '', '');
assertSame('3owner-idACTIVE', $ctx, 'buildV3 billing absent');

$ctx = Verifier::buildGatewayUserContextStringV3('user-x', 'prod-x', ['r1'], 'EXPIRED', 'PREPAID', 'PER_REQUEST', 'request');
assertSame('3user-xprod-xr1EXPIREDPREPAIDPER_REQUESTrequest', $ctx, 'buildV3 non access');

assertTrue(Verifier::grantsAccess('ACTIVE'), 'grants ACTIVE');
assertTrue(Verifier::grantsAccess('trial'), 'grants trial case-insensitive');
assertTrue(Verifier::grantsAccess('CANCELLING_PENDING'), 'grants CANCELLING_PENDING');
assertTrue(!Verifier::grantsAccess('EXPIRED'), 'denies EXPIRED');
assertTrue(!Verifier::grantsAccess(null), 'denies null');

$secret = 'my-agent-secret';
$payload = '';
$ts = '1700000000';
$userCtx = Verifier::buildGatewayUserContextStringV3(
    'user1',
    'prod-1',
    ['role1', 'role2'],
    'ACTIVE',
    'SUBSCRIPTION',
    'PER_REQUEST',
    'request'
);
$sig = \Tollara\AgentSdk\Hmac::calculateHmac($payload . $ts . $userCtx, $secret);
$headers = [
    'X-Tollara-Signature' => $sig,
    'X-Tollara-Timestamp' => $ts,
    'X-Tollara-Signing-Version' => '3',
    'X-Tollara-User-ID' => 'user1',
    'X-Tollara-Service-Product-ID' => 'prod-1',
    'X-Tollara-Roles' => 'role1,role2',
    'X-Tollara-Subscription-Status' => 'ACTIVE',
    'X-Tollara-Billing-Model' => 'SUBSCRIPTION',
    'X-Tollara-Measurement-Type' => 'PER_REQUEST',
    'X-Tollara-Unit-Label' => 'request',
];
assertTrue(Verifier::verifySignatureFromHeaders($secret, $headers, $payload), 'v3 verify from headers');
$ctx = Verifier::verifyInboundHmacAndGetUserContext($secret, $headers, $payload);
assertTrue($ctx !== null && $ctx->serviceProductId === 'prod-1', 'v3 user context');

echo "HMAC v3 tests passed\n";
