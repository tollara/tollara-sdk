<?php

declare(strict_types=1);

namespace Tollara\ServiceSdk;

final class UsageEstimateResult
{
    public function __construct(
        public bool $sufficientCredits,
        public bool $wouldExceedCap,
        public bool $wouldAllow,
        public ?float $estimatedCost,
        public ?string $billingModelType,
        public ?string $measurementType,
        public ?string $unitLabel,
        public ?UsageBreakdown $breakdown,
        public int $estimateSchemaVersion,
        public int $timestamp,
        public int $httpStatus,
    ) {
    }

    /** @param array<string, mixed> $data */
    public static function fromArray(array $data, int $httpStatus): self
    {
        $breakdown = null;
        if (isset($data['breakdown']) && is_array($data['breakdown'])) {
            $breakdown = UsageBreakdown::fromArray($data['breakdown']);
        }
        return new self(
            sufficientCredits: (bool) ($data['sufficientCredits'] ?? false),
            wouldExceedCap: (bool) ($data['wouldExceedCap'] ?? false),
            wouldAllow: (bool) ($data['wouldAllow'] ?? false),
            estimatedCost: isset($data['estimatedCost']) ? (float) $data['estimatedCost'] : null,
            billingModelType: isset($data['billingModelType']) ? (string) $data['billingModelType'] : null,
            measurementType: isset($data['measurementType']) ? (string) $data['measurementType'] : null,
            unitLabel: isset($data['unitLabel']) ? (string) $data['unitLabel'] : null,
            breakdown: $breakdown,
            estimateSchemaVersion: (int) ($data['estimateSchemaVersion'] ?? 0),
            timestamp: (int) ($data['timestamp'] ?? 0),
            httpStatus: $httpStatus,
        );
    }
}
