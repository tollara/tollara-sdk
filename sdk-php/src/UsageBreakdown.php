<?php

declare(strict_types=1);

namespace Tollara\AgentSdk;

/**
 * Shared usage breakdown for estimate and report responses.
 */
final class UsageBreakdown
{
    public function __construct(
        public ?float $unitsUsed = null,
        public ?float $baseUnitsUsed = null,
        public ?float $overageUnits = null,
        public ?float $chargeableOverageUnits = null,
        public ?float $surplusOverageUnits = null,
        public ?float $overageCost = null,
        public ?float $totalOverageCost = null,
        public ?float $unitsRemaining = null,
        public ?float $remainingCredits = null,
        public ?float $remainingSpendingCap = null,
        public ?float $totalUnitsUsedThisCycle = null,
        public ?bool $overLimit = null,
        public ?bool $overage = null,
        public ?bool $overageAllowed = null,
    ) {
    }

    /** @param array<string, mixed> $data */
    public static function fromArray(array $data): self
    {
        return new self(
            unitsUsed: isset($data['unitsUsed']) ? (float) $data['unitsUsed'] : null,
            baseUnitsUsed: isset($data['baseUnitsUsed']) ? (float) $data['baseUnitsUsed'] : null,
            overageUnits: isset($data['overageUnits']) ? (float) $data['overageUnits'] : null,
            chargeableOverageUnits: isset($data['chargeableOverageUnits']) ? (float) $data['chargeableOverageUnits'] : null,
            surplusOverageUnits: isset($data['surplusOverageUnits']) ? (float) $data['surplusOverageUnits'] : null,
            overageCost: isset($data['overageCost']) ? (float) $data['overageCost'] : null,
            totalOverageCost: isset($data['totalOverageCost']) ? (float) $data['totalOverageCost'] : null,
            unitsRemaining: isset($data['unitsRemaining']) ? (float) $data['unitsRemaining'] : null,
            remainingCredits: isset($data['remainingCredits']) ? (float) $data['remainingCredits'] : null,
            remainingSpendingCap: isset($data['remainingSpendingCap']) ? (float) $data['remainingSpendingCap'] : null,
            totalUnitsUsedThisCycle: isset($data['totalUnitsUsedThisCycle']) ? (float) $data['totalUnitsUsedThisCycle'] : null,
            overLimit: isset($data['isOverLimit']) ? (bool) $data['isOverLimit'] : null,
            overage: isset($data['isOverage']) ? (bool) $data['isOverage'] : null,
            overageAllowed: isset($data['isOverageAllowed']) ? (bool) $data['isOverageAllowed'] : null,
        );
    }
}
