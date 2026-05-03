"""Core JWT usage estimate (not HMAC-signed). See ``docs-sdk/MAIN-SDK-API-SPEC.md`` §2.2."""

from __future__ import annotations

from typing import TYPE_CHECKING, Optional, Union

from .validation_client import DEFAULT_CORE_PATH_PREFIX, UsageEstimateResult

if TYPE_CHECKING:
    import requests


def _billing_estimate_url(base_url: str, core_path_prefix: Optional[str]) -> str:
    base = base_url.rstrip("/")
    p = (core_path_prefix or DEFAULT_CORE_PATH_PREFIX).strip()
    if not p.startswith("/"):
        p = "/" + p
    p = p.rstrip("/")
    return f"{base}{p}/billing/usage/estimate"


def estimate_usage_with_jwt(
    base_url: str,
    bearer_token: str,
    user_id: str,
    agent_id: str,
    estimated_units: Union[int, float],
    *,
    core_path_prefix: Optional[str] = None,
    session: Optional["requests.Session"] = None,
) -> Optional[UsageEstimateResult]:
    """POST ``…/billing/usage/estimate`` with ``Authorization: Bearer`` JWT. Requires ``requests``."""
    try:
        import requests
    except ImportError:
        raise ImportError("estimate_usage_with_jwt requires 'requests'. pip install requests")
    if not bearer_token or not str(bearer_token).strip():
        return None
    if not user_id or not str(user_id).strip() or not agent_id or not str(agent_id).strip():
        return None
    if estimated_units is None or float(estimated_units) <= 0:
        return None
    url = _billing_estimate_url(base_url, core_path_prefix)
    body = {"userId": user_id, "agentId": agent_id, "estimatedUnits": estimated_units}
    sess = session or requests.Session()
    resp = sess.post(
        url,
        json=body,
        headers={
            "Authorization": f"Bearer {bearer_token.strip()}",
            "Content-Type": "application/json",
        },
        timeout=60,
    )
    code = resp.status_code
    if code not in (200, 403, 429):
        return None
    text = resp.text or ""
    if not text.strip():
        return None
    data = resp.json()
    breakdown = data.get("breakdown")
    if breakdown is not None and not isinstance(breakdown, dict):
        breakdown = None
    return UsageEstimateResult(
        sufficient_credits=bool(data.get("sufficientCredits")),
        would_exceed_cap=bool(data.get("wouldExceedCap")),
        would_allow=bool(data.get("wouldAllow")),
        estimated_cost=data.get("estimatedCost"),
        remaining_credits=data.get("remainingCredits"),
        remaining_spending_cap=data.get("remainingSpendingCap"),
        billing_model_type=data.get("billingModelType"),
        measurement_type=data.get("measurementType"),
        unit_label=data.get("unitLabel"),
        breakdown=breakdown,
        estimate_schema_version=int(data.get("estimateSchemaVersion", 0)),
        timestamp=int(data.get("timestamp", 0)),
        http_status=code,
    )
