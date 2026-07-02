/** Shared usage breakdown for estimate and report responses (MAIN-SDK-API-SPEC). */
export interface UsageBreakdown {
  unitsUsed?: number;
  baseUnitsUsed?: number;
  overageUnits?: number;
  chargeableOverageUnits?: number;
  surplusOverageUnits?: number;
  overageCost?: number;
  totalOverageCost?: number;
  unitsRemaining?: number;
  /** PREPAID: credit balance after this chunk. */
  remainingCredits?: number;
  remainingSpendingCap?: number;
  totalUnitsUsedThisCycle?: number;
  isOverLimit?: boolean;
  isOverage?: boolean;
  isOverageAllowed?: boolean;
}

export function parseUsageBreakdown(raw: unknown): UsageBreakdown | null {
  if (raw == null || typeof raw !== 'object' || Array.isArray(raw)) return null;
  const o = raw as Record<string, unknown>;
  const num = (k: string) => (typeof o[k] === 'number' ? (o[k] as number) : undefined);
  const bool = (k: string) => (typeof o[k] === 'boolean' ? (o[k] as boolean) : undefined);
  return {
    unitsUsed: num('unitsUsed'),
    baseUnitsUsed: num('baseUnitsUsed'),
    overageUnits: num('overageUnits'),
    chargeableOverageUnits: num('chargeableOverageUnits'),
    surplusOverageUnits: num('surplusOverageUnits'),
    overageCost: num('overageCost'),
    totalOverageCost: num('totalOverageCost'),
    unitsRemaining: num('unitsRemaining'),
    remainingCredits: num('remainingCredits'),
    remainingSpendingCap: num('remainingSpendingCap'),
    totalUnitsUsedThisCycle: num('totalUnitsUsedThisCycle'),
    isOverLimit: bool('isOverLimit'),
    isOverage: bool('isOverage'),
    isOverageAllowed: bool('isOverageAllowed'),
  };
}
