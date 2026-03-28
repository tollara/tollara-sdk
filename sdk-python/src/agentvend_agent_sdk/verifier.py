import json
from dataclasses import dataclass
from typing import Any, Dict, List, Optional

from .agentvend_headers import AgentVendHeaders
from .hmac_utils import calculate_hmac, constant_time_equals


@dataclass
class UserContext:
    user_id: Optional[str]
    plan: Optional[str]
    roles: List[str]
    quota_remaining: Optional[float]
    subscription_active: bool


@dataclass
class SignedUserContext:
    """Fields that participate in inbound HMAC userContextString (docs/hmac-spec.md)."""

    user_id: Optional[str]
    plan: Optional[str]
    roles: List[str]
    quota_remaining: Optional[float]


@dataclass
class InboundHmacRequest:
    signature: str
    timestamp: str
    payload: Any
    signed_user_context: SignedUserContext


def _header_get_ci(headers: Dict[str, Optional[str]], canonical_name: str) -> Optional[str]:
    target = canonical_name.lower()
    for k, v in headers.items():
        if k is not None and k.lower() == target:
            return v
    return None


def _format_quota(quota_remaining: Optional[float]) -> str:
    if quota_remaining is None:
        return ""
    # Match Java BigDecimal-style string for whole numbers (e.g. 10 not 10.0)
    if quota_remaining == int(quota_remaining):
        return str(int(quota_remaining))
    return str(quota_remaining)


def _build_user_context_string(
    user_id: Optional[str],
    plan: Optional[str],
    roles: List[str],
    quota_remaining: Optional[float],
) -> str:
    u = user_id or ""
    p = plan or ""
    r = ",".join(roles) if roles else ""
    q = _format_quota(quota_remaining)
    return u + p + r + q


def verify_inbound_hmac(agent_secret: str, request: InboundHmacRequest) -> bool:
    s = request.signed_user_context
    return verify_signature(
        agent_secret,
        request.signature,
        request.timestamp,
        request.payload,
        s.user_id,
        s.plan,
        s.roles,
        s.quota_remaining,
    )


def verify_signature_from_headers(
    agent_secret: str,
    headers: Dict[str, Optional[str]],
    payload: Any,
) -> bool:
    signature = _header_get_ci(headers, AgentVendHeaders.SIGNATURE) or ""
    timestamp = _header_get_ci(headers, AgentVendHeaders.TIMESTAMP) or ""
    if not signature or not timestamp:
        return False
    roles_str = _header_get_ci(headers, AgentVendHeaders.ROLES) or ""
    roles = [s.strip() for s in roles_str.split(",") if s.strip()]
    q_raw = _header_get_ci(headers, AgentVendHeaders.QUOTA_REMAINING)
    quota_remaining = float(q_raw) if q_raw not in (None, "") else None
    signed = SignedUserContext(
        user_id=_header_get_ci(headers, AgentVendHeaders.USER_ID),
        plan=_header_get_ci(headers, AgentVendHeaders.PLAN),
        roles=roles,
        quota_remaining=quota_remaining,
    )
    return verify_inbound_hmac(
        agent_secret,
        InboundHmacRequest(
            signature=signature,
            timestamp=timestamp,
            payload=payload,
            signed_user_context=signed,
        ),
    )


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
    roles_str = _header_get_ci(headers, AgentVendHeaders.ROLES) or ""
    roles = [s.strip() for s in roles_str.split(",") if s.strip()]
    q = _header_get_ci(headers, AgentVendHeaders.QUOTA_REMAINING)
    quota_remaining = float(q) if q not in (None, "") else None
    sub = _header_get_ci(headers, AgentVendHeaders.SUBSCRIPTION_ACTIVE)
    subscription_active = sub in ("true", "1")
    uid = _header_get_ci(headers, AgentVendHeaders.USER_ID)
    plan = _header_get_ci(headers, AgentVendHeaders.PLAN)
    return UserContext(
        user_id=uid,
        plan=plan,
        roles=roles,
        quota_remaining=quota_remaining,
        subscription_active=subscription_active,
    )
