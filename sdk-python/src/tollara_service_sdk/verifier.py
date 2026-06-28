import json
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Union

from .tollara_headers import TollaraHeaders
from .hmac_utils import calculate_hmac, constant_time_equals

SIGNING_VERSION_V3 = "3"
_INVOKE_ELIGIBLE_STATUSES = frozenset({"ACTIVE", "TRIAL", "CANCELLING", "CANCELLING_PENDING"})


def grants_access(subscription_status: Optional[str]) -> bool:
    """True when subscription_status is invoke-eligible (ACTIVE, TRIAL, CANCELLING, CANCELLING_PENDING)."""
    if subscription_status is None or not str(subscription_status).strip():
        return False
    return str(subscription_status).strip().upper() in _INVOKE_ELIGIBLE_STATUSES


@dataclass
class UserContext:
    user_id: Optional[str]
    service_product_id: Optional[str]
    roles: List[str]
    subscription_status: Optional[str]
    billing_model_type: Optional[str]
    measurement_type: Optional[str]
    unit_label: Optional[str]
    # v1/v2 only
    plan: Optional[str] = None
    quota_remaining: Optional[Union[int, float]] = None
    subscription_active: bool = False


@dataclass
class SignedUserContext:
    """Fields that participate in inbound HMAC userContextString (docs/hmac-spec.md)."""

    user_id: Optional[str] = None
    service_product_id: Optional[str] = None
    roles: List[str] = field(default_factory=list)
    subscription_status: Optional[str] = None
    billing_model_type: Optional[str] = None
    measurement_type: Optional[str] = None
    unit_label: Optional[str] = None
    # v1/v2 only
    plan: Optional[str] = None
    quota_remaining: Optional[Union[int, float]] = None
    subscription_active: bool = False


@dataclass
class InboundHmacRequest:
    signature: str
    timestamp: str
    payload: Any
    signed_user_context: SignedUserContext
    signing_version: Optional[str] = None


def _header_get_ci(headers: Dict[str, Optional[str]], canonical_name: str) -> Optional[str]:
    target = canonical_name.lower()
    for k, v in headers.items():
        if k is not None and k.lower() == target:
            return v
    return None


def _empty_to_none(value: Optional[str]) -> Optional[str]:
    if value is None or value == "":
        return None
    return value


def _format_quota(quota_remaining: Optional[Union[int, float, str]]) -> str:
    if quota_remaining is None:
        return ""
    if isinstance(quota_remaining, str):
        return quota_remaining
    if isinstance(quota_remaining, int):
        return str(quota_remaining)
    if quota_remaining == int(quota_remaining):
        return str(int(quota_remaining))
    return str(quota_remaining)


def _parse_quota_raw(raw: Optional[str]) -> Optional[Union[int, float]]:
    if raw in (None, ""):
        return None
    s = raw.strip()
    if "." in s:
        try:
            return float(s)
        except ValueError:
            return None
    try:
        return int(s)
    except ValueError:
        try:
            return float(s)
        except ValueError:
            return None


def _parse_subscription_active(raw: Optional[str]) -> bool:
    if raw is None or raw == "":
        return False
    t = raw.strip()
    return t.lower() == "true" or t == "1"


def _parse_roles_list(roles_header: Optional[str]) -> List[str]:
    if roles_header is None or roles_header == "":
        return []
    return [s.strip() for s in roles_header.split(",") if s.strip()]


def build_gateway_user_context_string(
    user_id: Optional[str],
    plan: Optional[str],
    roles: List[str],
    quota_remaining: Optional[Union[int, float, str]],
    subscription_active: bool,
    billing_model_type: Optional[str],
    measurement_type: Optional[str],
    unit_label: Optional[str],
) -> str:
    u = user_id or ""
    p = plan or ""
    r = ",".join(roles) if roles else ""
    q = _format_quota(quota_remaining)
    sub = "true" if subscription_active else "false"
    b = billing_model_type or ""
    m = measurement_type or ""
    ul = unit_label or ""
    return u + p + r + q + sub + b + m + ul


