<?php

declare(strict_types=1);

require_once __DIR__ . '/../src/PathPrefixes.php';
require_once __DIR__ . '/../src/TollaraClient.php';

use Tollara\ServiceSdk\PathPrefixes;
use Tollara\ServiceSdk\TollaraClient;

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

assertTrue(PathPrefixes::isHostedTollaraApiOrigin('https://api.tollara.ai'), 'prod hosted');
assertTrue(PathPrefixes::isHostedTollaraApiOrigin('https://acme.api.tollara.ai'), 'branded prod hosted');
assertTrue(!PathPrefixes::isHostedTollaraApiOrigin('http://host.docker.internal:8083'), 'docker not hosted');

assertSame(
    PathPrefixes::ECS_GATEWAY_PATH_PREFIX,
    PathPrefixes::resolveGatewayPathPrefix('https://api.tollara.ai'),
    'hosted gateway prefix'
);
assertSame(
    TollaraClient::DEFAULT_GATEWAY_PATH_PREFIX,
    PathPrefixes::resolveGatewayPathPrefix('http://host.docker.internal:8083'),
    'docker gateway prefix'
);
assertSame(
    '/api',
    PathPrefixes::resolveGatewayPathPrefix('https://api.tollara.ai', '/api'),
    'explicit override'
);

assertSame(
    PathPrefixes::ECS_CORE_PATH_PREFIX,
    PathPrefixes::resolveCorePathPrefix('https://api.tollara.ai'),
    'hosted core prefix'
);
assertSame(
    PathPrefixes::ECS_USAGE_PATH_PREFIX,
    PathPrefixes::resolveUsagePathPrefix('https://api.tollara.ai'),
    'hosted usage prefix'
);

echo "path_prefixes_test: OK\n";
