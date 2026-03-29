from dataclasses import dataclass
from typing import List, Optional

from .agentvend_headers import AgentVendHeaders
from .hmac_utils import validate_hmac_signature

DEFAULT_CORE_PATH_PREFIX = "/core/api/v1"


def _validate_url(base_url: str, core_path_prefix: Optional[str]) -> str:
    base = base_url.rstrip("/")
    p = (core_path_prefix or DEFAULT_CORE_PATH_PREFIX).strip()
    if not p.startswith("/"):
        p = "/" + p
    p = p.rstrip("/")
    return f"{base}{p}/agent-keys/validate"


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
