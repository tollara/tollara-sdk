from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Union

from .agentvend_headers import AgentVendHeaders
from .hmac_utils import validate_hmac_signature

DEFAULT_CORE_PATH_PREFIX = "/core/api/v1"


def _core_agent_keys_url(base_url: str, core_path_prefix: Optional[str], suffix: str) -> str:
    base = base_url.rstrip("/")
    p = (core_path_prefix or DEFAULT_CORE_PATH_PREFIX).strip()
    if not p.startswith("/"):
        p = "/" + p
    p = p.rstrip("/")
    return f"{base}{p}/agent-keys/{suffix}"


def _validate_url(base_url: str, core_path_prefix: Optional[str]) -> str:
    return _core_agent_keys_url(base_url, core_path_prefix, "validate")


@dataclass
class AgentKeyValidationResult:
    user_id: Optional[str]
    agent_id: Optional[str]
    plan: Optional[str]
    roles: List[str]
    quota_remaining: Optional[float]
    subscription_active: bool
    billing_model_type: Optional[str]
    measurement_type: Optional[str]
    unit_label: Optional[str]


@dataclass
class UsageEstimateResult:
    sufficient_credits: bool
    would_exceed_cap: bool
    would_allow: bool
    estimated_cost: Optional[float]
    remaining_credits: Optional[float]
    remaining_spending_cap: Optional[float]
    billing_model_type: Optional[str]
    measurement_type: Optional[str]
    unit_label: Optional[str]
    breakdown: Optional[Dict[str, Any]]
    estimate_schema_version: int
    timestamp: int
    http_status: int


def validate_agent_key(
    base_url: str,
    agent_key: str,
    agent_secret: str,
    agent_id: Optional[str] = None,
    *,
    core_path_prefix: Optional[str] = None,
    session: Optional["requests.Session"] = None,
) -> Optional[AgentKeyValidationResult]:
    """Validate agent key via Core service. Requires 'requests' (pip install requests)."""
    try:
        import requests
    except ImportError:
        raise ImportError("validate_agent_key requires 'requests'. pip install requests")
    url = _validate_url(base_url, core_path_prefix)
    body = {"agentKey": agent_key, "agentId": agent_id, "agentSecret": agent_secret}
    sess = session or requests.Session()
    resp = sess.post(url, json=body)
    if not resp.ok:
        return None
    response_text = resp.text
    signature = resp.headers.get(AgentVendHeaders.SIGNATURE)
    timestamp = resp.headers.get(AgentVendHeaders.TIMESTAMP)
    if not signature or not timestamp:
        return None
    if not validate_hmac_signature(signature, response_text + timestamp, agent_secret):
        return None
    data = resp.json()
    if not data.get("valid"):
        return None
    roles = data.get("roles") or []
    return AgentKeyValidationResult(
        user_id=data.get("userId"),
        agent_id=data.get("agentId") or agent_id,
        plan=data.get("plan"),
        roles=roles if isinstance(roles, list) else [],
        quota_remaining=data.get("quotaRemaining"),
        subscription_active=bool(data.get("subscriptionActive")),
        billing_model_type=data.get("billingModelType"),
        measurement_type=data.get("measurementType"),
        unit_label=data.get("unitLabel"),
    )


def estimate_usage(
    base_url: str,
    agent_key: str,
    agent_secret: str,
    estimated_units: Union[int, float],
    agent_id: Optional[str] = None,
    *,
    core_path_prefix: Optional[str] = None,
    session: Optional["requests.Session"] = None,
) -> Optional[UsageEstimateResult]:
    """Usage pre-flight via Core (same trust model as :func:`validate_agent_key`). Requires ``requests``."""
    try:
        import requests
    except ImportError:
        raise ImportError("estimate_usage requires 'requests'. pip install requests")
    if estimated_units is None or float(estimated_units) <= 0:
        return None
    if not agent_key or not str(agent_key).strip():
        return None
    url = _core_agent_keys_url(base_url, core_path_prefix, "estimate-usage")
    body = {
        "agentKey": agent_key,
        "agentId": agent_id,
        "agentSecret": agent_secret,
        "estimatedUnits": estimated_units,
    }
    sess = session or requests.Session()
    resp = sess.post(url, json=body)
    code = resp.status_code
    if code not in (200, 403, 429):
        return None
    response_text = resp.text
    if not response_text.strip():
        return None
    signature = resp.headers.get(AgentVendHeaders.SIGNATURE)
    timestamp = resp.headers.get(AgentVendHeaders.TIMESTAMP)
    if not signature or not timestamp:
        return None
    if not validate_hmac_signature(signature, response_text + timestamp, agent_secret):
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
