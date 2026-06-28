from dataclasses import dataclass
from typing import Any, Dict, Optional


@dataclass
class UsageBreakdown:
    units_used: Optional[float] = None
    base_units_used: Optional[float] = None
    overage_units: Optional[float] = None
    chargeable_overage_units: Optional[float] = None
    surplus_overage_units: Optional[float] = None
    overage_cost: Optional[float] = None
    total_overage_cost: Optional[float] = None
    units_remaining: Optional[float] = None
    remaining_credits: Optional[float] = None
    remaining_spending_cap: Optional[float] = None
    total_units_used_this_cycle: Optional[float] = None
    over_limit: Optional[bool] = None
    overage: Optional[bool] = None
    overage_allowed: Optional[bool] = None


def _opt_float(v: Any) -> Optional[float]:
    if v is None:
        return None
    if isinstance(v, (int, float)):
        return float(v)
    return None


def parse_usage_breakdown(data: Optional[Dict[str, Any]]) -> Optional[UsageBreakdown]:
    if data is None or not isinstance(data, dict):
        return None
    return UsageBreakdown(
        units_used=_opt_float(data.get("unitsUsed")),
        base_units_used=_opt_float(data.get("baseUnitsUsed")),
        overage_units=_opt_float(data.get("overageUnits")),
        chargeable_overage_units=_opt_float(data.get("chargeableOverageUnits")),
        surplus_overage_units=_opt_float(data.get("surplusOverageUnits")),
        overage_cost=_opt_float(data.get("overageCost")),
        total_overage_cost=_opt_float(data.get("totalOverageCost")),
        units_remaining=_opt_float(data.get("unitsRemaining")),
        remaining_credits=_opt_float(data.get("remainingCredits")),
        remaining_spending_cap=_opt_float(data.get("remainingSpendingCap")),
        total_units_used_this_cycle=_opt_float(data.get("totalUnitsUsedThisCycle")),
        over_limit=data.get("isOverLimit") if isinstance(data.get("isOverLimit"), bool) else None,
        overage=data.get("isOverage") if isinstance(data.get("isOverage"), bool) else None,
        overage_allowed=data.get("isOverageAllowed")
        if isinstance(data.get("isOverageAllowed"), bool)
        else None,
    )