def build_gateway_user_context_string_v2(
    user_id: Optional[str],
    plan: Optional[str],
    roles: List[str],
    subscription_active: bool,
    billing_model_type: Optional[str],
    measurement_type: Optional[str],
    unit_label: Optional[str],
) -> str:
    """Gateway HMAC user-context v2: leading ``2``, no quota segment."""
    u = user_id or ""
    p = plan or ""
    r = ",".join(roles) if roles else ""
    sub = "true" if subscription_active else "false"
    b = billing_model_type or ""
    m = measurement_type or ""
    ul = unit_label or ""
    return "2" + u + p + r + sub + b + m + ul


def build_gateway_user_context_string_v3(
    user_id: Optional[str],
    service_product_id: Optional[str],
    roles: List[str],
    subscription_status: Optional[str],
    billing_model_type: Optional[str],
    measurement_type: Optional[str],
    unit_label: Optional[str],
) -> str:
    """Gateway HMAC user-context v3: leading ``3``, serviceProductId and subscriptionStatus."""
    u = user_id or ""
    sp = service_product_id or ""
    r = ",".join(roles) if roles else ""
    ss = subscription_status or ""
    b = billing_model_type or ""
    m = measurement_type or ""
    ul = unit_label or ""
    return "3" + u + sp + r + ss + b + m + ul


def _parse_signed_user_context_from_headers(
    headers: Dict[str, Optional[str]],
) -> SignedUserContext:
    roles_str = _header_get_ci(headers, TollaraHeaders.ROLES) or ""
    roles = _parse_roles_list(roles_str)
    q_raw = _header_get_ci(headers, TollaraHeaders.QUOTA_REMAINING)
    quota_remaining = _parse_quota_raw(q_raw)
    sub_raw = _header_get_ci(headers, TollaraHeaders.SUBSCRIPTION_ACTIVE)
    bm = _header_get_ci(headers, TollaraHeaders.BILLING_MODEL)
    mt = _header_get_ci(headers, TollaraHeaders.MEASUREMENT_TYPE)
    ul = _header_get_ci(headers, TollaraHeaders.UNIT_LABEL)
    return SignedUserContext(
        user_id=_header_get_ci(headers, TollaraHeaders.USER_ID),
        service_product_id=_empty_to_none(_header_get_ci(headers, TollaraHeaders.SERVICE_PRODUCT_ID)),
        roles=roles,
        subscription_status=_empty_to_none(_header_get_ci(headers, TollaraHeaders.SUBSCRIPTION_STATUS)),
        billing_model_type=_empty_to_none(bm),
        measurement_type=_empty_to_none(mt),
        unit_label=_empty_to_none(ul),
        plan=_header_get_ci(headers, TollaraHeaders.PLAN),
        quota_remaining=quota_remaining,
        subscription_active=_parse_subscription_active(sub_raw),
    )


def verify_inbound_hmac(service_secret: str, request: InboundHmacRequest) -> bool:
    s = request.signed_user_context
    if request.signing_version == SIGNING_VERSION_V3:
        return verify_signature_v3(
            service_secret,
            request.signature,
            request.timestamp,
            request.payload,
            s.user_id,
            s.service_product_id,
            s.roles,
            s.subscription_status,
            s.billing_model_type,
            s.measurement_type,
            s.unit_label,
        )
    return verify_signature(
        service_secret,
        request.signature,
        request.timestamp,
        request.payload,
        s.user_id,
        s.plan,
        s.roles,
        s.quota_remaining,
        s.subscription_active,
        s.billing_model_type,
        s.measurement_type,
        s.unit_label,
        signing_version=request.signing_version,
    )


def verify_signature_from_headers(
    service_secret: str,
    headers: Dict[str, Optional[str]],
    payload: Any,
) -> bool:
    signature = _header_get_ci(headers, TollaraHeaders.SIGNATURE) or ""
    timestamp = _header_get_ci(headers, TollaraHeaders.TIMESTAMP) or ""
    if not signature or not timestamp:
        return False
    signed = _parse_signed_user_context_from_headers(headers)
    sv = _header_get_ci(headers, TollaraHeaders.SIGNING_VERSION)
    return verify_inbound_hmac(
        service_secret,
        InboundHmacRequest(
            signature=signature,
            timestamp=timestamp,
            payload=payload,
            signed_user_context=signed,
            signing_version=sv if sv else None,
        ),
    )


