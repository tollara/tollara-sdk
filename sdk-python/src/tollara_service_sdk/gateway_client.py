"""Caller-side gateway polling (docs/sdk-api-spec.md §1.3–1.4)."""

from __future__ import annotations

from dataclasses import dataclass
from typing import TYPE_CHECKING, Optional

if TYPE_CHECKING:
    import requests

from .path_prefixes import resolve_gateway_path_prefix

DEFAULT_GATEWAY_PATH_PREFIX = "/api"


def _normalize_base(url: str) -> str:
    return url.rstrip("/")


def _normalize_prefix(prefix: str) -> str:
    if not prefix:
        return ""
    p = prefix if prefix.startswith("/") else f"/{prefix}"
    return p.rstrip("/")


def _build_url(gateway_base_url: str, gateway_path_prefix: str, suffix: str) -> str:
    return f"{_normalize_base(gateway_base_url)}{_normalize_prefix(gateway_path_prefix)}{suffix}"


@dataclass
class GatewayPollResult:
    ok: bool
    status_code: int
    body: str


def get_request_status(
    base_url: str,
    request_id: str,
    service_key: str,
    *,
    gateway_path_prefix: Optional[str] = None,
    session: Optional["requests.Session"] = None,
) -> GatewayPollResult:
    """GET .../requests/{request_id}/status with Bearer service key."""
    try:
        import requests
    except ImportError as e:
        raise ImportError("get_request_status requires 'requests'. pip install requests") from e
    prefix = resolve_gateway_path_prefix(base_url, gateway_path_prefix)
    url = _build_url(base_url, prefix, f"/requests/{request_id}/status")
    sess = session or requests.Session()
    resp = sess.get(url, headers={"Authorization": f"Bearer {service_key}"}, timeout=60)
    return GatewayPollResult(ok=resp.ok, status_code=resp.status_code, body=resp.text or "")


def get_request_result(
    base_url: str,
    request_id: str,
    service_key: str,
    *,
    gateway_path_prefix: Optional[str] = None,
    session: Optional["requests.Session"] = None,
) -> GatewayPollResult:
    """GET .../requests/{request_id}/result with Bearer service key."""
    try:
        import requests
    except ImportError as e:
        raise ImportError("get_request_result requires 'requests'. pip install requests") from e
    prefix = resolve_gateway_path_prefix(base_url, gateway_path_prefix)
    url = _build_url(base_url, prefix, f"/requests/{request_id}/result")
    sess = session or requests.Session()
    resp = sess.get(url, headers={"Authorization": f"Bearer {service_key}"}, timeout=60)
    return GatewayPollResult(ok=resp.ok, status_code=resp.status_code, body=resp.text or "")
