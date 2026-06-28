<?php

declare(strict_types=1);

require_once __DIR__ . '/../src/UsageBreakdown.php';
require_once __DIR__ . '/../src/ServiceKeyValidationResult.php';
require_once __DIR__ . '/../src/UsageEstimateResult.php';
require_once __DIR__ . '/../src/UsageReportResponse.php';

use Tollara\ServiceSdk\ServiceKeyValidationResult;
use Tollara\ServiceSdk\UsageBreakdown;
use Tollara\ServiceSdk\UsageEstimateResult;
use Tollara\ServiceSdk\UsageReportResponse;

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

$validate = ServiceKeyValidationResult::fromArray([
    'valid' => true,
    'userId' => 'user-1',
    'serviceId' => 'svc-1',
    'serviceKeyId' => 'key-1',
    'serviceProductId' => 'prod-1',
    'roles' => ['USER'],
    'subscriptionStatus' => 'ACTIVE',
    'validationSchemaVersion' => 3,
    'billingModelType' => 'SUBSCRIPTION',
    'measurementType' => 'PER_REQUEST',
    'unitLabel' => 'request',
]);
assertSame('prod-1', $validate->serviceProductId, 'validate serviceProductId');
assertSame(3, $validate->validationSchemaVersion, 'validate schema version');
assertTrue($validate->grantsAccess(), 'validate grantsAccess ACTIVE');

$estimate = UsageEstimateResult::fromArray([
    'sufficientCredits' => true,
    'wouldExceedCap' => false,
    'wouldAllow' => true,
    'estimatedCost' => 0.1,
    'billingModelType' => 'SUBSCRIPTION',
    'measurementType' => 'PER_REQUEST',
    'unitLabel' => 'request',
    'breakdown' => [
        'unitsRemaining' => 199,
        'remainingSpendingCap' => 20,
        'isOverLimit' => false,
    ],
    'estimateSchemaVersion' => 3,
    'timestamp' => 1700000000,
], 200);
assertSame(3, $estimate->estimateSchemaVersion, 'estimate schema version');
assertTrue($estimate->breakdown !== null, 'estimate breakdown');
assertSame(20.0, $estimate->breakdown->remainingSpendingCap, 'estimate breakdown cap');

$report = UsageReportResponse::fromArray([
    'reportSchemaVersion' => 2,
    'status' => 'ok',
    'userId' => 'user-1',
    'serviceId' => 'svc-1',
    'billingModelType' => 'SUBSCRIPTION',
    'measurementType' => 'PER_REQUEST',
    'unitLabel' => 'request',
    'breakdown' => [
        'unitsUsed' => 1,
        'unitsRemaining' => 99,
        'isOverLimit' => false,
    ],
]);
assertSame(2, $report->reportSchemaVersion, 'report schema version');
assertTrue($report->breakdown !== null, 'report breakdown');
assertSame(99.0, $report->breakdown->unitsRemaining, 'report units remaining');

echo "Contract tests passed\n";
