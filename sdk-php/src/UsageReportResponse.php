<?php

declare(strict_types=1);

namespace Tollara\ServiceSdk;

final class UsageReportResponse
{
    public function __construct(
        public int $reportSchemaVersion,
        public ?string $status,
        public ?string $warning,
        public ?string $userId,
        public ?string $serviceId,
        public ?string $billingModelType,
        public ?string $measurementType,
        public ?string $unitLabel,
        public ?UsageBreakdown $breakdown,
    ) {
    }

    /** @param array<string, mixed> $data */
    public static function fromArray(array $data): self
    {
        $breakdown = null;
        if (isset($data['breakdown']) && is_array($data['breakdown'])) {
            $breakdown = UsageBreakdown::fromArray($data['breakdown']);
        }
        return new self(
            reportSchemaVersion: (int) ($data['reportSchemaVersion'] ?? 0),
            status: isset($data['status']) ? (string) $data['status'] : null,
            warning: isset($data['warning']) ? (string) $data['warning'] : null,
            userId: isset($data['userId']) ? (string) $data['userId'] : null,
            serviceId: isset($data['serviceId']) ? (string) $data['serviceId'] : null,
            billingModelType: isset($data['billingModelType']) ? (string) $data['billingModelType'] : null,
            measurementType: isset($data['measurementType']) ? (string) $data['measurementType'] : null,
            unitLabel: isset($data['unitLabel']) ? (string) $data['unitLabel'] : null,
            breakdown: $breakdown,
        );
    }
}
