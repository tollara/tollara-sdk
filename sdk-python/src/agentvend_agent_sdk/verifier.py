import json
from dataclasses import dataclass
from typing import Any, Dict, List, Optional

from .hmac_utils import calculate_hmac, constant_time_equals


@dataclass
class UserContext:
    user_id: Optional[str]
    plan: Optional[str]
    roles: List[str]
    quota_remaining: Optional[float]
    subscription_active: bool


def _build_user_context_string(
    user_id: Optional[str],
    plan: Optional[str],
    roles: List[str],
    quota_remaining: Optional[float],
) -> str:
    u = user_id or ""
    p = plan or ""
    r = ",".join(roles) if roles else ""
    q = str(quota_remaining) if quota_remaining is not None else ""
    return u + p + r + q


def verify_signature(
    agent_secret: str,
    signature: str,
    timestamp: str,
    payload: Any,
    user_id: Optional[str],
    plan: Optional[str],
    roles: List[str],
    quota_remaining: Optional[float],
) -> bool:
    if not signature or not timestamp or not agent_secret:
        return False
    try:
        payload_string = "" if payload is None else (payload if isinstance(payload, str) else json.dumps(payload))
        user_context_string = _build_user_context_string(user_id, plan, roles, quota_remaining)
        data_to_sign = payload_string + timestamp + user_context_string
        expected = calculate_hmac(data_to_sign, agent_secret)
        return constant_time_equals(expected, signature)
    except Exception:
        return False


def get_user_context(headers: Dict[str, Optional[str]]) -> UserContext:
    roles_str = headers.get("X-AgentVend-Roles") or headers.get("x-agentvend-roles") or ""
    roles = [s.strip() for s in roles_str.split(",") if s.strip()]
    q = headers.get("X-AgentVend-Quota-Remaining") or headers.get("x-agentvend-quota-remaining")
    quota_remaining = float(q) if q is not None and q != "" else None
    sub = headers.get("X-AgentVend-Subscription-Active") or headers.get("x-agentvend-subscription-active")
    subscription_active = sub in ("true", "1")
    uid = headers.get("X-AgentVend-User-ID") or headers.get("x-agentvend-user-id")
    plan = headers.get("X-AgentVend-Plan") or headers.get("x-agentvend-plan")
    return UserContext(
        user_id=uid,
        plan=plan,
        roles=roles,
        quota_remaining=quota_remaining,
        subscription_active=subscription_active,
    )
