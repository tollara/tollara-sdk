<?php

declare(strict_types=1);

namespace Tollara\ServiceSdk;

final class ServiceKeyValidationResult
{
    /**
     * @param list<string> $roles
     */
    public function __construct(
        public ?string $userId,
        public ?string $serviceId,
        public ?string $serviceKeyId,
        public ?string $serviceProductId,
        public array $roles,
        public ?string $subscriptionStatus,
        public int $validationSchemaVersion,
        public ?string $billingModelType,
        public ?string $measurementType,
        public ?string $unitLabel,
    ) {
    }

    public function grantAccess(): bool
    {
        return Verifier::grantAccess($this->subscriptionStatus);
    }

    /** @param array<string, mixed> $data */
    public static function fromArray(array $data): self
    {
        $roles = $data['roles'] ?? [];
        if (!is_array($roles)) {
            $roles = [];
        }
        return new self(
            userId: isset($data['userId']) ? (string) $data['userId'] : null,
            serviceId: isset($data['serviceId']) ? (string) $data['serviceId'] : null,
            serviceKeyId: isset($data['serviceKeyId']) ? (string) $data['serviceKeyId'] : null,
            serviceProductId: isset($data['serviceProductId']) ? (string) $data['serviceProductId'] : null,
            roles: array_values(array_map('strval', $roles)),
            subscriptionStatus: isset($data['subscriptionStatus']) ? (string) $data['subscriptionStatus'] : null,
            validationSchemaVersion: (int) ($data['validationSchemaVersion'] ?? 0),
            billingModelType: isset($data['billingModelType']) ? (string) $data['billingModelType'] : null,
            measurementType: isset($data['measurementType']) ? (string) $data['measurementType'] : null,
            unitLabel: isset($data['unitLabel']) ? (string) $data['unitLabel'] : null,
        );
    }
}
