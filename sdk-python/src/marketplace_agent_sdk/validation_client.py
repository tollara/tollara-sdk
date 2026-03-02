from dataclasses import dataclass
from typing import List, Optional

from .hmac_utils import validate_hmac_signature


@dataclass
class AgentKeyValidationResult:
    user_id: Optional[str]
    agent_id: Optional[str]
    plan: Optional[str]
    roles: List[str]
    quota_remaining: Optional[float]
    subscription_active: bool


def validate_agent_key(
    core_service_url: str,
    agent_key: str,
    agent_secret: str,
    agent_id: Optional[str] = None,
    *,
    session: Optional["requests.Session"] = None,
) -> Optional[AgentKeyValidationResult]:
    """Validate agent key via core service. Requires 'requests' (pip install requests)."""
    try:
        import requests
    except ImportError:
        raise ImportError("validate_agent_key requires 'requests'. pip install requests")
    url = core_service_url.rstrip("/") + "/agent-keys/validate"
    body = {"agentKey": agent_key, "agentId": agent_id, "agentSecret": agent_secret}
    sess = session or requests.Session()
    resp = sess.post(url, json=body)
    if not resp.ok:
        return None
    response_text = resp.text
    signature = resp.headers.get("X-Marketplace-Signature")
    timestamp = resp.headers.get("X-Marketplace-Timestamp")
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
    )