def verify_signature(
    service_secret: str,
    signature: str,
    timestamp: str,
    payload: Any,
    user_id: Optional[str],
    plan: Optional[str],
    roles: List[str],
    quota_remaining: Optional[Union[int, float, str]],
    subscription_active: bool,
    billing_model_type: Optional[str] = None,
    measurement_type: Optional[str] = None,
    unit_label: Optional[str] = None,
    signing_version: Optional[str] = None,
) -> bool:
    if signing_version == SIGNING_VERSION_V3:
        return False
    if not signature or not timestamp or not service_secret:
        return False
    try:
        payload_string = "" if payload is None else (payload if isinstance(payload, str) else json.dumps(payload))
        if signing_version == "2":
            user_context_string = build_gateway_user_context_string_v2(
                user_id,
                plan,
                roles,
                subscription_active,
                billing_model_type,
                measurement_type,
                unit_label,
            )
        else:
            user_context_string = build_gateway_user_context_string(
                user_id,
                plan,
                roles,
                quota_remaining,
                subscription_active,
                billing_model_type,
                measurement_type,
                unit_label,
            )
        data_to_sign = payload_string + timestamp + user_context_string
        expected = calculate_hmac(data_to_sign, service_secret)
        return constant_time_equals(expected, signature)
    except Exception:
        return False


def verify_signature_v3(
    service_secret: str,
    signature: str,
    timestamp: str,
    payload: Any,
    user_id: Optional[str],
    service_product_id: Optional[str],
    roles: List[str],
    subscription_status: Optional[str],
    billing_model_type: Optional[str] = None,
    measurement_type: Optional[str] = None,
    unit_label: Optional[str] = None,
) -> bool:
    if not signature or not timestamp or not service_secret:
        return False
    try:
        payload_string = "" if payload is None else (payload if isinstance(payload, str) else json.dumps(payload))
        user_context_string = build_gateway_user_context_string_v3(
            user_id,
            service_product_id,
            roles,
            subscription_status,
            billing_model_type,
            measurement_type,
            unit_label,
        )
        data_to_sign = payload_string + timestamp + user_context_string
        expected = calculate_hmac(data_to_sign, service_secret)
        return constant_time_equals(expected, signature)
    except Exception:
        return False


def verify_inbound_context(
    service_secret: str,
    headers: Dict[str, Optional[str]],
    payload: Any,
) -> Optional[UserContext]:
    """Verify inbound HMAC; if valid return :class:`UserContext`, else ``None`` (do not trust headers)."""
    if not verify_signature_from_headers(service_secret, headers, payload):
        return None
    return get_user_context(headers)


# Backward compatibility — prefer :func:`verify_inbound_context`
verify_signature_from_headers_and_get_user_context = verify_inbound_context


def get_user_context(headers: Dict[str, Optional[str]]) -> UserContext:
    roles_str = _header_get_ci(headers, TollaraHeaders.ROLES) or ""
    roles = _parse_roles_list(roles_str)
    q = _header_get_ci(headers, TollaraHeaders.QUOTA_REMAINING)
    quota_remaining = _parse_quota_raw(q)
    sub = _header_get_ci(headers, TollaraHeaders.SUBSCRIPTION_ACTIVE)
    subscription_active = _parse_subscription_active(sub)
    uid = _header_get_ci(headers, TollaraHeaders.USER_ID)
    plan = _header_get_ci(headers, TollaraHeaders.PLAN)
    bm = _header_get_ci(headers, TollaraHeaders.BILLING_MODEL)
    mt = _header_get_ci(headers, TollaraHeaders.MEASUREMENT_TYPE)
    ul = _header_get_ci(headers, TollaraHeaders.UNIT_LABEL)
    return UserContext(
        user_id=uid,
        service_product_id=_empty_to_none(_header_get_ci(headers, TollaraHeaders.SERVICE_PRODUCT_ID)),
        roles=roles,
        subscription_status=_empty_to_none(_header_get_ci(headers, TollaraHeaders.SUBSCRIPTION_STATUS)),
        billing_model_type=_empty_to_none(bm),
        measurement_type=_empty_to_none(mt),
        unit_label=_empty_to_none(ul),
        plan=plan,
        quota_remaining=quota_remaining,
        subscription_active=subscription_active,
    )
