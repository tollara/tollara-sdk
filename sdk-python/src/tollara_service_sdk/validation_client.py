from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Union
from uuid import UUID

from .tollara_headers import TollaraHeaders
from .hmac_utils import validate_hmac_signature
from .usage_breakdown import UsageBreakdown, parse_usage_breakdown
from .verifier import grant_access

DEFAULT_CORE_PATH_PREFIX = "/api/v1"


def _core_service_keys_url(base_url: str, core_path_prefix: Optional[str], suffix: str) -> str:
    base = base_url.rstrip("/")
    p = (core_path_prefix or DEFAULT_CORE_PATH_PREFIX).strip()
    if not p.startswith("/"):
        p = "/" + p
    p = p.rstrip("/")
    return f"{base}{p}/service-keys/{suffix}"


def _validate_url(base_url: str, core_path_prefix: Optional[str]) -> str:
    return _core_service_keys_url(base_url, core_path_prefix, "validate")


def _optional_uuid(value: Any) -> Optional[UUID]:
    if value is None:
        return None
    if isinstance(value, UUID):
        return value
    s = str(value).strip()
    if not s:
        return None
    try:
        return UUID(s)
    except ValueError:
        return None


@dataclass
class ServiceKeyValidationResult:
    user_id: Optional[str]
    service_id: Optional[str]
    service_key_id: Optional[UUID]
    service_product_id: Optional[str]
    roles: List[str]
    subscription_status: Optional[str]
    validation_schema_version: int
    billing_model_type: Optional[str]
    measurement_type: Optional[str]
    unit_label: Optional[str]

    def grant_access(self) -> bool:
        return grant_access(self.subscription_status)

    @staticmethod
    def grant_access_for_status(subscription_status: Optional[str]) -> bool:
        return grant_access(subscription_status)


@dataclass
class UsageEstimateResult:
    sufficient_credits: bool
    would_exceed_cap: bool
    would_allow: bool
    estimated_cost: Optional[float]
    billing_model_type: Optional[str]
    measurement_type: Optional[str]
    unit_label: Optional[str]
    breakdown: Optional[UsageBreakdown]
    estimate_schema_version: int
    timestamp: int
    http_status: int


def validate_service_key(
    base_url: str,
    service_key: str,
    service_secret: str,
    service_id: Optional[str] = None,
    *,
    core_path_prefix: Optional[str] = None,
    session: Optional["requests.Session"] = None,
) -> Optional[ServiceKeyValidationResult]:
    """Validate service key via Core service. Requires 'requests' (pip install requests)."""
    try:
        import requests
    except ImportError:
        raise ImportError("validate_service_key requires 'requests'. pip install requests")
    url = _validate_url(base_url, core_path_prefix)
    body = {"serviceKey": service_key, "serviceId": service_id, "serviceSecret": service_secret}
    sess = session or requests.Session()
    resp = sess.post(url, json=body)
    if not resp.ok:
        return None
    response_text = resp.text
    signature = resp.headers.get(TollaraHeaders.SIGNATURE)
    timestamp = resp.headers.get(TollaraHeaders.TIMESTAMP)
    if not signature or not timestamp:
        return None
    if not validate_hmac_signature(signature, response_text + timestamp, service_secret):
        return None
    data = resp.json()
    if not data.get("valid"):
        return None
    roles = data.get("roles") or []
    return ServiceKeyValidationResult(
        user_id=data.get("userId"),
        service_id=data.get("serviceId") or service_id,
        service_key_id=_optional_uuid(data.get("serviceKeyId")),
        service_product_id=data.get("serviceProductId"),
        roles=roles if isinstance(roles, list) else [],
        subscription_status=data.get("subscriptionStatus"),
        validation_schema_version=int(data.get("validationSchemaVersion", 0)),
        billing_model_type=data.get("billingModelType"),
        measurement_type=data.get("measurementType"),
        unit_label=data.get("unitLabel"),
    )


def estimate_usage(
    base_url: str,
    service_key: str,
    service_secret: str,
    estimated_units: Union[int, float],
    service_id: Optional[str] = None,
    *,
    core_path_prefix: Optional[str] = None,
    session: Optional["requests.Session"] = None,
) -> Optional[UsageEstimateResult]:
    """Usage pre-flight via Core (same trust model as :func:`validate_service_key`). Requires ``requests``."""
    try:
        import requests
    except ImportError:
        raise ImportError("estimate_usage requires 'requests'. pip install requests")
    if estimated_units is None or float(estimated_units) <= 0:
        return None
    if not service_key or not str(service_key).strip():
        return None
    url = _core_service_keys_url(base_url, core_path_prefix, "estimate-usage")
    body = {
        "serviceKey": service_key,
        "serviceId": service_id,
        "serviceSecret": service_secret,
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
    signature = resp.headers.get(TollaraHeaders.SIGNATURE)
    timestamp = resp.headers.get(TollaraHeaders.TIMESTAMP)
    if not signature or not timestamp:
        return None
    if not validate_hmac_signature(signature, response_text + timestamp, service_secret):
        return None
    data = resp.json()
    breakdown_raw = data.get("breakdown")
    breakdown = parse_usage_breakdown(breakdown_raw) if isinstance(breakdown_raw, dict) else None
    return UsageEstimateResult(
        sufficient_credits=bool(data.get("sufficientCredits")),
        would_exceed_cap=bool(data.get("wouldExceedCap")),
        would_allow=bool(data.get("wouldAllow")),
        estimated_cost=data.get("estimatedCost"),
        billing_model_type=data.get("billingModelType"),
        measurement_type=data.get("measurementType"),
        unit_label=data.get("unitLabel"),
        breakdown=breakdown,
        estimate_schema_version=int(data.get("estimateSchemaVersion", 0)),
        timestamp=int(data.get("timestamp", 0)),
        http_status=code,
    )
