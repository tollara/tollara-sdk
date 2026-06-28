<?php

declare(strict_types=1);

require_once __DIR__ . '/../src/Hmac.php';
require_once __DIR__ . '/../src/TollaraHeaders.php';
require_once __DIR__ . '/../src/UsageCallbackResult.php';
require_once __DIR__ . '/../src/TollaraClient.php';

use Tollara\ServiceSdk\TollaraClient;
use Tollara\ServiceSdk\UsageCallbackResult;

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

$client = new TollaraClient(apiUrl: 'http://usage.test', serviceSecret: 'secret');

$result = $client->sendProgressUpdate('', 'req-1', 'processing', 25);
assertTrue($result instanceof UsageCallbackResult, 'progress returns UsageCallbackResult');
assertTrue(!$result->success, 'missing url fails');
assertSame(0, $result->httpStatus, 'missing url status');
assertSame('Missing or invalid callback/progress URL', $result->httpStatusText, 'missing url message');

$result = $client->sendProgressUpdate('http://usage.test/api/usage/progress/req-1', 'req-1', 'processing', 25);
assertTrue(!$result->success, 'missing timestamp fails');
assertSame(0, $result->httpStatus, 'missing timestamp status');
assertSame('Missing timestamp query parameter in URL', $result->httpStatusText, 'missing timestamp message');

$result = $client->sendCompletion('http://usage.test/api/usage/complete/req-1', 'req-1', 'COMPLETED', 1.0);
assertTrue(!$result->success, 'completion missing timestamp fails');
assertSame(0, $result->httpStatus, 'completion missing timestamp status');

echo "UsageClient tests passed\n";
